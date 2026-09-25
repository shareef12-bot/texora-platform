package com.texora.secops.sec.secrets.dto;

import java.util.UUID;

/**
 * Response for secret reference resolution.
 *
 * <p>Contains ONLY the vault path and KMS key ID — NEVER raw secret material.
 * The calling service uses these references to contact the vault/KMS directly
 * using its own mTLS identity.</p>
 */
public class SecretReferenceResponse {

    private UUID referenceId;
    private String logicalName;
    private String vaultPath;
    private String kmsKeyId;

    public SecretReferenceResponse() {}

    public SecretReferenceResponse(UUID referenceId, String logicalName,
                                   String vaultPath, String kmsKeyId) {
        this.referenceId = referenceId;
        this.logicalName = logicalName;
        this.vaultPath = vaultPath;
        this.kmsKeyId = kmsKeyId;
    }

    public UUID getReferenceId() { return referenceId; }
    public void setReferenceId(UUID referenceId) { this.referenceId = referenceId; }

    public String getLogicalName() { return logicalName; }
    public void setLogicalName(String logicalName) { this.logicalName = logicalName; }

    public String getVaultPath() { return vaultPath; }
    public void setVaultPath(String vaultPath) { this.vaultPath = vaultPath; }

    public String getKmsKeyId() { return kmsKeyId; }
    public void setKmsKeyId(String kmsKeyId) { this.kmsKeyId = kmsKeyId; }
}
