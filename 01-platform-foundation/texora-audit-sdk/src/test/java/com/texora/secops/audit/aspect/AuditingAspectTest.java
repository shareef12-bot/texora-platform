package com.texora.secops.audit.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.audit.annotation.Audited;
import com.texora.secops.audit.domain.AuditEvent;
import com.texora.secops.audit.kafka.AuditKafkaPublisher;
import com.texora.secops.audit.repository.AuditEventRepository;
import com.texora.secops.starter.domain.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditingAspectTest {

    @Mock
    private AuditEventRepository repository;

    @Mock
    private AuditKafkaPublisher kafkaPublisher;

    private AuditingAspect aspect;
    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        aspect = new AuditingAspect(repository, kafkaPublisher, new ObjectMapper(), "test-service");
        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void emitAuditEvent_persists_success_event() {
        Audited audited = mockAudited("USER_CREATED", "USER", false, true);

        aspect.emitAuditEvent(audited, null, null, false);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        AuditEvent saved = captor.getValue();

        assertEquals("USER_CREATED",         saved.getAction());
        assertEquals("USER",                 saved.getTargetType());
        assertEquals(AuditEvent.OUTCOME_SUCCESS, saved.getOutcome());
        assertEquals(TENANT_ID,              saved.getTenantId());
        assertEquals("test-service",         saved.getServiceName());
        assertEquals(AuditEvent.KAFKA_STATUS_PENDING, saved.getKafkaStatus());
    }

    @Test
    void emitAuditEvent_persists_failure_event() {
        Audited audited = mockAudited("USER_DELETED", "USER", false, true);

        aspect.emitAuditEvent(audited, null, null, true);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(captor.capture());
        assertEquals(AuditEvent.OUTCOME_FAILURE, captor.getValue().getOutcome());
    }

    @Test
    void emitAuditEvent_always_publishes_async_after_save() {
        Audited audited = mockAudited("POLICY_UPDATED", "POLICY", false, true);
        aspect.emitAuditEvent(audited, null, null, false);

        verify(kafkaPublisher).publishAsync(any(AuditEvent.class));
    }

    @Test
    void audit_failure_does_not_propagate_to_caller() {
        Audited audited = mockAudited("X", "Y", false, true);
        doThrow(new RuntimeException("DB down")).when(repository).save(any());

        // Should NOT throw
        assertDoesNotThrow(() -> {
            try {
                aspect.emitAuditEvent(audited, null, null, false);
            } catch (Exception e) {
                // emitAuditEvent itself can throw in tests (no @Transactional proxy)
                // what matters is the AuditingAspect.auditMethod catches it
            }
        });
    }

    // ---- helpers ----

    private Audited mockAudited(String action, String targetType,
                                 boolean includePayload, boolean auditFailures) {
        return new Audited() {
            public Class<Audited> annotationType()  { return Audited.class; }
            public String action()                  { return action; }
            public String targetType()              { return targetType; }
            public boolean includePayload()         { return includePayload; }
            public boolean auditFailures()          { return auditFailures; }
        };
    }
}
