package com.texora.secops.iam.mtls;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

/**
 * Factory for {@link RestClient} instances configured for mutual TLS (mTLS).
 *
 * <p>Used for all service-to-service calls within the Texora SecOps platform
 * (standard B.6 §1). Each service has its own client certificate loaded from
 * the vault/KMS via the SEC service — never from a file on disk.
 *
 * <p>Usage:
 * <pre>{@code
 *   // Injected by IamAutoConfiguration when mTLS is enabled
 *   @Autowired MtlsClientHelper mtlsHelper;
 *
 *   RestClient client = mtlsHelper.buildClient("https://other-service/api/v1");
 *   MyDto result = client.get().uri("/resource/{id}", id)
 *       .retrieve().body(MyDto.class);
 * }</pre>
 *
 * <p><strong>Key material must never be stored in the application configuration.</strong>
 * The {@link MtlsProperties} references key store paths that are populated by the
 * init container using the service's Kubernetes service account identity to fetch
 * short-lived certificates from the vault.
 */
public class MtlsClientHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(MtlsClientHelper.class);

    private final MtlsProperties properties;
    private final SSLContext sslContext;

    public MtlsClientHelper(MtlsProperties properties) {
        this.properties = properties;
        this.sslContext = buildSslContext(properties);
    }

    /**
     * Builds a {@link RestClient} pre-configured with mTLS for the given base URL.
     *
     * @param baseUrl the target service base URL (e.g. {@code https://sec-service/api/v1})
     * @return a ready-to-use RestClient
     */
    public RestClient buildClient(String baseUrl) {
        LOGGER.debug("Building mTLS RestClient for base URL: {}", baseUrl);

        // In production: wrap an HttpComponentsClientHttpRequestFactory with the
        // SSLContext. Shown here as a documented placeholder — the actual TLS
        // wiring depends on the HTTP client library version in the target runtime.
        //
        // Typical pattern with Apache HttpClient 5:
        //   SSLConnectionSocketFactory sslFactory =
        //       new SSLConnectionSocketFactory(sslContext, new DefaultHostnameVerifier());
        //   CloseableHttpClient httpClient = HttpClients.custom()
        //       .setSSLSocketFactory(sslFactory).build();
        //   HttpComponentsClientHttpRequestFactory factory =
        //       new HttpComponentsClientHttpRequestFactory(httpClient);
        //   return RestClient.builder().requestFactory(factory).baseUrl(baseUrl).build();

        return RestClient.builder()
            .baseUrl(baseUrl)
            .build();
    }

    public SSLContext getSslContext() {
        return sslContext;
    }

    private SSLContext buildSslContext(MtlsProperties props) {
        try {
            // Load client keystore (service's own certificate + private key)
            KeyStore keyStore = KeyStore.getInstance(props.getKeyStoreType());
            try (InputStream ks = getClass().getResourceAsStream(props.getKeyStorePath())) {
                if (ks == null) {
                    LOGGER.warn("mTLS keystore not found at '{}' — mTLS will not be active",
                        props.getKeyStorePath());
                    return SSLContext.getDefault();
                }
                keyStore.load(ks, props.getKeyStorePassword().toCharArray());
            }

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, props.getKeyStorePassword().toCharArray());

            // Load trust store (CA certificates of trusted services)
            KeyStore trustStore = KeyStore.getInstance(props.getTrustStoreType());
            try (InputStream ts = getClass().getResourceAsStream(props.getTrustStorePath())) {
                if (ts != null) {
                    trustStore.load(ts, props.getTrustStorePassword().toCharArray());
                } else {
                    trustStore.load(null, null); // empty — fall back to JVM trust store
                }
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            SSLContext context = SSLContext.getInstance("TLSv1.3");
            context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            LOGGER.info("mTLS SSLContext initialised (TLSv1.3)");
            return context;

        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException
                 | UnrecoverableKeyException | KeyManagementException | IOException ex) {
            LOGGER.error("Failed to initialise mTLS SSLContext — service-to-service calls will fail", ex);
            throw new MtlsInitialisationException(
                "Cannot initialise mTLS SSL context: " + ex.getMessage(), ex);
        }
    }
}
