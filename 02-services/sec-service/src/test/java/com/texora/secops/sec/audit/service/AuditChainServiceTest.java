  package com.texora.secops.sec.audit.service;

import com.texora.secops.sec.audit.domain.SecAuditEvent;

import com.texora.secops.sec.audit.repository.SecAuditEventRepository;
import com.texora.secops.sec.audit.service.AuditChainService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditChainService — Hash Chain")
class AuditChainServiceTest {

    @Mock private SecAuditEventRepository auditEventRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    private AuditChainService service;

    @BeforeEach
    void setUp() {
        service = new AuditChainService(auditEventRepository, kafkaTemplate, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("Genesis event gets genesis hash as prevHash")
    void genesisEvent_usesGenesisHash() {
        when(auditEventRepository.findLatestEvent()).thenReturn(Optional.empty());
        when(auditEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SecAuditEvent event = service.appendEvent(
                UUID.randomUUID(), "user-1", "TEST_ACTION",
                "sec_policy", "pol-1", "SUCCESS", null);

        assertThat(event.getPrevHash()).isEqualTo(
                "0000000000000000000000000000000000000000000000000000000000000000");
        assertThat(event.getEventHash()).isNotBlank();
        assertThat(event.getEventHash()).hasSize(64); // SHA-256 hex = 64 chars
    }

    @Test
    @DisplayName("computeEventHash is deterministic — same input always same output")
    void hashIsDeterministic() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String prev = "abc123";

        String hash1 = service.computeEventHash(id, tenantId, "actor", "ACTION",
                "target_type", "target_id", "SUCCESS", prev);
        String hash2 = service.computeEventHash(id, tenantId, "actor", "ACTION",
                "target_type", "target_id", "SUCCESS", prev);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("Hash changes when any field changes — tamper detection")
    void hashChanges_whenFieldModified() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String original = service.computeEventHash(id, tenantId, "actor", "ACTION",
                "target_type", "target_id", "SUCCESS", "prevhash");
        String tampered = service.computeEventHash(id, tenantId, "actor", "ACTION",
                "target_type", "target_id", "FAILURE", "prevhash"); // outcome changed

        assertThat(original).isNotEqualTo(tampered);
    }

    @Test
    @DisplayName("Hash changes when prevHash changes — chain linkage")
    void hashChanges_whenPrevHashChanges() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        String hash1 = service.computeEventHash(id, tenantId, "actor", "ACTION",
                "type", "target", "SUCCESS", "prevhash-A");
        String hash2 = service.computeEventHash(id, tenantId, "actor", "ACTION",
                "type", "target", "SUCCESS", "prevhash-B");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("Second event links to first event's hash")
    void secondEvent_linksToFirstEventHash() {
        UUID tenantId = UUID.randomUUID();

        // Simulate first event already persisted
        SecAuditEvent firstEvent = new SecAuditEvent(
                UUID.randomUUID(), tenantId, "actor", "FIRST_ACTION",
                "type", "target", "SUCCESS", null, "0".repeat(64), "firsthash123");

        when(auditEventRepository.findLatestEvent()).thenReturn(Optional.of(firstEvent));
        when(auditEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SecAuditEvent second = service.appendEvent(
                tenantId, "actor", "SECOND_ACTION", "type", "target", "SUCCESS", null);

        assertThat(second.getPrevHash()).isEqualTo("firsthash123");
    }
}
