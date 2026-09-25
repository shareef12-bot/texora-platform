package com.texora.secops.sec.secrets.service;

import com.texora.secops.sec.secrets.adapter.VaultKmsAdapter;
import com.texora.secops.sec.secrets.domain.SecSecretReference;
import com.texora.secops.sec.secrets.dto.SecretReferenceResponse;
import com.texora.secops.sec.secrets.repository.SecSecretReferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Manages secret references — vault paths and KMS key IDs only, never raw material.
 *
 * <p>CRITICAL: Raw secret material MUST NEVER transit this service, be stored here,
 * or appear in any log. SEC returns references; calling services retrieve material
 * directly from the vault/KMS using their own mTLS identity.</p>
 *
 * <p>Access is owner-scoped: a caller that is not the owning_service receives
 * 403 Forbidden.</p>
 */
@Service
public class SecretReferenceService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecretReferenceService.class);

    private final SecSecretReferenceRepository referenceRepository;
    private final VaultKmsAdapter vaultKmsAdapter;

    public SecretReferenceService(SecSecretReferenceRepository referenceRepository,
                                   VaultKmsAdapter vaultKmsAdapter) {
        this.referenceRepository = referenceRepository;
        this.vaultKmsAdapter = vaultKmsAdapter;
    }

    @Transactional(readOnly = true)
    public Page<SecSecretReference> listReferences(UUID tenantId, String callerService, Pageable pageable) {
        // Scope listing to the owning service — callers see only their own references
        return referenceRepository.findByTenantIdAndOwningService(tenantId, callerService, pageable);
    }

    /**
     * Registers a new secret reference. The vault path is verified via VaultKmsAdapter
     * before saving.
     */
    @Transactional
    public SecSecretReference registerReference(UUID tenantId, String logicalName, String vaultPath,
                                                 String kmsKeyId, String owningService,
                                                 String classification) {
        // Verify the vault path is accessible before we register it
        if (!vaultKmsAdapter.verifyVaultPath(vaultPath)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Vault path is not accessible or invalid");
        }

        // Check for duplicate logical name within tenant
        referenceRepository.findByTenantIdAndLogicalName(tenantId, logicalName)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Secret reference with logical name '" + logicalName + "' already exists");
                });

        SecSecretReference reference = new SecSecretReference(
                UUID.randomUUID(), tenantId, logicalName, vaultPath,
                kmsKeyId, owningService, classification);

        SecSecretReference saved = referenceRepository.save(reference);
        LOGGER.info("Registered secret reference id={} logicalName='{}' owningService={}",
                saved.getId(), logicalName, owningService);
        return saved;
    }

    /**
     * Resolves a secret reference for the calling service.
     *
     * <p>Returns {vaultPath, kmsKeyId} ONLY — never the actual secret material.</p>
     *
     * <p>If the caller is not the owning_service: 403 Forbidden (Req 2).</p>
     */
    @Transactional(readOnly = true)
    public SecretReferenceResponse resolveReference(UUID tenantId, String logicalName,
                                                     String callerService) {
        SecSecretReference reference = referenceRepository
                .findByTenantIdAndLogicalName(tenantId, logicalName)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Secret reference not found: " + logicalName));

        // =================================================================
        // OWNER-SCOPE ENFORCEMENT: caller must be the owning service.
        // Not the owner → 403, not an empty result.
        // =================================================================
        if (!callerService.equals(reference.getOwningService())) {
            LOGGER.warn("SECRET_ACCESS_DENIED: caller={} is not owner={} for reference={}",
                    callerService, reference.getOwningService(), logicalName);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Caller service '" + callerService + "' is not the owner of this secret reference");
        }

        if (!"ACTIVE".equals(reference.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Secret reference is not active, status: " + reference.getStatus());
        }

        LOGGER.info("Secret reference resolved: logicalName='{}' caller={}", logicalName, callerService);

        // Return REFERENCE ONLY — caller uses this to contact vault/KMS directly
        return new SecretReferenceResponse(
                reference.getId(),
                reference.getLogicalName(),
                reference.getVaultPath(),
                reference.getKmsKeyId()
        );
    }
}
