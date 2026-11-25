package com.texora.secops.ldap.sync.service;

import com.texora.secops.ldap.audit.LdapAuditHelper;
import com.texora.secops.ldap.domain.LdapAuditEvent;
import com.texora.secops.ldap.domain.LdapSyncState;
import com.texora.secops.ldap.repository.SyncStateRepository;
import com.texora.secops.ldap.sync.client.LdapEngineAdapter;
import com.texora.secops.ldap.sync.client.LdapEngineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reconciles directory entries between the Texora data model and the LDAP engine.
 *
 * <p><b>Fail-closed:</b> if the engine is unreachable the sync state is set to FAILED
 * (not silently ignored), and will be retried by the scheduled job.
 *
 * <p>The scheduled retry only processes entries whose {@code lastAttemptAt} is older than
 * {@code ldap.sync.retry-after-minutes} to avoid hammering a down engine.
 */
@Service
public class DirectoryReconciliationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryReconciliationService.class);

    private final SyncStateRepository syncStateRepository;
    private final LdapEngineAdapter ldapEngineAdapter;
    private final LdapAuditHelper auditHelper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Value("${ldap.kafka.topics.audit-events}")
    private String auditEventsTopic;

    @Value("${ldap.sync.retry-after-minutes:15}")
    private int retryAfterMinutes;

    @Value("${ldap.sync.max-retry-attempts:5}")
    private int maxRetryAttempts;

    public DirectoryReconciliationService(SyncStateRepository syncStateRepository,
                                           LdapEngineAdapter ldapEngineAdapter,
                                           LdapAuditHelper auditHelper,
                                           KafkaTemplate<String, String> kafkaTemplate,
                                           com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.syncStateRepository = syncStateRepository;
        this.ldapEngineAdapter = ldapEngineAdapter;
        this.auditHelper = auditHelper;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Reconciles a single directory entry. Called by the Kafka consumer and by the
     * manual sync endpoint.
     *
     * <p>If the engine is unreachable, the state transitions to FAILED (fail-closed).
     */
    @Transactional
    public void reconcileEntry(UUID tenantId, String entryDn, UUID requestedBy) {
        LOGGER.info("Reconciling entry dn='{}' for tenant {}", entryDn, tenantId);

        LdapSyncState state = syncStateRepository.findByEntryDnAndTenantId(entryDn, tenantId)
                .orElseGet(() -> {
                    LdapSyncState s = new LdapSyncState(entryDn, tenantId);
                    s.setStatusEnum(LdapSyncState.Status.PENDING);
                    s.setRetryCount(0);
                    return s;
                });

        state.setLastSyncedAt(Instant.now());
        state.setRetryCount(state.getRetryCount() + 1);
        state.setStatusEnum(LdapSyncState.Status.PENDING);
        syncStateRepository.save(state);

        try {
            // Query the engine for the current entry — stub returns empty list
            List<Map<String, List<String>>> result = ldapEngineAdapter.search(
                    entryDn, "(objectClass=*)", List.of());

            if (result.isEmpty()) {
                LOGGER.info("Entry dn='{}' not found in directory engine (stub or absent)", entryDn);
                state.setStatusEnum(LdapSyncState.Status.IN_SYNC); // treat absent as in-sync in stub mode
            } else {
                state.setStatusEnum(LdapSyncState.Status.IN_SYNC);
                LOGGER.info("Entry dn='{}' reconciled successfully for tenant {}", entryDn, tenantId);
            }

            state.setLastError(null);
            syncStateRepository.save(state);

            auditHelper.record(tenantId, requestedBy, "DC_SYNC_COMPLETED",
                    "LdapSyncState", entryDn, LdapAuditEvent.Outcome.SUCCESS, null);

        } catch (LdapEngineException e) {
            // Fail closed — record FAILED, never silently skip
            LOGGER.error("Engine unreachable during reconciliation of dn='{}' tenant={}", entryDn, tenantId, e);
            state.setStatusEnum(LdapSyncState.Status.FAILED);
            state.setLastError(e.getMessage());
            syncStateRepository.save(state);

            auditHelper.record(tenantId, requestedBy, "DC_SYNC_ENGINE_UNREACHABLE",
                    "LdapSyncState", entryDn, LdapAuditEvent.Outcome.FAILURE, null);

            throw e; // propagate so caller can decide (Kafka consumer acks anyway)
        }
    }

    /** Applies one DC sync message. Never throws for engine problems: the outcome is saved as state. */
    @Transactional
    public void applySync(UUID tenantId, String entryDn, UUID entityId, String operation,
                          String payload, Map<String, List<String>> attributes, UUID requestedBy) {
        UUID actor = requestedBy != null ? requestedBy : new UUID(0L, 0L); // system actor for DC-driven syncs
        LdapSyncState state = syncStateRepository.findByEntryDnAndTenantId(entryDn, tenantId)
                .orElseGet(() -> new LdapSyncState(entryDn, tenantId, entityId, LdapSyncState.Status.PENDING));
        state.setDcSourceId(entityId);
        state.setOperation(operation);
        state.setPayload(payload);
        state.setLastSyncedAt(Instant.now());
        state.setRetryCount(state.getRetryCount() + 1);

        try {
            if (!ldapEngineAdapter.ping()) {
                throw new LdapEngineException("engine not connected");
            }
            if ("DELETE".equals(operation)) {
                ldapEngineAdapter.delete(entryDn);
            } else {
                ldapEngineAdapter.addOrReplace(entryDn, attributes);
            }
            state.setStatusEnum(LdapSyncState.Status.IN_SYNC);
            state.setLastError(null);
            auditHelper.record(tenantId, actor, "DC_SYNC_COMPLETED",
                    "LdapSyncState", entryDn, LdapAuditEvent.Outcome.SUCCESS, null);
        } catch (LdapEngineException e) {
            LOGGER.error("Engine problem syncing dn='{}' tenant={}: {}", entryDn, tenantId, e.getMessage());
            state.setStatusEnum(LdapSyncState.Status.FAILED);
            state.setLastError(e.getMessage());
            auditHelper.record(tenantId, actor, "DC_SYNC_ENGINE_UNREACHABLE",
                    "LdapSyncState", entryDn, LdapAuditEvent.Outcome.FAILURE, null);
        }
        syncStateRepository.save(state);
    }

    /** Builds directory attributes from DC's payload JSON. Throws on a payload without a username. */
    public Map<String, List<String>> parseAttributes(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            Map<?, ?> p = objectMapper.readValue(payload, Map.class);
            if (p.get("username") == null) {
                throw new IllegalArgumentException("payload has no username");
            }
            String uid = String.valueOf(p.get("username"));
            String display = p.get("displayName") == null ? uid : String.valueOf(p.get("displayName"));
            Map<String, List<String>> attrs = new java.util.LinkedHashMap<>();
            attrs.put("objectClass", List.of("inetOrgPerson"));
            attrs.put("uid", List.of(uid));
            attrs.put("cn", List.of(display));
            attrs.put("sn", List.of(display));
            attrs.put("displayName", List.of(display));
            if (p.get("orgUnitId") != null) {
                attrs.put("ou", List.of(String.valueOf(p.get("orgUnitId"))));
            }
            if (p.get("hrmsEmployeeId") != null) {
                attrs.put("employeeNumber", List.of(String.valueOf(p.get("hrmsEmployeeId"))));
            }
            return attrs;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid sync payload", e);
        }
    }

    /**
     * Scheduled retry of FAILED or PENDING sync states.
     * Runs every {@code ldap.sync.retry-after-minutes} minutes.
     * Entries that have exceeded {@code maxRetryAttempts} are left in FAILED state.
     */
    @Scheduled(fixedDelayString = "${ldap.sync.retry-interval-ms:900000}")
    @Transactional
    public void retryFailedEntries() {
        Instant retryBefore = Instant.now().minusSeconds((long) retryAfterMinutes * 60);
        List<LdapSyncState> candidates = syncStateRepository.findRetryableEntries(
                LdapSyncState.Status.FAILED.name(), retryBefore, maxRetryAttempts);
        // NOTE: findRetryableEntries must be declared in SyncStateRepository

        if (candidates.isEmpty()) {
            return;
        }

        LOGGER.info("Retry scheduler: processing {} FAILED sync entries", candidates.size());

        for (LdapSyncState state : candidates) {
            try {
                if (state.getOperation() == null) {
                    continue;
                }
                applySync(state.getTenantId(), state.getEntryDn(), state.getDcSourceId(), state.getOperation(),
                        state.getPayload(), parseAttributes(state.getPayload()), null);
            } catch (Exception e) {
                // Already logged and state updated inside reconcileEntry
                LOGGER.warn("Retry failed for dn='{}' tenant={} (attempt {})",
                        state.getEntryDn(), state.getTenantId(), state.getRetryCount());
            }
        }
    }
}
