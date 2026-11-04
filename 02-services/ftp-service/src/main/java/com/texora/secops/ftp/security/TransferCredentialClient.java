package com.texora.secops.ftp.security;

import com.texora.secops.ftp.exception.UpstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * Retrieves transfer credential material from the Security Server using THIS service's own identity (mTLS).
 * No secret is ever stored in the ftp schema or in configuration - endpoints hold only an opaque reference.
 * Secret values are never logged.
 */
@Component
public class TransferCredentialClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransferCredentialClient.class);

    private final RestClient secClient;

    public TransferCredentialClient(@Qualifier("secRestClient") RestClient secClient) {
        this.secClient = secClient;
    }

    public TransferCredential fetch(UUID tenantId, String credentialRef) {
        try {
            Map<?, ?> m = secClient.get()
                    .uri("/api/v1/transfer-credentials/{ref}?tenantId={tenantId}", credentialRef, tenantId.toString())
                    .retrieve()
                    .body(Map.class);
            if (m == null || m.get("username") == null) {
                throw new UpstreamUnavailableException("CREDENTIAL_UNAVAILABLE", "SEC returned no usable credential");
            }
            String password = m.get("password") == null ? null : String.valueOf(m.get("password"));
            String key = m.get("privateKeyPem") == null ? null : String.valueOf(m.get("privateKeyPem"));
            if (password == null && key == null) {
                throw new UpstreamUnavailableException("CREDENTIAL_UNAVAILABLE", "SEC returned no usable credential");
            }
            return new TransferCredential(String.valueOf(m.get("username")), password, key);
        } catch (UpstreamUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            LOGGER.warn("Credential retrieval failed for ref {}: {}", credentialRef, e.getClass().getSimpleName());
            throw new UpstreamUnavailableException("CREDENTIAL_UNAVAILABLE", "Credential service unavailable", e);
        }
    }
}
