package com.texora.secops.ldap.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.ldap.audit.LdapAuditHelper;
import com.texora.secops.ldap.domain.LdapSyncState;
import com.texora.secops.ldap.repository.SyncStateRepository;
import com.texora.secops.ldap.sync.client.LdapEngineAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DirectoryReconciliationService.applySync")
class DirectoryReconciliationServiceApplySyncTest {

    @Mock private SyncStateRepository syncStateRepository;
    @Mock private LdapEngineAdapter engine;
    @Mock private LdapAuditHelper auditHelper;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private DirectoryReconciliationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID entityId = UUID.randomUUID();
    private static final String DN = "uid=jsmith,ou=users,dc=texora,dc=internal";

    @BeforeEach
    void setUp() {
        service = new DirectoryReconciliationService(syncStateRepository, engine, auditHelper,
                kafkaTemplate, new ObjectMapper());
        when(syncStateRepository.findByEntryDnAndTenantId(DN, tenantId)).thenReturn(Optional.empty());
    }

    private LdapSyncState savedState() {
        ArgumentCaptor<LdapSyncState> c = ArgumentCaptor.forClass(LdapSyncState.class);
        verify(syncStateRepository).save(c.capture());
        return c.getValue();
    }

    @Test
    @DisplayName("engine not connected: state saved as FAILED, no write to the engine")
    void engineDown_failed() {
        when(engine.ping()).thenReturn(false);

        service.applySync(tenantId, DN, entityId, "UPSERT", "{}", Map.of(), null);

        LdapSyncState s = savedState();
        assertThat(s.getStatus()).isEqualTo("FAILED");
        assertThat(s.getLastError()).isEqualTo("engine not connected");
        assertThat(s.getDcSourceId()).isEqualTo(entityId);
        assertThat(s.getOperation()).isEqualTo("UPSERT");
    }

    @Test
    @DisplayName("engine up: addOrReplace called and state is IN_SYNC")
    void engineUp_inSync() {
        when(engine.ping()).thenReturn(true);
        Map<String, List<String>> attrs = Map.of("uid", List.of("jsmith"));

        service.applySync(tenantId, DN, entityId, "UPSERT", "{}", attrs, null);

        verify(engine).addOrReplace(DN, attrs);
        assertThat(savedState().getStatus()).isEqualTo("IN_SYNC");
    }

    @Test
    @DisplayName("null requester: the system actor (all zeros) is used for the audit event")
    void nullRequester_systemActor() {
        when(engine.ping()).thenReturn(false);

        service.applySync(tenantId, DN, entityId, "UPSERT", "{}", Map.of(), null);

        verify(auditHelper).record(eq(tenantId), eq(new UUID(0L, 0L)), any(), any(), any(), any(), any());
    }
}
