package com.texora.secops.vpn.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.vpn.config.VpnProperties;
import com.texora.secops.vpn.domain.AuditEvent;
import com.texora.secops.vpn.domain.AuditOutbox;
import com.texora.secops.vpn.repository.AuditOutboxRepository;
import com.texora.secops.vpn.repository.VpnAuditEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditOutboxPublisherTest {

    private final AuditOutboxRepository outbox = mock(AuditOutboxRepository.class);
    private final VpnAuditEventRepository events = mock(VpnAuditEventRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T05:00:00Z"), ZoneOffset.UTC);
    private final VpnProperties props = new VpnProperties();
    private final AuditOutboxPublisher publisher = new AuditOutboxPublisher(outbox, events, kafka, props, new ObjectMapper(), clock);

    private AuditOutbox row() {
        AuditOutbox o = new AuditOutbox();
        o.setId(UUID.randomUUID());
        o.setAuditEventId(UUID.randomUUID());
        o.setTenantId(UUID.randomUUID());
        return o;
    }

    private AuditEvent event(AuditOutbox o) {
        AuditEvent e = new AuditEvent();
        e.setId(o.getAuditEventId());
        e.setTenantId(o.getTenantId());
        e.setActorId(UUID.randomUUID());
        e.setAction("A");
        e.setTargetType("T");
        e.setTargetId("1");
        e.setOutcome("SUCCESS");
        e.setOccurredAt(clock.instant());
        e.setPayload("{\"k\":\"v\"}");
        return e;
    }

    @Test
    void successfulSendMarksPublished() {
        AuditOutbox o = row();
        when(outbox.findDue(any(Instant.class), any(Pageable.class))).thenReturn(List.of(o));
        when(outbox.claim(eq(o.getId()), any(), any())).thenReturn(1);
        when(events.findById(o.getAuditEventId())).thenReturn(Optional.of(event(o)));
        when(kafka.send(eq("vpn.audit.events"), anyString(), anyString()))
                .thenReturn(CompletableFuture.<SendResult<String, String>>completedFuture(null));
        assertThat(publisher.publishDue()).isEqualTo(1);
        verify(outbox).markPublished(eq(o.getId()), any());
        verify(outbox, never()).markFailed(any(), any(), any());
    }

    @Test
    void failedSendIsRescheduledNeverDropped() {
        AuditOutbox o = row();
        when(outbox.findDue(any(Instant.class), any(Pageable.class))).thenReturn(List.of(o));
        when(outbox.claim(eq(o.getId()), any(), any())).thenReturn(1);
        when(events.findById(o.getAuditEventId())).thenReturn(Optional.of(event(o)));
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.<SendResult<String, String>>failedFuture(new RuntimeException("broker down")));
        assertThat(publisher.publishDue()).isZero();
        verify(outbox, never()).markPublished(any(), any());
        verify(outbox).markFailed(eq(o.getId()), eq(clock.instant().plusSeconds(props.getAudit().getRetryBackoffSeconds())), anyString());
    }

    @Test
    void synchronousSendExceptionIsAlsoRescheduled() {
        AuditOutbox o = row();
        when(outbox.findDue(any(Instant.class), any(Pageable.class))).thenReturn(List.of(o));
        when(outbox.claim(eq(o.getId()), any(), any())).thenReturn(1);
        when(events.findById(o.getAuditEventId())).thenReturn(Optional.of(event(o)));
        when(kafka.send(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("no brokers"));
        assertThat(publisher.publishDue()).isZero();
        verify(outbox).markFailed(eq(o.getId()), any(), anyString());
    }

    @Test
    void rowClaimedByAnotherReplicaIsSkipped() {
        AuditOutbox o = row();
        when(outbox.findDue(any(Instant.class), any(Pageable.class))).thenReturn(List.of(o));
        when(outbox.claim(eq(o.getId()), any(), any())).thenReturn(0);
        assertThat(publisher.publishDue()).isZero();
        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void missingAuditRowIsRetriedNotMarkedPublished() {
        AuditOutbox o = row();
        when(outbox.findDue(any(Instant.class), any(Pageable.class))).thenReturn(List.of(o));
        when(outbox.claim(eq(o.getId()), any(), any())).thenReturn(1);
        when(events.findById(o.getAuditEventId())).thenReturn(Optional.empty());
        assertThat(publisher.publishDue()).isZero();
        verify(outbox, never()).markPublished(any(), any());
    }
}
