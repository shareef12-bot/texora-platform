package com.texora.secops.siem.domain.enums;

/** Wire protocol an ingestion source uses. See MultiProtocolIngestionAdapter. */
public enum Protocol {
    SYSLOG,
    HTTP,
    KAFKA
}
