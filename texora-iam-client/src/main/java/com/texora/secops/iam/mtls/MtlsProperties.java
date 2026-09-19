package com.texora.secops.iam.mtls;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for mTLS client certificates.
 *
 * <p>Bind with prefix {@code texora.iam.mtls} in {@code application.yml}:
 * <pre>
 * texora:
 *   iam:
 *     mtls:
 *       enabled: true
 *       key-store-path: /etc/tls/client.p12     # populated by init container from vault
 *       key-store-password: ${MTLS_KS_PASSWORD}  # from Kubernetes secret
 *       key-store-type: PKCS12
 *       trust-store-path: /etc/tls/truststore.p12
 *       trust-store-password: ${MTLS_TS_PASSWORD}
 *       trust-store-type: PKCS12
 * </pre>
 *
 * <p>Key material is NEVER stored in the application config or the database.
 * It is fetched from the vault via the service's Kubernetes service account
 * identity and written to the paths above by an init container (standard B.6 §6).
 */
@ConfigurationProperties(prefix = "texora.iam.mtls")
public class MtlsProperties {

    private boolean enabled = false;
    private String keyStorePath = "/etc/tls/client.p12";
    private String keyStorePassword = "";
    private String keyStoreType = "PKCS12";
    private String trustStorePath = "/etc/tls/truststore.p12";
    private String trustStorePassword = "";
    private String trustStoreType = "PKCS12";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getKeyStorePath() { return keyStorePath; }
    public void setKeyStorePath(String keyStorePath) { this.keyStorePath = keyStorePath; }

    public String getKeyStorePassword() { return keyStorePassword; }
    public void setKeyStorePassword(String keyStorePassword) { this.keyStorePassword = keyStorePassword; }

    public String getKeyStoreType() { return keyStoreType; }
    public void setKeyStoreType(String keyStoreType) { this.keyStoreType = keyStoreType; }

    public String getTrustStorePath() { return trustStorePath; }
    public void setTrustStorePath(String trustStorePath) { this.trustStorePath = trustStorePath; }

    public String getTrustStorePassword() { return trustStorePassword; }
    public void setTrustStorePassword(String trustStorePassword) { this.trustStorePassword = trustStorePassword; }

    public String getTrustStoreType() { return trustStoreType; }
    public void setTrustStoreType(String trustStoreType) { this.trustStoreType = trustStoreType; }
}
