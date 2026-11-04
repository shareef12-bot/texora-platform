package com.texora.secops.siem.normalization;

import com.texora.secops.siem.normalization.model.NormalizedEvent;
import com.texora.secops.siem.normalization.service.NormalizationException;
import com.texora.secops.siem.normalization.service.RawEventContext;
import com.texora.secops.siem.normalization.service.profile.LdapAuditProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LdapAuditProfileTest {

    private final LdapAuditProfile profile = new LdapAuditProfile();

    @Test
    void mapsTheSharedAuditSdkShapeIntoTheCommonSchema() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String raw = "{"
                + "\"id\":\"evt-123\","
                + "\"tenantId\":\"" + tenantId + "\","
                + "\"actorId\":\"" + UUID.randomUUID() + "\","
                + "\"action\":\"bind.failed\","
                + "\"targetType\":\"LdapPrincipal\","
                + "\"targetId\":\"cn=jdoe\","
                + "\"outcome\":\"FAILURE\","
                + "\"occurredAt\":\"2026-01-15T10:00:00Z\"}";

        NormalizedEvent event = profile.normalize(new RawEventContext("ldap", raw, Instant.now()));

        assertThat(event.getSourceService()).isEqualTo("ldap");
        assertThat(event.getEventType()).isEqualTo("bind.failed");
        assertThat(event.getTenantScope()).isEqualTo(tenantId);
        assertThat(event.getTarget()).isEqualTo("LdapPrincipal:cn=jdoe");
        assertThat(event.getOutcome()).isEqualTo("FAILURE");
    }

    @Test
    void malformedJsonIsRejectedRatherThanSilentlyAccepted() {
        assertThatThrownBy(() -> profile.normalize(new RawEventContext("ldap", "not json at all", Instant.now())))
                .isInstanceOf(NormalizationException.class);
    }

    @Test
    void eventWithNoResolvableTenantIsRejected() {
        String raw = "{\"id\":\"evt-1\",\"action\":\"bind.failed\",\"outcome\":\"FAILURE\"}";
        assertThatThrownBy(() -> profile.normalize(new RawEventContext("ldap", raw, Instant.now())))
                .isInstanceOf(NormalizationException.class);
    }
}
