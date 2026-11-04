package com.texora.secops.vpn.session;

import com.texora.secops.vpn.VpnConstants;
import com.texora.secops.vpn.admin.VpnConfigService;
import com.texora.secops.vpn.config.VpnProperties;
import com.texora.secops.vpn.domain.AccessPolicy;
import com.texora.secops.vpn.domain.DeviceEnrollment;
import com.texora.secops.vpn.domain.GatewayBinding;
import com.texora.secops.vpn.domain.GatewayEndpoint;
import com.texora.secops.vpn.domain.VpnSession;
import com.texora.secops.vpn.domain.VpnSubject;
import com.texora.secops.vpn.dto.EstablishSessionRequest;
import com.texora.secops.vpn.dto.PageResponse;
import com.texora.secops.vpn.dto.RevokeSessionResponse;
import com.texora.secops.vpn.dto.SessionResponse;
import com.texora.secops.vpn.dto.SessionSummary;
import com.texora.secops.vpn.exception.VpnException;
import com.texora.secops.vpn.gateway.GatewayEstablishRequest;
import com.texora.secops.vpn.gateway.GatewayEstablishResult;
import com.texora.secops.vpn.gateway.GatewayIntegrationAdapter;
import com.texora.secops.vpn.gateway.GatewayRevokeRequest;
import com.texora.secops.vpn.gateway.GatewayRevokeResult;
import com.texora.secops.vpn.gateway.GatewayUnavailableException;
import com.texora.secops.vpn.metrics.VpnMetrics;
import com.texora.secops.vpn.policy.AccessPolicyConditions;
import com.texora.secops.vpn.policy.AccessPolicyEvaluator;
import com.texora.secops.vpn.policy.PolicyConditionsCodec;
import com.texora.secops.vpn.policy.PolicyConditionsException;
import com.texora.secops.vpn.policy.PolicyEvaluation;
import com.texora.secops.vpn.policy.PolicyVerdict;
import com.texora.secops.vpn.repository.AccessPolicyRepository;
import com.texora.secops.vpn.repository.GatewayBindingRepository;
import com.texora.secops.vpn.repository.GatewayEndpointRepository;
import com.texora.secops.vpn.repository.PolicyAssignmentRepository;
import com.texora.secops.vpn.repository.VpnSessionRepository;
import com.texora.secops.vpn.security.Caller;
import com.texora.secops.vpn.security.PolicyDecisionClient;
import com.texora.secops.vpn.security.SecDecision;
import com.texora.secops.vpn.security.SecDecisionRequest;
import com.texora.secops.vpn.subject.DeviceEnrollmentService;
import com.texora.secops.vpn.subject.IdentityState;
import com.texora.secops.vpn.subject.MfaFactorVerifier;
import com.texora.secops.vpn.subject.VpnSubjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The fail-closed heart of the VPN control plane.
 *
 * <ul>
 *   <li><b>Establishment</b> (Requirement 1): DENY on ambiguous policy, DENY policy, device/MFA failure, or any SEC
 *       failure/timeout. No default allow; any unexpected exception is a DENY.</li>
 *   <li><b>Revocation</b> (Requirement 2): control-plane state is marked REVOKED FIRST and durably (with its audit
 *       event, same transaction), THEN the dataplane tunnel is torn down. If the dataplane is unreachable the session is
 *       still REVOKED here, teardown stays PENDING and is retried until the dataplane confirms.</li>
 *   <li><b>Re-validation</b>: any doubt about an ACTIVE session revokes it. See docs/UNCERTAINTY-TRIGGERS.md.</li>
 * </ul>
 * External calls (SEC, DC, dataplane) are never made inside a database transaction.
 */
@Service
public class SessionControlService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionControlService.class);

    private final VpnSessionRepository sessions;
    private final GatewayBindingRepository bindings;
    private final AccessPolicyRepository policies;
    private final PolicyAssignmentRepository assignments;
    private final GatewayEndpointRepository endpoints;
    private final VpnSubjectService subjectService;
    private final DeviceEnrollmentService deviceService;
    private final MfaFactorVerifier mfaVerifier;
    private final PolicyDecisionClient secClient;
    private final GatewayIntegrationAdapter adapter;
    private final AccessPolicyEvaluator evaluator;
    private final PolicyConditionsCodec codec;
    private final SessionAuditService sessionAudit;
    private final VpnConfigService config;
    private final VpnProperties properties;
    private final VpnMetrics metrics;
    private final TransactionTemplate tx;
    private final Clock clock;

    public SessionControlService(VpnSessionRepository sessions, GatewayBindingRepository bindings,
                                 AccessPolicyRepository policies, PolicyAssignmentRepository assignments,
                                 GatewayEndpointRepository endpoints, VpnSubjectService subjectService,
                                 DeviceEnrollmentService deviceService, MfaFactorVerifier mfaVerifier,
                                 PolicyDecisionClient secClient, GatewayIntegrationAdapter adapter,
                                 AccessPolicyEvaluator evaluator, PolicyConditionsCodec codec,
                                 SessionAuditService sessionAudit, VpnConfigService config, VpnProperties properties,
                                 VpnMetrics metrics, TransactionTemplate tx, Clock clock) {
        this.sessions = sessions;
        this.bindings = bindings;
        this.policies = policies;
        this.assignments = assignments;
        this.endpoints = endpoints;
        this.subjectService = subjectService;
        this.deviceService = deviceService;
        this.mfaVerifier = mfaVerifier;
        this.secClient = secClient;
        this.adapter = adapter;
        this.evaluator = evaluator;
        this.codec = codec;
        this.sessionAudit = sessionAudit;
        this.config = config;
        this.properties = properties;
        this.metrics = metrics;
        this.tx = tx;
        this.clock = clock;
    }

    // =====================================================================
    // ESTABLISHMENT  (Requirement 1)
    // =====================================================================

    public SessionResponse establish(Caller caller, EstablishSessionRequest request) {
        UUID tenantId = caller.getTenantId();
        Instant now = clock.instant();

        Admission admission;
        try {
            admission = admit(caller, request, now);
        } catch (RuntimeException e) {
            LOGGER.error("Session establishment evaluation failed; DENYING (fail closed)", e);
            admission = Admission.deny("INTERNAL_ERROR");
        }
        if (!admission.allow) {
            denyEstablishment(caller, request, admission.reason);
        }

        final Admission granted = admission;
        VpnSession session = tx.execute(st -> {
            VpnSession s = new VpnSession();
            s.setId(UUID.randomUUID());
            s.setTenantId(tenantId);
            s.setVpnSubjectId(granted.subject.getId());
            s.setAccessPolicyId(granted.policy.getId());
            s.setDeviceEnrollmentId(granted.device == null ? null : granted.device.getId());
            s.setStatus(VpnConstants.SESSION_ACTIVE);
            s.setEstablishedAt(now);
            s.setExpiresAt(granted.expiresAt);
            s.setLastValidatedAt(now);
            // First re-validation after the grace window. If the binding is still missing then, it is revoked.
            s.setNextValidationAt(now.plusSeconds(properties.getSession().getEstablishGraceSeconds()));
            sessions.save(s);
            return s;
        });

        GatewayEstablishResult result;
        try {
            result = adapter.establish(new GatewayEstablishRequest(session.getId(), tenantId, granted.subject.getId(),
                    granted.endpoint.getEndpointUrl(), granted.expiresAt));
            if (result == null || result.getDataplaneSessionRef() == null || result.getDataplaneSessionRef().isBlank()) {
                throw new GatewayUnavailableException("dataplane returned no session reference");
            }
        } catch (RuntimeException e) {
            // Includes GatewayUnavailableException AND anything unexpected. The tunnel MAY exist; never assume it does not.
            LOGGER.error("Dataplane establish failed/ambiguous for session {}: {}", session.getId(), e.getClass().getSimpleName());
            metrics.establishment("dataplane_failure");
            safeRevoke(command(tenantId, session.getId(), VpnConstants.TRIGGER_ESTABLISH_FAILURE,
                    "dataplane establishment failed or was ambiguous", VpnConstants.SYSTEM_ACTOR,
                    VpnConstants.SESSION_REVOKED, true, null));
            throw VpnException.gatewayTimeout("DATAPLANE_UNAVAILABLE", "VPN dataplane could not establish the session");
        }

        final String ref = result.getDataplaneSessionRef();
        try {
            tx.executeWithoutResult(st -> {
                GatewayBinding b = new GatewayBinding();
                b.setId(UUID.randomUUID());
                b.setTenantId(tenantId);
                b.setSessionId(session.getId());
                b.setDataplaneSessionRef(ref); // opaque reference ONLY
                b.setGatewayEndpoint(granted.endpoint.getEndpointUrl());
                b.setBoundAt(clock.instant());
                b.setTeardownState(VpnConstants.TEARDOWN_NONE);
                b.setTeardownAttempts(0);
                bindings.save(b);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("policyId", granted.policy.getId().toString());
                payload.put("subjectId", granted.subject.getId().toString());
                payload.put("gatewayEndpoint", granted.endpoint.getEndpointUrl());
                payload.put("expiresAt", granted.expiresAt.toString());
                sessionAudit.record(tenantId, session.getId(), caller.getUserId(), "ESTABLISHED", "session established",
                        VpnConstants.ACTION_SESSION_ESTABLISHED, VpnConstants.OUTCOME_SUCCESS, null, payload);
            });
        } catch (RuntimeException e) {
            LOGGER.error("Could not record binding for session {}; tearing the tunnel down", session.getId(), e);
            metrics.establishment("bookkeeping_failure");
            safeRevoke(command(tenantId, session.getId(), VpnConstants.TRIGGER_ESTABLISH_FAILURE,
                    "binding could not be recorded", VpnConstants.SYSTEM_ACTOR, VpnConstants.SESSION_REVOKED, true, ref));
            throw new VpnException(500, "INTERNAL_ERROR", "Internal error");
        }
        metrics.establishment("allowed");
        SessionResponse response = new SessionResponse();
        response.setSessionId(session.getId());
        response.setStatus(VpnConstants.SESSION_ACTIVE);
        response.setEstablishedAt(session.getEstablishedAt());
        response.setExpiresAt(session.getExpiresAt());
        response.setGatewayEndpoint(granted.endpoint.getEndpointUrl());
        return response;
    }

    private Admission admit(Caller caller, EstablishSessionRequest request, Instant now) {
        UUID tenantId = caller.getTenantId();
        Optional<VpnSubject> subjectOpt = subjectService.findByDcUser(tenantId, caller.getUserId());
        if (subjectOpt.isEmpty()) {
            return Admission.deny("SUBJECT_NOT_FOUND");
        }
        VpnSubject subject = subjectOpt.get();
        IdentityState identity = subjectService.ensureFresh(tenantId, subject.getId());
        if (identity == IdentityState.INACTIVE) {
            return Admission.deny("IDENTITY_INACTIVE");
        }
        if (identity == IdentityState.UNCERTAIN) {
            return Admission.deny("IDENTITY_UNCERTAIN");
        }
        AccessPolicy policy = policies.findByIdAndTenantId(request.getAccessPolicyId(), tenantId).orElse(null);
        if (policy == null) {
            return Admission.deny("POLICY_NOT_FOUND");
        }
        if (!VpnConstants.POLICY_APPROVED.equals(policy.getStatus())) {
            return Admission.deny("POLICY_NOT_APPROVED");
        }
        if (!assignments.existsByTenantIdAndAccessPolicyIdAndVpnSubjectId(tenantId, policy.getId(), subject.getId())) {
            return Admission.deny("POLICY_NOT_ASSIGNED");
        }
        AccessPolicyConditions conditions;
        try {
            conditions = codec.parseStored(policy.getConditions());
        } catch (PolicyConditionsException e) {
            return Admission.deny("POLICY_AMBIGUOUS");
        }
        DeviceEnrollment device = deviceService.findActive(tenantId, subject.getId(), request.getDeviceFingerprint()).orElse(null);
        boolean mfaRequired = !Boolean.FALSE.equals(conditions.getMfaRequired());
        boolean mfaSatisfied = !mfaRequired
                || (device != null && mfaVerifier.verify(tenantId, subject.getId(), device.getMfaFactorRef(), request.getMfaCode()));

        PolicyEvaluation evaluation = evaluator.evaluateRequested(conditions, now, device != null, mfaSatisfied);
        if (evaluation.getVerdict() != PolicyVerdict.ALLOW) {
            return Admission.deny(evaluation.getVerdict() == PolicyVerdict.AMBIGUOUS ? "POLICY_AMBIGUOUS" : evaluation.getReasonCode());
        }
        String override = denyOverride(tenantId, subject.getId(), policy.getId(), now);
        if (override != null) {
            return Admission.deny(override);
        }
        SecDecision sec = secClient.decide(new SecDecisionRequest(tenantId, subject.getDcUserId(),
                "vpn.session.establish", "vpn:access-policy:" + policy.getId()), 1);
        if (sec == SecDecision.UNAVAILABLE) {
            metrics.secUnavailable();
            return Admission.deny("SEC_UNAVAILABLE");
        }
        if (sec != SecDecision.ALLOW) {
            return Admission.deny("SEC_DENY");
        }
        List<GatewayEndpoint> active = endpoints.findByTenantIdAndStatusOrderByCreatedAt(tenantId, VpnConstants.ENDPOINT_ACTIVE);
        if (active.isEmpty()) {
            return Admission.deny("NO_GATEWAY_ENDPOINT");
        }
        int minutes = config.sessionMaxMinutes(tenantId);
        if (conditions.getMaxSessionMinutes() != null) {
            minutes = Math.min(minutes, conditions.getMaxSessionMinutes());
        }
        return Admission.allow(subject, policy, device, active.get(0), now.plusSeconds(minutes * 60L));
    }

    /** Any other assigned APPROVED DENY-policy that applies now (or cannot be evaluated) overrides the ALLOW. */
    private String denyOverride(UUID tenantId, UUID subjectId, UUID requestedPolicyId, Instant now) {
        for (AccessPolicy other : policies.findAssignedByStatus(tenantId, subjectId, VpnConstants.POLICY_APPROVED)) {
            if (other.getId().equals(requestedPolicyId)) {
                continue;
            }
            try {
                PolicyEvaluation e = evaluator.evaluateDenyOverride(codec.parseStored(other.getConditions()), now);
                if (e.getVerdict() == PolicyVerdict.DENY) {
                    return "DENY_POLICY_APPLIES";
                }
                if (e.getVerdict() == PolicyVerdict.AMBIGUOUS) {
                    return "POLICY_AMBIGUOUS";
                }
            } catch (PolicyConditionsException ex) {
                return "POLICY_AMBIGUOUS";
            }
        }
        return null;
    }

    private void denyEstablishment(Caller caller, EstablishSessionRequest request, String reason) {
        metrics.establishment("denied");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reason", reason);
        payload.put("accessPolicyId", String.valueOf(request.getAccessPolicyId()));
        try {
            sessionAudit.record(caller.getTenantId(), null, caller.getUserId(), "DENIED", reason,
                    VpnConstants.ACTION_SESSION_DENIED, VpnConstants.OUTCOME_DENIED, String.valueOf(request.getAccessPolicyId()), payload);
        } catch (RuntimeException e) {
            LOGGER.error("Could not audit establishment denial (still denying)", e);
        }
        throw VpnException.forbidden("SESSION_DENIED", "Access denied"); // reason code deliberately only in the audit trail
    }

    // =====================================================================
    // REVOCATION  (Requirement 2)
    // =====================================================================

    public RevokeSessionResponse revokeByAdmin(Caller caller, UUID sessionId, String reason) {
        UUID tenantId = caller.getTenantId();
        if (sessions.findByIdAndTenantId(sessionId, tenantId).isEmpty()) {
            if (sessions.existsByIdAndTenantIdNot(sessionId, tenantId)) {
                throw VpnException.forbidden("TENANT_FORBIDDEN", "Resource belongs to another tenant");
            }
            throw VpnException.notFound("SESSION_NOT_FOUND", "Session not found");
        }
        RevocationResult result = revokeCore(command(tenantId, sessionId, VpnConstants.TRIGGER_ADMIN, reason,
                caller.getUserId(), VpnConstants.SESSION_REVOKED, true, null));
        if (!result.isFound()) {
            throw VpnException.notFound("SESSION_NOT_FOUND", "Session not found");
        }
        RevokeSessionResponse r = new RevokeSessionResponse();
        r.setSessionId(sessionId);
        r.setStatus(result.getStatus());
        r.setRevokedAt(result.getRevokedAt());
        r.setDataplaneRevocationConfirmed(result.isDataplaneConfirmed());
        return r;
    }

    /** The dataplane says a tunnel ended. It can only END a session here, never create or extend one. */
    public boolean onDataplaneTunnelTerminated(String dataplaneSessionRef) {
        GatewayBinding binding = bindings.findByDataplaneSessionRef(dataplaneSessionRef).orElse(null);
        if (binding == null) {
            return false;
        }
        RevocationResult r = safeRevoke(command(binding.getTenantId(), binding.getSessionId(),
                VpnConstants.TRIGGER_DATAPLANE_REPORTED, "dataplane reported tunnel terminated",
                VpnConstants.SYSTEM_ACTOR, VpnConstants.SESSION_EXPIRED, false, null));
        return r.isFound();
    }

    /**
     * Two phases. PHASE 1 durably marks the session terminal (plus audit) in one transaction - this is what makes the
     * control plane fail closed even if the dataplane is down. PHASE 2 asks the dataplane to tear the tunnel down.
     */
    RevocationResult revokeCore(RevocationCommand c) {
        Phase1 p = tx.execute(st -> {
            VpnSession s = sessions.findForUpdate(c.getSessionId(), c.getTenantId()).orElse(null);
            Phase1 out = new Phase1();
            if (s == null) {
                return out;
            }
            out.found = true;
            GatewayBinding b = bindings.findBySessionIdAndTenantId(c.getSessionId(), c.getTenantId()).orElse(null);
            if (!VpnConstants.SESSION_ACTIVE.equals(s.getStatus())) { // idempotent: already terminal
                out.status = s.getStatus();
                out.revokedAt = s.getRevokedAt();
                if (b != null) {
                    out.ref = b.getDataplaneSessionRef();
                    out.teardownNeeded = c.isCallDataplane() && VpnConstants.TEARDOWN_PENDING.equals(b.getTeardownState());
                    out.confirmed = !VpnConstants.TEARDOWN_PENDING.equals(b.getTeardownState());
                } else {
                    out.confirmed = true;
                }
                return out;
            }
            Instant now = clock.instant();
            s.setStatus(c.getTerminalStatus());
            s.setRevokedAt(now);
            s.setRevocationTrigger(c.getTrigger());
            String reason = c.getReason();
            s.setRevocationReason(reason == null ? null : (reason.length() > 500 ? reason.substring(0, 500) : reason));
            sessions.save(s);

            if (b == null && c.isDataplaneMayExist() && c.isCallDataplane()) {
                // Outcome of establishment was unknown or the binding was never written: a tunnel may still exist.
                b = new GatewayBinding();
                b.setId(UUID.randomUUID());
                b.setTenantId(c.getTenantId());
                b.setSessionId(c.getSessionId());
                b.setDataplaneSessionRef(c.getKnownDataplaneRef());
                b.setGatewayEndpoint("unknown");
                b.setBoundAt(now);
                b.setTeardownAttempts(0);
            }
            if (b != null) {
                b.setTeardownState(c.isCallDataplane() ? VpnConstants.TEARDOWN_PENDING : VpnConstants.TEARDOWN_CONFIRMED);
                bindings.save(b);
                out.ref = b.getDataplaneSessionRef();
                out.teardownNeeded = c.isCallDataplane();
            }
            out.confirmed = !out.teardownNeeded;
            out.status = c.getTerminalStatus();
            out.revokedAt = now;
            out.newlyRevoked = true;

            boolean uncertainty = VpnConstants.isUncertaintyTrigger(c.getTrigger());
            String action = uncertainty ? VpnConstants.ACTION_SESSION_REVOKED_UNCERTAINTY
                    : (VpnConstants.TRIGGER_ADMIN.equals(c.getTrigger()) ? VpnConstants.ACTION_SESSION_REVOKED_ADMIN
                    : VpnConstants.ACTION_SESSION_ENDED);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("trigger", c.getTrigger());
            payload.put("uncertaintyTriggered", uncertainty);
            payload.put("reason", c.getReason());
            payload.put("subjectId", s.getVpnSubjectId().toString());
            payload.put("policyId", s.getAccessPolicyId().toString());
            sessionAudit.record(c.getTenantId(), c.getSessionId(), c.getActorId(), c.getTerminalStatus(),
                    c.getTrigger() + ": " + (c.getReason() == null ? "" : c.getReason()), action,
                    VpnConstants.OUTCOME_SUCCESS, null, payload);
            return out;
        });

        if (p == null || !p.found) {
            return new RevocationResult(false, null, null, false);
        }
        if (p.newlyRevoked) {
            metrics.revocation(c.getTrigger(), VpnConstants.isUncertaintyTrigger(c.getTrigger()));
        }
        boolean confirmed = p.confirmed;
        if (p.teardownNeeded) {
            confirmed = attemptTeardown(c.getTenantId(), c.getSessionId(), p.ref, c.getTrigger(), true);
        }
        return new RevocationResult(true, p.status, p.revokedAt, confirmed);
    }

    /**
     * Never throws. If even the bookkeeping fails (e.g. database down) it still tries to kill the tunnel directly -
     * an unrecorded teardown is better than a live tunnel. The scheduler retries everything on the next tick.
     */
    RevocationResult safeRevoke(RevocationCommand c) {
        try {
            return revokeCore(c);
        } catch (RuntimeException e) {
            LOGGER.error("Revocation bookkeeping failed for session {} (trigger {}); attempting direct teardown",
                    c.getSessionId(), c.getTrigger(), e);
            metrics.teardown("bookkeeping_failed");
            if (c.isCallDataplane()) {
                try {
                    adapter.revoke(new GatewayRevokeRequest(c.getSessionId(), c.getKnownDataplaneRef(), c.getTrigger()));
                } catch (RuntimeException ex) {
                    LOGGER.error("Direct teardown also failed for session {}: {}", c.getSessionId(), ex.getClass().getSimpleName());
                }
            }
            return new RevocationResult(false, null, null, false);
        }
    }

    /** Asks the adapter to tear down the tunnel and records the outcome. Returns true only on dataplane confirmation. */
    boolean attemptTeardown(UUID tenantId, UUID sessionId, String ref, String trigger, boolean incrementAttempts) {
        boolean confirmed = false;
        try {
            GatewayRevokeResult r = adapter.revoke(new GatewayRevokeRequest(sessionId, ref, trigger));
            confirmed = r != null && r.isConfirmed();
        } catch (RuntimeException e) {
            // Class name only: vendor exception messages must never be able to carry credential material into logs.
            LOGGER.error("Dataplane teardown failed for session {} ({}); session stays REVOKED, teardown will be retried",
                    sessionId, e.getClass().getSimpleName());
        }
        final boolean ok = confirmed;
        try {
            tx.executeWithoutResult(st -> {
                GatewayBinding b = bindings.findBySessionIdAndTenantId(sessionId, tenantId).orElse(null);
                if (b == null) {
                    return;
                }
                if (ok) {
                    b.setTeardownState(VpnConstants.TEARDOWN_CONFIRMED);
                }
                if (incrementAttempts) {
                    b.setTeardownAttempts(b.getTeardownAttempts() + 1);
                    b.setLastTeardownAttemptAt(clock.instant());
                }
                bindings.save(b);
                int n = b.getTeardownAttempts();
                if (ok || n <= 1 || n % 10 == 0) { // avoid one audit row every retry tick during a long outage
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("trigger", trigger);
                    payload.put("attempts", n);
                    sessionAudit.record(tenantId, sessionId, VpnConstants.SYSTEM_ACTOR,
                            ok ? "DATAPLANE_TEARDOWN_CONFIRMED" : "DATAPLANE_TEARDOWN_FAILED", null,
                            VpnConstants.ACTION_TEARDOWN, ok ? VpnConstants.OUTCOME_SUCCESS : VpnConstants.OUTCOME_FAILURE, null, payload);
                }
            });
        } catch (RuntimeException e) {
            LOGGER.error("Could not record teardown outcome for session {}", sessionId, e);
        }
        metrics.teardown(ok ? "confirmed" : "failed");
        return ok;
    }

    /** Retries teardown for REVOKED/EXPIRED sessions whose dataplane tunnel is not yet confirmed gone. */
    public int retryPendingTeardowns() {
        Instant now = clock.instant();
        Instant cutoff = now.minusSeconds(properties.getSession().getTeardownRetrySeconds());
        int confirmed = 0;
        for (GatewayBinding b : bindings.findTeardownDueAcrossTenants(cutoff,
                PageRequest.of(0, properties.getSession().getBatchSize()))) {
            if (bindings.claimTeardown(b.getId(), b.getTenantId(), now, cutoff) != 1) {
                continue;
            }
            if (attemptTeardown(b.getTenantId(), b.getSessionId(), b.getDataplaneSessionRef(), "TEARDOWN_RETRY", false)) {
                confirmed++;
            }
        }
        return confirmed;
    }

    // =====================================================================
    // RE-VALIDATION OF ACTIVE SESSIONS  (Requirement 2)
    // =====================================================================

    /**
     * Re-checks one ACTIVE session. Confirmed -> stays ACTIVE. Ambiguous / DENY / SEC timeout / ANY exception -> revoke.
     * Called by the scheduler after it has atomically claimed the session.
     */
    public void revalidate(UUID tenantId, UUID sessionId) {
        VpnSession s = sessions.findByIdAndTenantId(sessionId, tenantId).orElse(null);
        if (s == null || !VpnConstants.SESSION_ACTIVE.equals(s.getStatus())) {
            return;
        }
        Instant now = clock.instant();
        String knownRef = bindings.findBySessionIdAndTenantId(sessionId, tenantId)
                .map(GatewayBinding::getDataplaneSessionRef).orElse(null);
        Verdict verdict;
        try {
            verdict = checkContinuedAuthorization(s, now);
        } catch (RuntimeException e) {
            LOGGER.error("Re-validation of session {} failed unexpectedly; REVOKING (fail closed)", sessionId, e);
            verdict = new Verdict(VpnConstants.TRIGGER_INTERNAL_ERROR, "unexpected error during re-validation",
                    VpnConstants.SESSION_REVOKED);
        }
        if (verdict == null) {
            sessions.markValidated(sessionId, tenantId, now,
                    now.plusSeconds(config.revalidationIntervalSeconds(tenantId)));
            metrics.revalidation("confirmed");
            return;
        }
        metrics.revalidation("revoked");
        safeRevoke(command(tenantId, sessionId, verdict.trigger, verdict.reason, VpnConstants.SYSTEM_ACTOR,
                verdict.terminalStatus, true, knownRef));
    }

    /** @return null when continued authorization is CONFIRMED; otherwise the reason to revoke. */
    private Verdict checkContinuedAuthorization(VpnSession s, Instant now) {
        UUID tenantId = s.getTenantId();
        if (!s.getExpiresAt().isAfter(now)) {
            return new Verdict(VpnConstants.TRIGGER_EXPIRY, "session lifetime ended", VpnConstants.SESSION_EXPIRED);
        }
        GatewayBinding binding = bindings.findBySessionIdAndTenantId(s.getId(), tenantId).orElse(null);
        if (binding == null || binding.getDataplaneSessionRef() == null) {
            return revoke(VpnConstants.TRIGGER_STATE_INCONSISTENT, "no dataplane binding recorded for an ACTIVE session");
        }
        IdentityState identity = subjectService.ensureFresh(tenantId, s.getVpnSubjectId());
        if (identity == IdentityState.INACTIVE) {
            return revoke(VpnConstants.TRIGGER_IDENTITY_INACTIVE, "identity is no longer active");
        }
        if (identity == IdentityState.UNCERTAIN) {
            return revoke(VpnConstants.TRIGGER_IDENTITY_UNCERTAIN, "identity status is stale and could not be refreshed");
        }
        AccessPolicy policy = policies.findByIdAndTenantId(s.getAccessPolicyId(), tenantId).orElse(null);
        if (policy == null || !VpnConstants.POLICY_APPROVED.equals(policy.getStatus())
                || !assignments.existsByTenantIdAndAccessPolicyIdAndVpnSubjectId(tenantId, s.getAccessPolicyId(), s.getVpnSubjectId())) {
            return revoke(VpnConstants.TRIGGER_POLICY_CHANGED, "policy was disabled, removed or unassigned");
        }
        AccessPolicyConditions conditions;
        try {
            conditions = codec.parseStored(policy.getConditions());
        } catch (PolicyConditionsException e) {
            return revoke(VpnConstants.TRIGGER_POLICY_AMBIGUOUS, "policy conditions cannot be evaluated");
        }
        boolean deviceOk = s.getDeviceEnrollmentId() != null
                && deviceService.findActiveById(tenantId, s.getDeviceEnrollmentId()).isPresent();
        // MFA is a point-of-establishment control; it is satisfied for an already-established session.
        PolicyEvaluation evaluation = evaluator.evaluateRequested(conditions, now, deviceOk, true);
        if (evaluation.getVerdict() == PolicyVerdict.AMBIGUOUS) {
            return revoke(VpnConstants.TRIGGER_POLICY_AMBIGUOUS, evaluation.getReasonCode());
        }
        if (evaluation.getVerdict() != PolicyVerdict.ALLOW) {
            return revoke(VpnConstants.TRIGGER_POLICY_DENY, evaluation.getReasonCode());
        }
        String override = denyOverride(tenantId, s.getVpnSubjectId(), s.getAccessPolicyId(), now);
        if (override != null) {
            return revoke("POLICY_AMBIGUOUS".equals(override) ? VpnConstants.TRIGGER_POLICY_AMBIGUOUS
                    : VpnConstants.TRIGGER_POLICY_DENY, override);
        }
        return secCheck(s, tenantId);
    }

    private Verdict secCheck(VpnSession s, UUID tenantId) {
        UUID dcUserId = subjectDcUser(tenantId, s.getVpnSubjectId());
        if (dcUserId == null) {
            return revoke(VpnConstants.TRIGGER_STATE_INCONSISTENT, "subject record is missing");
        }
        SecDecision sec = secClient.decide(new SecDecisionRequest(tenantId, dcUserId, "vpn.session.revalidate",
                "vpn:access-policy:" + s.getAccessPolicyId()), properties.getSec().getRevalidationAttempts());
        if (sec == SecDecision.UNAVAILABLE) {
            metrics.secUnavailable();
            return revoke(VpnConstants.TRIGGER_SEC_UNAVAILABLE, "SEC could not confirm authorization");
        }
        if (sec != SecDecision.ALLOW) {
            return revoke(VpnConstants.TRIGGER_POLICY_DENY, "SEC denied continued authorization");
        }
        return null;
    }

    private UUID subjectDcUser(UUID tenantId, UUID subjectId) {
        return subjectService.findDcUserId(tenantId, subjectId).orElse(null);
    }

    private static Verdict revoke(String trigger, String reason) {
        return new Verdict(trigger, reason, VpnConstants.SESSION_REVOKED);
    }

    // =====================================================================
    // QUERY
    // =====================================================================

    public PageResponse<SessionSummary> list(UUID tenantId, String status, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
        Page<VpnSession> result = (status == null || status.isBlank())
                ? sessions.findByTenantIdOrderByEstablishedAtDesc(tenantId, pr)
                : sessions.findByTenantIdAndStatusOrderByEstablishedAtDesc(tenantId, status, pr);
        List<SessionSummary> items = new ArrayList<>();
        for (VpnSession s : result.getContent()) {
            SessionSummary x = new SessionSummary();
            x.setSessionId(s.getId());
            x.setSubjectId(s.getVpnSubjectId());
            x.setAccessPolicyId(s.getAccessPolicyId());
            x.setStatus(s.getStatus());
            x.setEstablishedAt(s.getEstablishedAt());
            x.setExpiresAt(s.getExpiresAt());
            x.setRevokedAt(s.getRevokedAt());
            x.setRevocationTrigger(s.getRevocationTrigger());
            items.add(x);
        }
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private RevocationCommand command(UUID tenantId, UUID sessionId, String trigger, String reason, UUID actor,
                                      String terminalStatus, boolean callDataplane, String knownRef) {
        RevocationCommand c = new RevocationCommand();
        c.setTenantId(tenantId);
        c.setSessionId(sessionId);
        c.setTrigger(trigger);
        c.setReason(reason);
        c.setActorId(actor);
        c.setTerminalStatus(terminalStatus);
        c.setCallDataplane(callDataplane);
        c.setDataplaneMayExist(true);
        c.setKnownDataplaneRef(knownRef);
        return c;
    }

    private static final class Phase1 {
        boolean found;
        boolean newlyRevoked;
        boolean teardownNeeded;
        boolean confirmed;
        String status;
        Instant revokedAt;
        String ref;
    }

    private static final class Verdict {
        final String trigger;
        final String reason;
        final String terminalStatus;

        Verdict(String trigger, String reason, String terminalStatus) {
            this.trigger = trigger;
            this.reason = reason;
            this.terminalStatus = terminalStatus;
        }
    }

    private static final class Admission {
        final boolean allow;
        final String reason;
        final VpnSubject subject;
        final AccessPolicy policy;
        final DeviceEnrollment device;
        final GatewayEndpoint endpoint;
        final Instant expiresAt;

        private Admission(boolean allow, String reason, VpnSubject subject, AccessPolicy policy, DeviceEnrollment device,
                          GatewayEndpoint endpoint, Instant expiresAt) {
            this.allow = allow;
            this.reason = reason;
            this.subject = subject;
            this.policy = policy;
            this.device = device;
            this.endpoint = endpoint;
            this.expiresAt = expiresAt;
        }

        static Admission deny(String reason) {
            return new Admission(false, reason, null, null, null, null, null);
        }

        static Admission allow(VpnSubject subject, AccessPolicy policy, DeviceEnrollment device, GatewayEndpoint endpoint,
                               Instant expiresAt) {
            return new Admission(true, "ALLOWED", subject, policy, device, endpoint, expiresAt);
        }
    }
}
