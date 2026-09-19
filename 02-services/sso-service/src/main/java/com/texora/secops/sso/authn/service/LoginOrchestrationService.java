package com.texora.secops.sso.authn.service;

import com.texora.secops.sso.audit.annotation.Audited;
import com.texora.secops.sso.authn.client.DcIdentityClient;
import com.texora.secops.sso.authn.client.LdapBindClient;
import com.texora.secops.sso.authn.client.MfaVerificationClient;
import com.texora.secops.sso.authn.model.BindResult;
import com.texora.secops.sso.authn.model.LoginOutcome;
import com.texora.secops.sso.authn.model.LoginResult;
import com.texora.secops.sso.authn.model.MfaChallengeResult;
import com.texora.secops.sso.consent.service.ConsentPolicyService;
import com.texora.secops.sso.domain.ApplicationStatus;
import com.texora.secops.sso.domain.MfaFactorStatus;
import com.texora.secops.sso.domain.SsoApplication;
import com.texora.secops.sso.domain.SsoMfaEnrollment;
import com.texora.secops.sso.domain.SsoSession;
import com.texora.secops.sso.repository.SsoApplicationRepository;
import com.texora.secops.sso.repository.SsoMfaEnrollmentRepository;
import com.texora.secops.sso.session.service.SessionService;
import com.texora.secops.sso.session.service.TokenService;
import com.texora.secops.sso.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the exact LOGIN FLOW from the LLD/prompt, step by step:
 *   0. Resolve the target application and, from it, the tenant. This method
 *      is invoked from the browser-facing /login flow BEFORE any SSO token
 *      exists (there is no bearer token yet — this call is what produces
 *      the first one), so unlike every other service method, tenant cannot
 *      come from TenantResolvingFilter / an inbound token claim. It is
 *      derived from the application being logged into instead, and bound
 *      to TenantContext for the rest of this call so every downstream
 *      repository/service call stays correctly tenant-scoped.
 *   1. LDAP bind          -&gt; BIND_FAILED on any failure
 *   2. MFA challenge       -&gt; MFA_FAILED / MFA_NOT_ENROLLED halts the flow
 *   3. Consent/policy check (ConsentPolicyService, fail-closed) -&gt; POLICY_DENIED
 *   4. Session + token issuance on success
 *   5. Audit event emitted for EVERY outcome, success or failure, via the
 *      @Audited annotation — this method always runs to completion and
 *      returns a LoginResult rather than throwing on a business denial, so
 *      the audit aspect can record the true outcome instead of swallowing
 *      it as a generic exception.
 */
@Service
public class LoginOrchestrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoginOrchestrationService.class);

    private final SsoApplicationRepository applicationRepository;
    private final LdapBindClient ldapBindClient;
    private final SsoMfaEnrollmentRepository mfaEnrollmentRepository;
    private final ConsentPolicyService consentPolicyService;
    private final SessionService sessionService;
    private final TokenService tokenService;
    private final DcIdentityClient dcIdentityClient;
    private final MfaVerificationClient mfaVerificationClient;

    public LoginOrchestrationService(SsoApplicationRepository applicationRepository,
                                      LdapBindClient ldapBindClient,
                                      SsoMfaEnrollmentRepository mfaEnrollmentRepository,
                                      ConsentPolicyService consentPolicyService,
                                      SessionService sessionService,
                                      TokenService tokenService,
                                      DcIdentityClient dcIdentityClient,
                                      MfaVerificationClient mfaVerificationClient) {
        this.applicationRepository = applicationRepository;
        this.ldapBindClient = ldapBindClient;
        this.mfaEnrollmentRepository = mfaEnrollmentRepository;
        this.consentPolicyService = consentPolicyService;
        this.sessionService = sessionService;
        this.tokenService = tokenService;
        this.dcIdentityClient = dcIdentityClient;
        this.mfaVerificationClient = mfaVerificationClient;
    }

    @Audited(action = "login.attempt", targetType = "sso_session")
    public LoginResult login(String username, String credential, UUID applicationId, String mfaCode) {
        // Step 0: resolve application -> tenant. An unknown or non-ACTIVE
        // application is denied before any credential is even checked.
        Optional<SsoApplication> application = applicationRepository.findById(applicationId);
        if (application.isEmpty() || application.get().getStatus() != ApplicationStatus.ACTIVE) {
            LOGGER.info("Login denied: unknown or inactive applicationId={}", applicationId);
            return LoginResult.denied(LoginOutcome.APPLICATION_UNKNOWN);
        }
        UUID tenantId = application.get().getTenantId();
        TenantContext.set(tenantId);

        // Step 1: LDAP bind
        BindResult bind = ldapBindClient.verifyBind(username, credential);
        if (!bind.isSuccess()) {
            LOGGER.info("Login denied: LDAP bind failed for username={} reason={}", username,
                    bind.getFailureReason());
            return LoginResult.denied(LoginOutcome.BIND_FAILED);
        }
        UUID dcUserId = bind.getDcUserId();

        // Step 2: MFA — halt if required but not enrolled, or if the challenge fails.
        MfaChallengeResult mfaResult = challengeMfa(tenantId, dcUserId, mfaCode);
        if (mfaResult.getStatus() == MfaChallengeResult.Status.NOT_ENROLLED) {
            LOGGER.info("Login denied: MFA required but not enrolled for dcUserId={}", dcUserId);
            return LoginResult.denied(LoginOutcome.MFA_NOT_ENROLLED);
        }
        if (!mfaResult.isPassed()) {
            LOGGER.info("Login denied: MFA challenge failed for dcUserId={}", dcUserId);
            return LoginResult.denied(LoginOutcome.MFA_FAILED);
        }

        // Step 3: consent/policy — FAIL CLOSED. ConsentPolicyService itself never
        // defaults to allow.
        boolean allowed = consentPolicyService.isAccessAllowed(tenantId, applicationId, dcUserId);
        if (!allowed) {
            LOGGER.info("Login denied: consent/policy check failed for dcUserId={} applicationId={}",
                    dcUserId, applicationId);
            return LoginResult.denied(LoginOutcome.POLICY_DENIED);
        }

        // Step 4: resolve DC-derived claims (best-effort; absence doesn't block login),
        // then issue session + tokens.
        dcIdentityClient.fetchProfile(dcUserId);

        SsoSession session = sessionService.createActiveSession(dcUserId, applicationId);
        List<?> tokens = tokenService.issueTokenSet(session.getId());
        LOGGER.info("Login succeeded for dcUserId={} applicationId={} sessionId={} tokensIssued={}",
                dcUserId, applicationId, session.getId(), tokens.size());

        return LoginResult.success(session.getId(), dcUserId);
    }

    private MfaChallengeResult challengeMfa(UUID tenantId, UUID dcUserId, String mfaCode) {
        List<SsoMfaEnrollment> enrollments = mfaEnrollmentRepository
                .findAllByDcUserIdAndTenantIdAndStatus(dcUserId, tenantId, MfaFactorStatus.ACTIVE);

        if (enrollments.isEmpty()) {
            return MfaChallengeResult.notEnrolled();
        }
        if (mfaCode == null || mfaCode.isBlank()) {
            return MfaChallengeResult.failed();
        }
        // Actual factor verification (TOTP/push/etc.) is delegated to the shared IAM
        // client library's MFA verifier — this orchestration layer only sequences
        // the flow and enforces halt-on-failure, per the CRITICAL CONSTRAINT against
        // implementing any crypto/protocol logic locally.
        boolean verified = mfaVerificationClient.verify(dcUserId, enrollments.get(0).getFactorType(), mfaCode);
        return verified ? MfaChallengeResult.passed() : MfaChallengeResult.failed();
    }
}
