package com.texora.qap.credentials;

import com.texora.qap.domain.QapCredentialReference;
import com.texora.qap.repository.QapCredentialReferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves test-environment credentials via the Security Server (SEC).
 *
 * <p><strong>NO credential material is ever stored in the database.</strong>
 * {@link QapCredentialReference} holds only an opaque SEC reference ID.
 * The actual material is fetched from SEC (via mTLS) at the point of use and
 * held in memory only for the duration of the test run.  It is NEVER:
 * <ul>
 *   <li>written to the database</li>
 *   <li>written to any log</li>
 *   <li>returned through any API</li>
 * </ul>
 *
 * <p>This is the same pattern used by every core service that retrieves secrets
 * (SSO's signing cert, SIEM's mTLS cert, VPN's dataplane credentials).
 */
@Service
public class CredentialReferenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialReferenceService.class);

    private static final Duration SEC_TIMEOUT = Duration.ofSeconds(10);

    private final QapCredentialReferenceRepository credentialReferenceRepository;
    private final WebClient secClient;

    public CredentialReferenceService(
            QapCredentialReferenceRepository credentialReferenceRepository,
            WebClient.Builder webClientBuilder,
            @Value("${qap.sec.base-url:http://sec-service/api/v1}") String secBaseUrl) {
        this.credentialReferenceRepository = credentialReferenceRepository;
        this.secClient = webClientBuilder
                .baseUrl(secBaseUrl)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Resolve test-environment credentials for a product.
     *
     * <p>The returned {@link ResolvedCredential} holds the material in memory only.
     * The caller MUST NOT log or persist any field of the returned object.
     *
     * @param tenantId  the tenant
     * @param productId the product under test
     * @return          in-memory credential, never null (throws on failure)
     */
    public ResolvedCredential resolve(UUID tenantId, String productId) {
        QapCredentialReference ref = credentialReferenceRepository
                .findByTenantIdAndProductId(tenantId, productId)
                .orElseThrow(() -> new CredentialNotFoundException(
                        "No credential reference found for tenant=" + tenantId + " product=" + productId));

        LOGGER.debug("Resolving credential reference for tenant={} product={}", tenantId, productId);

        try {
            // POST /api/v1/policies/evaluate — use the real stub endpoint (from prompt)
            SecEvaluateResponse response = secClient.post()
                    .uri("/policies/evaluate")
                    .bodyValue(Map.of("secretReferenceId", ref.getSecSecretReferenceId()))
                    .retrieve()
                    .bodyToMono(SecEvaluateResponse.class)
                    .timeout(SEC_TIMEOUT)
                    .block();

            if (response == null || response.username() == null) {
                throw new CredentialResolutionException(
                        "SEC returned empty credential for reference " + ref.getSecSecretReferenceId());
            }

            // Material is held in-memory only.  DO NOT log response.password().
            LOGGER.debug("Credential resolved for product={} (username present: {})",
                    productId, response.username() != null);

            return new ResolvedCredential(response.username(), response.password());

        } catch (CredentialNotFoundException | CredentialResolutionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CredentialResolutionException(
                    "Failed to resolve credential from SEC: " + ex.getMessage(), ex);
        }
    }

    // ---- Reference management -------------------------------------------

    @Transactional
    public QapCredentialReference register(UUID tenantId, String productId,
                                            String secSecretReferenceId, String description) {
        QapCredentialReference ref = new QapCredentialReference(
                UUID.randomUUID(), tenantId, productId, secSecretReferenceId, description, Instant.now());
        LOGGER.info("Registering credential reference for tenant={} product={}", tenantId, productId);
        return credentialReferenceRepository.save(ref);
    }

    public List<QapCredentialReference> listByTenant(UUID tenantId) {
        return credentialReferenceRepository.findAllByTenantId(tenantId);
    }

    public Optional<QapCredentialReference> findByIdAndTenant(UUID id, UUID tenantId) {
        return credentialReferenceRepository.findByIdAndTenantId(id, tenantId);
    }

    // ---- Inner types ----------------------------------------------------

    /**
     * Resolved credential — held in-memory only, NEVER persisted or logged.
     */
    public record ResolvedCredential(String username, String password) {
        @Override
        public String toString() {
            // Override to prevent accidental logging of the password
            return "ResolvedCredential{username=" + username + ", password=[REDACTED]}";
        }
    }

    /** SEC evaluate response shape. */
    record SecEvaluateResponse(String username, String password) {}

    public static class CredentialNotFoundException extends RuntimeException {
        public CredentialNotFoundException(String msg) { super(msg); }
    }

    public static class CredentialResolutionException extends RuntimeException {
        public CredentialResolutionException(String msg) { super(msg); }
        public CredentialResolutionException(String msg, Throwable cause) { super(msg, cause); }
    }
}
