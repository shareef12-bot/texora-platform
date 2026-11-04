package com.texora.secops.vpn.config;

import org.springframework.boot.ssl.SslBundles;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Builds the HttpClient used for service-to-service calls (SEC, DC). With an SSL bundle configured the client
 * presents the service's own certificate (mTLS, B.6.1). Private key material is read by Spring's SSL-bundle
 * machinery from mounted files; this service never reads, stores or logs it.
 */
@Component
public class MtlsHttpClientFactory {

    private final SslBundles sslBundles;

    public MtlsHttpClientFactory(SslBundles sslBundles) {
        this.sslBundles = sslBundles;
    }

    public HttpClient create(String sslBundleName, int connectTimeoutMs) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER);
        if (sslBundleName != null && !sslBundleName.isBlank()) {
            SSLContext context = sslBundles.getBundle(sslBundleName).createSslContext();
            builder.sslContext(context);
        }
        return builder.build();
    }
}
