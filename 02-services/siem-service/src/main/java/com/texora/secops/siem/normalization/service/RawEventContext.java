package com.texora.secops.siem.normalization.service;

import java.time.Instant;

public final class RawEventContext {

    private final String sourceKey;
    private final String rawPayload;
    private final Instant ingestedAt;

    public RawEventContext(String sourceKey, String rawPayload, Instant ingestedAt) {
        this.sourceKey = sourceKey;
        this.rawPayload = rawPayload;
        this.ingestedAt = ingestedAt;
    }

    public String getSourceKey() {
        return sourceKey;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }
}
