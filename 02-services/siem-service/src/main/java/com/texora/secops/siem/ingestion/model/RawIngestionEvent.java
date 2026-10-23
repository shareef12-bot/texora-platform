package com.texora.secops.siem.ingestion.model;

import com.texora.secops.siem.domain.enums.Protocol;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * What {@code MultiProtocolIngestionAdapter} hands to {@code IngestionOrchestrationService}
 * once a source has been authenticated, regardless of which wire protocol it arrived on. This
 * is the seam that keeps Syslog/HTTP/Kafka specifics out of every class except the adapter (B.4).
 *
 * <p>{@code sourceKey} is the logical source identity used to pick a {@code NormalizationProfile}
 * (e.g. "ldap", "crm") - for the twelve platform Kafka topics this is derived directly from the
 * topic name; for an admin-registered HTTP/Syslog source it is that source's registered name.
 */
public final class RawIngestionEvent {

    private final UUID ingestionSourceId;
    private final String sourceKey;
    private final Protocol protocol;
    private final Instant receivedAt;
    private final String rawPayload;

    public RawIngestionEvent(UUID ingestionSourceId, String sourceKey, Protocol protocol,
                              Instant receivedAt, String rawPayload) {
        this.ingestionSourceId = ingestionSourceId;
        this.sourceKey = sourceKey;
        this.protocol = protocol;
        this.receivedAt = receivedAt;
        this.rawPayload = rawPayload;
    }

    public UUID getIngestionSourceId() {
        return ingestionSourceId;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RawIngestionEvent)) {
            return false;
        }
        RawIngestionEvent that = (RawIngestionEvent) other;
        return Objects.equals(ingestionSourceId, that.ingestionSourceId)
                && Objects.equals(receivedAt, that.receivedAt)
                && Objects.equals(rawPayload, that.rawPayload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ingestionSourceId, receivedAt, rawPayload);
    }
}
