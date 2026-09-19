package com.texora.secops.sso.authn.model;

/** Outcome of challenging the user's enrolled MFA factor. */
public final class MfaChallengeResult {

    public enum Status {
        PASSED,
        FAILED,
        NOT_ENROLLED
    }

    private final Status status;

    private MfaChallengeResult(Status status) {
        this.status = status;
    }

    public static MfaChallengeResult passed() {
        return new MfaChallengeResult(Status.PASSED);
    }

    public static MfaChallengeResult failed() {
        return new MfaChallengeResult(Status.FAILED);
    }

    public static MfaChallengeResult notEnrolled() {
        return new MfaChallengeResult(Status.NOT_ENROLLED);
    }

    public Status getStatus() {
        return status;
    }

    public boolean isPassed() {
        return status == Status.PASSED;
    }
}
