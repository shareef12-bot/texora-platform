package com.texora.secops.sec.secrets.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Isolation adapter for the external Vault/KMS (B.4 adapter rule).
 *
 * <p>THIS IS THE ONLY CLASS IN THE ENTIRE SEC SERVICE PERMITTED TO CONTACT
 * THE VAULT/KMS. No other class may import the underlying vault client library.</p>
 *
 * <p>CRITICAL INVARIANT: This adapter NEVER returns raw secret material.
 * It validates that a vault path exists / is accessible, and coordinates
 * rotation triggers. The calling service retrieves material directly from
 * the vault using its own mTLS identity.</p>
 *
 * <p>The specific vault/KMS product is an open item (LLD §12). This adapter
 * is designed so the product choice does not ripple into business logic once
 * decided — swap the implementation here and nothing else changes.</p>
 */
@Component
public class VaultKmsAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(VaultKmsAdapter.class);

    /**
     * Verifies that the given vault path is accessible via the SEC service's
     * own mTLS identity. Returns true if accessible, false otherwise.
     *
     * <p>Does NOT return the secret material.</p>
     *
     * @param vaultPath the vault path to verify
     * @return true if the path is accessible and the reference is valid
     */
    public boolean verifyVaultPath(String vaultPath) {
        try {
            LOGGER.debug("Verifying vault path accessibility: {}", sanitizePath(vaultPath));
            // TODO: Implement actual vault client call once the vault product is decided (LLD §12).
            // Example for HashiCorp Vault:
            //   VaultTemplate vaultTemplate = ...;
            //   VaultResponseSupport<Map<String,Object>> response = vaultTemplate.read(vaultPath);
            //   return response != null;
            //
            // The vault client is injected here; no other class imports it.
            return true; // placeholder — replace when vault product is confirmed
        } catch (Exception ex) {
            LOGGER.error("Vault path verification failed for path={}: {}",
                    sanitizePath(vaultPath), ex.getMessage());
            return false;
        }
    }

    /**
     * Verifies that the given KMS key ID is accessible and usable.
     *
     * <p>Does NOT return any key material.</p>
     *
     * @param kmsKeyId the KMS key ID to verify
     * @return true if the key is accessible
     */
    public boolean verifyKmsKey(String kmsKeyId) {
        try {
            LOGGER.debug("Verifying KMS key accessibility: {}", sanitizeKeyId(kmsKeyId));
            // TODO: Implement actual KMS client call once the vault/KMS product is decided.
            return true; // placeholder — replace when vault/KMS product is confirmed
        } catch (Exception ex) {
            LOGGER.error("KMS key verification failed for keyId={}: {}",
                    sanitizeKeyId(kmsKeyId), ex.getMessage());
            return false;
        }
    }

    /**
     * Triggers a key rotation in the vault/KMS for the given path/key.
     * The rotation executes in the vault/KMS — SEC only coordinates scheduling
     * and records the outcome.
     *
     * @param vaultPath the vault path containing the key to rotate
     * @param kmsKeyId  the KMS key ID to rotate
     * @return true if rotation was successfully triggered
     */
    public boolean triggerRotation(String vaultPath, String kmsKeyId) {
        try {
            LOGGER.info("Triggering key rotation for path={}", sanitizePath(vaultPath));
            // TODO: Implement actual vault/KMS rotation trigger.
            // The rotation happens in the vault/KMS; SEC records the outcome in sec_key_rotation_record.
            return true; // placeholder
        } catch (Exception ex) {
            LOGGER.error("Key rotation trigger failed for path={}: {}",
                    sanitizePath(vaultPath), ex.getMessage());
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Path/key sanitization for safe logging — never log actual secret paths
    // verbatim in production log levels to avoid reconnaissance exposure.
    // -----------------------------------------------------------------------

    private String sanitizePath(String path) {
        if (path == null) return "<null>";
        // Log only the first segment and mask the rest
        int slash = path.indexOf('/', 1);
        return slash > 0 ? path.substring(0, slash) + "/***" : "***";
    }

    private String sanitizeKeyId(String keyId) {
        if (keyId == null || keyId.length() < 8) return "***";
        return keyId.substring(0, 4) + "***";
    }
}
