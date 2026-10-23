package com.texora.secops.vpn.exception;

/**
 * Service-specific failure carrying the HTTP status and a stable machine-readable code.
 * Mapped to the standard error body by the service's own GlobalExceptionHandler.
 * (The shared PlatformException hierarchy is used for authentication failures; its constructors
 * are not assumed here.)
 */
public class VpnException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;

    public VpnException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static VpnException unauthenticated(String code, String message) {
        return new VpnException(401, code, message);
    }

    public static VpnException forbidden(String code, String message) {
        return new VpnException(403, code, message);
    }

    public static VpnException notFound(String code, String message) {
        return new VpnException(404, code, message);
    }

    public static VpnException conflict(String code, String message) {
        return new VpnException(409, code, message);
    }

    public static VpnException unprocessable(String code, String message) {
        return new VpnException(422, code, message);
    }

    public static VpnException gatewayTimeout(String code, String message) {
        return new VpnException(504, code, message);
    }
}
