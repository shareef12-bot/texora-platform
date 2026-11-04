package com.texora.secops.vpn.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.vpn.domain.AuditEvent;
import com.texora.secops.vpn.domain.AuditOutbox;
import com.texora.secops.vpn.repository.AuditOutboxRepository;
import com.texora.secops.vpn.repository.VpnAuditEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VpnAuditRecorderTest {

    private final VpnAuditEventRepository events = mock(VpnAuditEventRepository.class);
    private final AuditOutboxRepository outbox = mock(AuditOutboxRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T05:00:00Z"), ZoneOffset.UTC);
    private final VpnAuditRecorder recorder = new VpnAuditRecorder(events, outbox, new ObjectMapper(), clock);

    @Test
    void writesAuditRowAndOutboxRowTogether() {
        UUID tenant = UUID.randomUUID();
        recorder.record(tenant, UUID.randomUUID(), "SESSION_ESTABLISHED", "VPN_SESSION", "s1", "SUCCESS", Map.of("a", "b"));
        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        ArgumentCaptor<AuditOutbox> box = ArgumentCaptor.forClass(AuditOutbox.class);
        verify(events).save(ev.capture());
        verify(outbox).save(box.capture());
        assertThat(ev.getValue().getTenantId()).isEqualTo(tenant);
        assertThat(ev.getValue().getPayload()).contains("\"a\":\"b\"");
        assertThat(box.getValue().getAuditEventId()).isEqualTo(ev.getValue().getId());
        assertThat(box.getValue().getPublishedAt()).isNull();
        assertThat(box.getValue().getNextAttemptAt()).isEqualTo(clock.instant());
    }

    @Test
    void secretLookingKeysAndPemValuesAreRedacted() {
        Map<String, Object> in = new LinkedHashMap<>();
        in.put("mfaCode", "123456");
        in.put("clientSecret", "x");
        in.put("privateKey", "x");
        in.put("accessToken", "x");
        in.put("note", "-----BEGIN PRIVATE KEY-----abc");
        in.put("trigger", "SEC_UNAVAILABLE");
        Map<String, Object> out = recorder.sanitize(in);
        assertThat(out.get("mfaCode")).isEqualTo("[REDACTED]");
        assertThat(out.get("clientSecret")).isEqualTo("[REDACTED]");
        assertThat(out.get("privateKey")).isEqualTo("[REDACTED]");
        assertThat(out.get("accessToken")).isEqualTo("[REDACTED]");
        assertThat(out.get("note")).isEqualTo("[REDACTED]");
        assertThat(out.get("trigger")).isEqualTo("SEC_UNAVAILABLE");
        assertThat(recorder.sanitize(null)).isEmpty();
    }

    @Test
    void nullPayloadIsStoredAsEmptyObject() {
        recorder.record(UUID.randomUUID(), UUID.randomUUID(), "X", "T", "1", "SUCCESS", null);
        ArgumentCaptor<AuditEvent> ev = ArgumentCaptor.forClass(AuditEvent.class);
        verify(events).save(ev.capture());
        assertThat(ev.getValue().getPayload()).isEqualTo("{}");
        verify(outbox).save(any(AuditOutbox.class));
    }
}
