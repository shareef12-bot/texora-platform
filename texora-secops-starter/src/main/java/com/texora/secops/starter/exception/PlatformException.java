package com.texora.secops.starter.exception;

/**
 * Root of the Texora SecOps platform exception hierarchy.
 *
 * <p>All service-specific exceptions should extend one of the typed subclasses
 * below. The {@link GlobalExceptionHandler} maps each to the correct HTTP status.
 */
public abstract class PlatformException extends RuntimeException {

    private final String code;

    protected PlatformException(String code, String message) {
        super(message);
        this.code = code;
    }

    protected PlatformException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    // -------------------------------------------------------------------------
    // HTTP 400 — malformed request
    // -------------------------------------------------------------------------

    public static class BadRequestException extends PlatformException {
        public BadRequestException(String code, String message) {
            super(code, message);
        }
    }

    // -------------------------------------------------------------------------
    // HTTP 401 — unauthenticated
    // -------------------------------------------------------------------------

    public static class UnauthenticatedException extends PlatformException {
        public UnauthenticatedException(String code, String message) {
            super(code, message);
        }
    }

    // -------------------------------------------------------------------------
    // HTTP 403 — forbidden (RBAC or cross-tenant)
    // -------------------------------------------------------------------------

    public static class ForbiddenException extends PlatformException {
        public ForbiddenException(String code, String message) {
            super(code, message);
        }

        /** Convenience factory for RBAC denials. */
        public static ForbiddenException rbacDenied(String message) {
            return new ForbiddenException("RBAC_DENIED", message);
        }

        /** Convenience factory for cross-tenant access attempts. */
        public static ForbiddenException crossTenant() {
            return new ForbiddenException("CROSS_TENANT_ACCESS",
                "Access to resource belonging to another tenant is forbidden");
        }
    }

    // -------------------------------------------------------------------------
    // HTTP 404 — not found
    // -------------------------------------------------------------------------

    public static class NotFoundException extends PlatformException {
        public NotFoundException(String code, String message) {
            super(code, message);
        }

        public static NotFoundException forResource(String resourceType, Object id) {
            return new NotFoundException("NOT_FOUND",
                resourceType + " not found: " + id);
        }
    }

    // -------------------------------------------------------------------------
    // HTTP 409 — conflict
    // -------------------------------------------------------------------------

    public static class ConflictException extends PlatformException {
        public ConflictException(String code, String message) {
            super(code, message);
        }
    }

    // -------------------------------------------------------------------------
    // HTTP 422 — validation / business-rule failure
    // -------------------------------------------------------------------------

    public static class UnprocessableException extends PlatformException {
        public UnprocessableException(String code, String message) {
            super(code, message);
        }
    }

    // -------------------------------------------------------------------------
    // HTTP 504 — upstream timeout
    // -------------------------------------------------------------------------

    public static class UpstreamTimeoutException extends PlatformException {
        public UpstreamTimeoutException(String code, String message) {
            super(code, message);
        }

        public UpstreamTimeoutException(String code, String message, Throwable cause) {
            super(code, message, cause);
        }
    }
}
