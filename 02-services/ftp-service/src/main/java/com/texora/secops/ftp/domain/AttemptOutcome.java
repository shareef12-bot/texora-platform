package com.texora.secops.ftp.domain;

public enum AttemptOutcome {
    IN_PROGRESS,
    SUCCESS,
    FAILED_RETRYABLE,
    FAILED_FATAL,
    CANCELLED
}
