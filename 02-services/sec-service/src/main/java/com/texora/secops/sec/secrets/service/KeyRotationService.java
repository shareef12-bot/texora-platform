package com.texora.secops.sec.secrets.service;

import com.texora.secops.sec.secrets.adapter.VaultKmsAdapter;
import com.texora.secops.sec.secrets.domain.SecKeyRotationRecord;
import com.texora.secops.sec.secrets.domain.SecSecretReference;
import com.texora.secops.sec.secrets.repository.SecKeyRotationRecordRepository;
import com.texora.secops.sec.secrets.repository.SecSecretReferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class KeyRotationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyRotationService.class);

    private final SecKeyRotationRecordRepository rotationRepository;
    private final SecSecretReferenceRepository referenceRepository;
    private final VaultKmsAdapter vaultKmsAdapter;

    public KeyRotationService(SecKeyRotationRecordRepository rotationRepository,
                               SecSecretReferenceRepository referenceRepository,
                               VaultKmsAdapter vaultKmsAdapter) {
        this.rotationRepository = rotationRepository;
        this.referenceRepository = referenceRepository;
        this.vaultKmsAdapter = vaultKmsAdapter;
    }

    @Transactional(readOnly = true)
    public Page<SecKeyRotationRecord> listRotationRecords(UUID tenantId, Pageable pageable) {
        return rotationRepository.findByTenantIdOrderByRotationDueAtDesc(tenantId, pageable);
    }

    @Transactional
    public SecKeyRotationRecord scheduleRotation(UUID tenantId, UUID secretReferenceId,
                                                  Instant rotationDueAt) {
        referenceRepository.findByIdAndTenantId(secretReferenceId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Secret reference not found: " + secretReferenceId));

        SecKeyRotationRecord record = new SecKeyRotationRecord(
                UUID.randomUUID(), tenantId, secretReferenceId, rotationDueAt);
        SecKeyRotationRecord saved = rotationRepository.save(record);
        LOGGER.info("Scheduled key rotation for secretReferenceId={} dueAt={}",
                secretReferenceId, rotationDueAt);
        return saved;
    }

    @Transactional
    public SecKeyRotationRecord recordRotationOutcome(UUID tenantId, UUID rotationRecordId,
                                                       UUID rotatedBy, String outcome) {
        SecKeyRotationRecord record = rotationRepository.findById(rotationRecordId)
                .filter(r -> tenantId.equals(r.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Rotation record not found: " + rotationRecordId));

        SecSecretReference reference = referenceRepository
                .findById(record.getSecretReferenceId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Secret reference not found"));

        // Trigger the actual rotation in the vault/KMS via the adapter
        boolean triggered = vaultKmsAdapter.triggerRotation(
                reference.getVaultPath(), reference.getKmsKeyId());

        record.setRotatedAt(Instant.now());
        record.setRotatedBy(rotatedBy);
        record.setOutcome(triggered ? "SUCCESS" : "FAILED");

        SecKeyRotationRecord saved = rotationRepository.save(record);
        LOGGER.info("Rotation outcome recorded: recordId={} outcome={}", rotationRecordId, outcome);
        return saved;
    }
}
