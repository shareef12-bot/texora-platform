package com.texora.secops.siem.normalization.service;

/** Thrown when a raw event cannot be mapped to the common schema - malformed payload, unknown
 *  event shape, etc. Caught by IngestionOrchestrationService and treated as a processing
 *  failure eligible for retry, exactly like any other transient fault (Requirement 1). */
public class NormalizationException extends Exception {

    public NormalizationException(String message) {
        super(message);
    }

    public NormalizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
