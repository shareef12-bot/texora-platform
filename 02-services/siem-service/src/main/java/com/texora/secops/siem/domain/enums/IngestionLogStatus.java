package com.texora.secops.siem.domain.enums;

/**
 * Requirement 1 (NEVER SILENTLY DROP AN EVENT): every ingested event moves through these
 * states. BUFFERED and RETRIED are transient; PROCESSED and DLQ are terminal. An event that
 * cannot be processed after exhausting retries becomes DLQ - it is never simply discarded.
 */
public enum IngestionLogStatus {
    BUFFERED,
    RETRIED,
    DLQ,
    PROCESSED
}
