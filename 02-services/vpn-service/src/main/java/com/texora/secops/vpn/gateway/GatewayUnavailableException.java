package com.texora.secops.vpn.gateway;

/** The dataplane could not be reached, timed out, or returned an outcome that cannot be trusted. */
public class GatewayUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GatewayUnavailableException(String message) {
        super(message);
    }

    public GatewayUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
