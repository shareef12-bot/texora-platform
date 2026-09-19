package com.texora.secops.iam.mtls;

/**
 * Thrown when the mTLS SSLContext cannot be initialised.
 * This is a fatal startup error — service should not start without valid mTLS credentials.
 */
public class MtlsInitialisationException extends RuntimeException {

    public MtlsInitialisationException(String message, Throwable cause) {
        super(message, cause);
    }
}
