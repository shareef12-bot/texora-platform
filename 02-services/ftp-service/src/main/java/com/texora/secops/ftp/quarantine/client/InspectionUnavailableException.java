package com.texora.secops.ftp.quarantine.client;

/** The external inspection service could not deliver a verdict. Callers MUST treat this as "quarantine". */
public class InspectionUnavailableException extends RuntimeException {

    public InspectionUnavailableException(String message) {
        super(message);
    }

    public InspectionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
