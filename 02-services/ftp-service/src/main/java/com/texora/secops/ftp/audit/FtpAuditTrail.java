package com.texora.secops.ftp.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.audit.annotation.Audited;
import com.texora.secops.ftp.domain.AuditEvent;
import com.texora.secops.ftp.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Single audit facade for the FTP service.
 *
 * <ul>
 *   <li>Each public method carries the SHARED {@code @Audited} annotation, so the shared Audit SDK aspect emits the
 *       immutable event to the {@code ftp.audit.events} Kafka topic (this class does NOT publish to Kafka itself and
 *       is not a reimplementation of the aspect).</li>
 *   <li>It also writes the searchable, append-only {@code ftp_audit_event} row this service's LLD requires
 *       (GET /api/v1/audit). Actor and tenant are passed explicitly so it works identically on request threads and
 *       on async orchestration threads.</li>
 * </ul>
 *
 * Mutations join the caller's transaction (audit and change commit or roll back together). Denials use
 * REQUIRES_NEW so they persist even though the denied operation is rolled back / throws.
 * Payloads must never contain file bytes, secrets or credentials.
 */
@Component
public class FtpAuditTrail {

    private static final Logger LOGGER = LoggerFactory.getLogger(FtpAuditTrail.class);

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate joined;
    private final TransactionTemplate independent;

    public FtpAuditTrail(AuditEventRepository repository, ObjectMapper objectMapper, PlatformTransactionManager tm) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.joined = new TransactionTemplate(tm);
        this.independent = new TransactionTemplate(tm);
        this.independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ---- jobs
    @Audited(action = "FTP_JOB_SUBMITTED", targetType = "TRANSFER_JOB")
    public void jobSubmitted(UUID tenantId, String actor, UUID jobId, Map<String, Object> details) {
        write(tenantId, actor, "FTP_JOB_SUBMITTED", "TRANSFER_JOB", jobId, "SUCCESS", details, false);
    }

    @Audited(action = "FTP_JOB_CANCELLED", targetType = "TRANSFER_JOB")
    public void jobCancelled(UUID tenantId, String actor, UUID jobId) {
        write(tenantId, actor, "FTP_JOB_CANCELLED", "TRANSFER_JOB", jobId, "SUCCESS", Map.of(), false);
    }

    @Audited(action = "FTP_JOB_COMPLETED", targetType = "TRANSFER_JOB")
    public void jobCompleted(UUID tenantId, String actor, UUID jobId, Map<String, Object> details) {
        write(tenantId, actor, "FTP_JOB_COMPLETED", "TRANSFER_JOB", jobId, "SUCCESS", details, true);
    }

    @Audited(action = "FTP_JOB_FAILED", targetType = "TRANSFER_JOB")
    public void jobFailed(UUID tenantId, String actor, UUID jobId, String reason) {
        write(tenantId, actor, "FTP_JOB_FAILED", "TRANSFER_JOB", jobId, "FAILURE", Map.of("reason", reason), true);
    }

    @Audited(action = "FTP_TRANSFER_ATTEMPT", targetType = "TRANSFER_JOB")
    public void transferAttempt(UUID tenantId, String actor, UUID jobId, Map<String, Object> details) {
        write(tenantId, actor, "FTP_TRANSFER_ATTEMPT", "TRANSFER_JOB", jobId, "SUCCESS", details, true);
    }

    // ---- checksum / inspection / quarantine
    @Audited(action = "FTP_CHECKSUM_RESULT", targetType = "TRANSFER_JOB")
    public void checksumResult(UUID tenantId, String actor, UUID jobId, boolean verified, Map<String, Object> details) {
        write(tenantId, actor, "FTP_CHECKSUM_RESULT", "TRANSFER_JOB", jobId, verified ? "SUCCESS" : "FAILURE", details, false);
    }

    @Audited(action = "FTP_INSPECTION_RESULT", targetType = "TRANSFER_JOB")
    public void inspectionResult(UUID tenantId, String actor, UUID jobId, String outcome, Map<String, Object> details) {
        write(tenantId, actor, "FTP_INSPECTION_RESULT", "TRANSFER_JOB", jobId, outcome, details, false);
    }

    @Audited(action = "FTP_QUARANTINE_PLACED", targetType = "QUARANTINE_RECORD")
    public void quarantinePlaced(UUID tenantId, String actor, UUID quarantineId, Map<String, Object> details) {
        write(tenantId, actor, "FTP_QUARANTINE_PLACED", "QUARANTINE_RECORD", quarantineId, "SUCCESS", details, false);
    }

    @Audited(action = "FTP_QUARANTINE_RELEASED", targetType = "QUARANTINE_RECORD")
    public void quarantineReleased(UUID tenantId, String actor, UUID quarantineId, Map<String, Object> details) {
        write(tenantId, actor, "FTP_QUARANTINE_RELEASED", "QUARANTINE_RECORD", quarantineId, "SUCCESS", details, false);
    }

    @Audited(action = "FTP_QUARANTINE_DESTROYED", targetType = "QUARANTINE_RECORD")
    public void quarantineDestroyed(UUID tenantId, String actor, UUID quarantineId, Map<String, Object> details) {
        write(tenantId, actor, "FTP_QUARANTINE_DESTROYED", "QUARANTINE_RECORD", quarantineId, "SUCCESS", details, false);
    }

    /** Always persisted independently - the denied operation rolls back. */
    @Audited(action = "FTP_QUARANTINE_RELEASE_DENIED", targetType = "QUARANTINE_RECORD")
    public void quarantineReleaseDenied(UUID tenantId, String actor, UUID quarantineId, String reason) {
        write(tenantId, actor, "FTP_QUARANTINE_RELEASE_DENIED", "QUARANTINE_RECORD", quarantineId, "DENIED",
                Map.of("reason", reason), true);
    }

    // ---- administration / access
    @Audited(action = "FTP_ENDPOINT_REGISTERED", targetType = "ENDPOINT")
    public void endpointRegistered(UUID tenantId, String actor, UUID endpointId, Map<String, Object> details) {
        write(tenantId, actor, "FTP_ENDPOINT_REGISTERED", "ENDPOINT", endpointId, "SUCCESS", details, false);
    }

    @Audited(action = "FTP_CONFIG_CHANGED", targetType = "CONFIG")
    public void configChanged(UUID tenantId, String actor, UUID configId, Map<String, Object> details) {
        write(tenantId, actor, "FTP_CONFIG_CHANGED", "CONFIG", configId, "SUCCESS", details, false);
    }

    @Audited(action = "FTP_POLICY_DECISION", targetType = "TRANSFER_JOB")
    public void policyDecision(UUID tenantId, String actor, UUID jobId, boolean allowed, Map<String, Object> details) {
        write(tenantId, actor, "FTP_POLICY_DECISION", "TRANSFER_JOB", jobId, allowed ? "ALLOW" : "DENY", details, true);
    }

    @Audited(action = "FTP_RBAC_DENIED", targetType = "API")
    public void rbacDenied(UUID tenantId, String actor, Map<String, Object> details) {
        write(tenantId, actor, "FTP_RBAC_DENIED", "API", null, "DENIED", details, true);
    }

    @Audited(action = "FTP_ENDPOINT_REJECTED", targetType = "ENDPOINT")
    public void endpointRejected(UUID tenantId, String actor, UUID endpointId, String reason) {
        write(tenantId, actor, "FTP_ENDPOINT_REJECTED", "ENDPOINT", endpointId, "DENIED", Map.of("reason", reason), true);
    }

    private void write(UUID tenantId, String actor, String action, String targetType, UUID targetId, String outcome,
                       Map<String, Object> details, boolean independentTx) {
        final String payload = toJson(details);
        final AuditEvent event = new AuditEvent(UUID.randomUUID(), tenantId, actor, action, targetType, targetId,
                outcome, Instant.now(), payload);
        TransactionTemplate template = independentTx ? independent : joined;
        template.executeWithoutResult(status -> repository.save(event));
        LOGGER.debug("Audit {} {} target={} outcome={}", action, targetType, targetId, outcome);
    }

    private String toJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details == null ? Map.of() : details);
        } catch (JsonProcessingException e) {
            LOGGER.error("Could not serialise audit payload", e);
            return "{\"error\":\"payload-serialisation-failed\"}";
        }
    }
}
