package com.texora.secops.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the IAM client library.
 *
 * <p>Bind with prefix {@code texora.iam} in each service's {@code application.yml}:
 * <pre>
 * texora:
 *   iam:
 *     jwks-uri: https://sso.texora.internal/oauth2/jwks
 *     issuer-uri: https://sso.texora.internal
 *     audience: texora-secops
 *     mtls:
 *       enabled: true
 *       ...
 * </pre>
 */
@ConfigurationProperties(prefix = "texora.iam")
public class IamProperties {

    /** JWKS endpoint of the SSO server. Used to fetch signing keys for token validation. */
    private String jwksUri;

    /** Expected issuer claim in all tokens. */
    private String issuerUri;

    /** Expected audience claim in all tokens. */
    private String audience = "texora-secops";

    public String getJwksUri() { return jwksUri; }
    public void setJwksUri(String jwksUri) { this.jwksUri = jwksUri; }

    public String getIssuerUri() { return issuerUri; }
    public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }

    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
}
