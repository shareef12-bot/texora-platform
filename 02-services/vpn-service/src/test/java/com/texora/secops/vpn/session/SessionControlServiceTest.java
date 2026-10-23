package com.texora.secops.vpn.session;

import com.texora.secops.vpn.TestTransactions;
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
import com.texora.secops.vpn.dto.SessionResponse;
import com.texora.secops.vpn.exception.VpnException;
import com.texora.secops.vpn.gateway.LocalStubGatewayIntegrationAdapter;
import com.texora.secops.vpn.metrics.VpnMetrics;
import com.texora.secops.vpn.policy.AccessPolicyEvaluator;
import com.texora.secops.vpn.policy.PolicyConditionsCodec;
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every fail-closed branch of establishment (Requirement 1) and of active-session re-validation (Requirement 2),
 * including SEC unavailability during BOTH, dataplane outage, and unexpected exceptions.
 */
class SessionControlServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z"); // Friday
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final VpnSessionRepository sessions = mock(VpnSessionRepository.class);
    private final GatewayBindingRepository bindings = mock(GatewayBindingRepository.class);
    private final AccessPolicyRepository policies = mock(AccessPolicyRepository.class);
    private final PolicyAssignmentRepository assignments = mock(PolicyAssignmentRepository.class);
    private final GatewayEndpointRepository endpoints = mock(GatewayEndpointRepository.class);
    private final VpnSubjectService subjectService = mock(VpnSubjectService.class);
    private final DeviceEnrollmentService deviceService = mock(DeviceEnrollmentService.class);
    private final MfaFactorVerifier mfa = mock(MfaFactorVerifier.class);
    private final PolicyDecisionClient sec = mock(PolicyDecisionClient.class);
    private final LocalStubGatewayIntegrationAdapter adapter = new LocalStubGatewayIntegrationAdapter();
    private final SessionAuditService audit = mock(SessionAuditService.class);
    private final VpnConfigService config = mock(VpnConfigService.class);
    private final VpnProperties props = new VpnProperties();

    private final AtomicReference<VpnSession> storedSession = new AtomicReference<>();
    private final AtomicReference<GatewayBinding> storedBinding = new AtomicReference<>();

    private final UUID tenant = UUID.randomUUID();
    private final UUID user = UUID.randomUUID(); // SSO subject == DC user id
    private final Caller caller = new Caller(tenant, user);
    private VpnSubject subject;
    private AccessPolicy policy;
    private DeviceEnrollment device;
    private GatewayEndpoint endpoint;
    private SessionControlService service;

    private static final String ALLOW_JSON = "{\"effect\":\"ALLOW\",\"mfaRequired\":true,\"deviceEnrollmentRequired\":true}";

    @BeforeEach
    void setUp() {
        service = new SessionControlService(sessions, bindings, policies, assignments, endpoints, subjectService, deviceService,
                mfa, sec, adapter, new AccessPolicyEvaluator(), new PolicyConditionsCodec(), audit, config, props,
                new VpnMetrics(new SimpleMeterRegistry()), TestTransactions.template(), clock);

        subject = new VpnSubject();
        subject.setId(UUID.randomUUID());
        subject.setTenantId(tenant);
        subject.setDcUserId(user);
        subject.setStatus(VpnConstants.SUBJECT_ACTIVE);

        policy = new AccessPolicy();
        policy.setId(UUID.randomUUID());
        policy.setTenantId(tenant);
        policy.setStatus(VpnConstants.POLICY_APPROVED);
        policy.setConditions(ALLOW_JSON);

        device = new DeviceEnrollment();
        device.setId(UUID.randomUUID());
        device.setTenantId(tenant);
        device.setMfaFactorRef("factor-1");
        device.setStatus(VpnConstants.DEVICE_ACTIVE);

        endpoint = new GatewayEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setEndpointUrl("https://gw.example.internal");

        when(subjectService.findByDcUser(tenant, user)).thenReturn(Optional.of(subject));
        when(subjectService.findDcUserId(tenant, subject.getId())).thenReturn(Optional.of(user));
        when(subjectService.ensureFresh(tenant, subject.getId())).thenReturn(IdentityState.ACTIVE);
        when(policies.findByIdAndTenantId(policy.getId(), tenant)).thenReturn(Optional.of(policy));
        when(policies.findAssignedByStatus(tenant, subject.getId(), VpnConstants.POLICY_APPROVED)).thenReturn(List.of(policy));
        when(assignments.existsByTenantIdAndAccessPolicyIdAndVpnSubjectId(tenant, policy.getId(), subject.getId())).thenReturn(true);
        when(deviceService.findActive(tenant, subject.getId(), "fp-1")).thenReturn(Optional.of(device));
        when(deviceService.findActiveById(tenant, device.getId())).thenReturn(Optional.of(device));
        when(mfa.verify(any(), any(), any(), any())).thenReturn(true);
        when(sec.decide(any(SecDecisionRequest.class), anyInt())).thenReturn(SecDecision.ALLOW);
        when(endpoints.findByTenantIdAndStatusOrderByCreatedAt(tenant, VpnConstants.ENDPOINT_ACTIVE)).thenReturn(List.of(endpoint));
        when(config.sessionMaxMinutes(tenant)).thenReturn(480);
        when(config.revalidationIntervalSeconds(tenant)).thenReturn(60);

        when(sessions.save(any(VpnSession.class))).thenAnswer(i -> {
            storedSession.set(i.getArgument(0));
            return i.getArgument(0);
        });
        when(sessions.findByIdAndTenantId(any(), eq(tenant))).thenAnswer(i -> Optional.ofNullable(storedSession.get()));
        when(sessions.findForUpdate(any(), eq(tenant))).thenAnswer(i -> Optional.ofNullable(storedSession.get()));
        when(bindings.save(any(GatewayBinding.class))).thenAnswer(i -> {
            storedBinding.set(i.getArgument(0));
            return i.getArgument(0);
        });
        when(bindings.findBySessionIdAndTenantId(any(), eq(tenant))).thenAnswer(i -> Optional.ofNullable(storedBinding.get()));
        when(bindings.findByDataplaneSessionRef(any())).thenAnswer(i -> Optional.ofNullable(storedBinding.get()));
    }

    // ------------------------------------------------------------------ helpers

    private EstablishSessionRequest request() {
        EstablishSessionRequest r = new EstablishSessionRequest();
        r.setAccessPolicyId(policy.getId());
        r.setDeviceFingerprint("fp-1");
        r.setMfaCode("000000");
        return r;
    }

    private SessionResponse establishOk() {
        return service.establish(caller, request());
    }

    @SuppressWarnings("unchecked")
    private void assertDenied(String expectedReason) {
        assertThatThrownBy(() -> service.establish(caller, request()))
                .isInstanceOfSatisfying(VpnException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(403);
                    assertThat(e.getCode()).isEqualTo("SESSION_DENIED");
                });
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(audit).record(eq(tenant), any(), eq(user), any(), any(), eq(VpnConstants.ACTION_SESSION_DENIED),
                eq(VpnConstants.OUTCOME_DENIED), any(), payload.capture());
        assertThat(payload.getValue()).containsEntry("reason", expectedReason);
        assertThat(storedSession.get()).as("no session row may be created on denial").isNull();
        assertThat(adapter.sessionCount()).as("dataplane must never be touched on denial").isZero();
    }

    private void assertRevoked(String trigger, boolean uncertainty) {
        VpnSession s = storedSession.get();
        assertThat(s.getStatus()).isIn(VpnConstants.SESSION_REVOKED, VpnConstants.SESSION_EXPIRED);
        assertThat(s.getRevocationTrigger()).isEqualTo(trigger);
        assertThat(adapter.hasSession(s.getId())).as("dataplane tunnel must be torn down").isFalse();
        assertThat(storedBinding.get().getTeardownState()).isEqualTo(VpnConstants.TEARDOWN_CONFIRMED);
        verify(audit).record(eq(tenant), eq(s.getId()), eq(VpnConstants.SYSTEM_ACTOR), any(), any(),
                eq(uncertainty ? VpnConstants.ACTION_SESSION_REVOKED_UNCERTAINTY : VpnConstants.ACTION_SESSION_ENDED),
                eq(VpnConstants.OUTCOME_SUCCESS), any(), any());
        verify(sessions, never()).markValidated(any(), any(), any(), any());
    }

    private UUID establishAndGetId() {
        establishOk();
        return storedSession.get().getId();
    }

    // ================================================================== ESTABLISHMENT

    @Test
    void happyPathEstablishesBindsOpaqueRefAndAudits() {
        SessionResponse r = establishOk();
        assertThat(r.getStatus()).isEqualTo(VpnConstants.SESSION_ACTIVE);
        assertThat(r.getExpiresAt()).isEqualTo(NOW.plusSeconds(480 * 60L));
        VpnSession s = storedSession.get();
        assertThat(adapter.hasSession(s.getId())).isTrue();
        assertThat(storedBinding.get().getDataplaneSessionRef()).startsWith("stub-");
        assertThat(storedBinding.get().getTeardownState()).isEqualTo(VpnConstants.TEARDOWN_NONE);
        assertThat(s.getNextValidationAt()).isEqualTo(NOW.plusSeconds(props.getSession().getEstablishGraceSeconds()));
        verify(audit).record(eq(tenant), eq(s.getId()), eq(user), any(), any(), eq(VpnConstants.ACTION_SESSION_ESTABLISHED),
                eq(VpnConstants.OUTCOME_SUCCESS), any(), any());
        verify(sec).decide(any(SecDecisionRequest.class), eq(1)); // fail fast, no retries while a user waits
    }

    @Test
    void policyMaxSessionMinutesShortensTheSession() {
        policy.setConditions("{\"effect\":\"ALLOW\",\"mfaRequired\":true,\"deviceEnrollmentRequired\":true,\"maxSessionMinutes\":30}");
        assertThat(establishOk().getExpiresAt()).isEqualTo(NOW.plusSeconds(30 * 60L));
    }

    @Test
    void denyWhenSecSaysDeny() {
        when(sec.decide(any(SecDecisionRequest.class), anyInt())).thenReturn(SecDecision.DENY);
        assertDenied("SEC_DENY");
    }

    @Test
    void denyWhenSecIsUnavailableDuringEstablishment() {
        when(sec.decide(any(SecDecisionRequest.class), anyInt())).thenReturn(SecDecision.UNAVAILABLE);
        assertDenied("SEC_UNAVAILABLE");
    }

    @Test
    void denyWhenPolicyEffectIsDeny() {
        policy.setConditions("{\"effect\":\"DENY\"}");
        assertDenied("POLICY_EFFECT_DENY");
    }

    @Test
    void denyWhenStoredPolicyIsAmbiguous() {
        policy.setConditions("{not json");
        assertDenied("POLICY_AMBIGUOUS");
    }

    @Test
    void denyWhenPolicyWindowIsInvalidRatherThanGuessing() {
        policy.setConditions("{\"effect\":\"ALLOW\",\"timeWindow\":{\"zone\":\"Bad/Zone\",\"startTime\":\"09:00\",\"endTime\":\"18:00\"}}");
        assertDenied("POLICY_AMBIGUOUS");
    }

    @Test
    void denyOutsideTimeWindow() {
        policy.setConditions("{\"effect\":\"ALLOW\",\"timeWindow\":{\"zone\":\"UTC\",\"startTime\":\"11:00\",\"endTime\":\"12:00\"}}");
        assertDenied("OUTSIDE_TIME_WINDOW");
    }

    @Test
    void denyWhenMfaFails() {
        when(mfa.verify(any(), any(), any(), any())).thenReturn(false);
        assertDenied("MFA_NOT_SATISFIED");
    }

    @Test
    void denyWhenDeviceNotEnrolledAndMfaCannotBeChecked() {
        when(deviceService.findActive(tenant, subject.getId(), "fp-1")).thenReturn(Optional.empty());
        assertDenied("MFA_NOT_SATISFIED");
        verify(mfa, never()).verify(any(), any(), any(), any());
    }

    @Test
    void denyWhenDeviceMissingEvenIfMfaNotRequired() {
        policy.setConditions("{\"effect\":\"ALLOW\",\"mfaRequired\":false,\"deviceEnrollmentRequired\":true}");
        when(deviceService.findActive(tenant, subject.getId(), "fp-1")).thenReturn(Optional.empty());
        assertDenied("DEVICE_NOT_ENROLLED");
    }

    @Test
    void denyWhenIdentityInactiveOrUncertain() {
        when(subjectService.ensureFresh(tenant, subject.getId())).thenReturn(IdentityState.INACTIVE);
        assertDenied("IDENTITY_INACTIVE");
    }

    @Test
    void denyWhenIdentityUncertain() {
        when(subjectService.ensureFresh(tenant, subject.getId())).thenReturn(IdentityState.UNCERTAIN);
        assertDenied("IDENTITY_UNCERTAIN");
    }

    @Test
    void denyWhenNoSubject() {
        when(subjectService.findByDcUser(tenant, user)).thenReturn(Optional.empty());
        assertDenied("SUBJECT_NOT_FOUND");
    }

    @Test
    void denyWhenPolicyMissingNotApprovedOrNotAssigned() {
        when(policies.findByIdAndTenantId(policy.getId(), tenant)).thenReturn(Optional.empty());
        assertDenied("POLICY_NOT_FOUND");
    }

    @Test
    void denyWhenPolicyNotApproved() {
        policy.setStatus(VpnConstants.POLICY_PENDING);
        assertDenied("POLICY_NOT_APPROVED");
    }

    @Test
    void denyWhenPolicyNotAssigned() {
        when(assignments.existsByTenantIdAndAccessPolicyIdAndVpnSubjectId(tenant, policy.getId(), subject.getId())).thenReturn(false);
        assertDenied("POLICY_NOT_ASSIGNED");
    }

    @Test
    void denyWhenNoGatewayEndpointConfigured() {
        when(endpoints.findByTenantIdAndStatusOrderByCreatedAt(tenant, VpnConstants.ENDPOINT_ACTIVE)).thenReturn(List.of());
        assertDenied("NO_GATEWAY_ENDPOINT");
    }

    @Test
    void anotherAssignedDenyPolicyOverridesTheAllow() {
        AccessPolicy deny = new AccessPolicy();
        deny.setId(UUID.randomUUID());
        deny.setConditions("{\"effect\":\"DENY\"}");
        when(policies.findAssignedByStatus(tenant, subject.getId(), VpnConstants.POLICY_APPROVED)).thenReturn(List.of(policy, deny));
        assertDenied("DENY_POLICY_APPLIES");
    }

    @Test
    void anotherAssignedPolicyThatCannotBeEvaluatedDeniesToo() {
        AccessPolicy broken = new AccessPolicy();
        broken.setId(UUID.randomUUID());
        broken.setConditions("garbage");
        when(policies.findAssignedByStatus(tenant, subject.getId(), VpnConstants.POLICY_APPROVED)).thenReturn(List.of(policy, broken));
        assertDenied("POLICY_AMBIGUOUS");
    }

    @Test
    void anUnexpectedExceptionDuringEvaluationIsADenyNotA500() {
        when(sec.decide(any(SecDecisionRequest.class), anyInt())).thenThrow(new IllegalStateException("boom"));
        assertDenied("INTERNAL_ERROR");
    }

    @Test
    void aFailureToWriteTheDenialAuditStillDenies() {
        when(sec.decide(any(SecDecisionRequest.class), anyInt())).thenReturn(SecDecision.DENY);
        org.mockito.Mockito.doThrow(new IllegalStateException("db down")).when(audit)
                .record(any(), any(), any(), any(), any(), any(), any(), any(), any());
        assertThatThrownBy(() -> service.establish(caller, request()))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(403));
    }

    // ---- dataplane trouble during establishment

    @Test
    void dataplaneOutageDuringEstablishmentDeniesAndLeavesNothingActive() {
        adapter.setSimulatedOutage(true);
        assertThatThrownBy(() -> service.establish(caller, request()))
                .isInstanceOfSatisfying(VpnException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(504);
                    assertThat(e.getCode()).isEqualTo("DATAPLANE_UNAVAILABLE");
                });
        VpnSession s = storedSession.get();
        assertThat(s.getStatus()).isEqualTo(VpnConstants.SESSION_REVOKED);
        assertThat(s.getRevocationTrigger()).isEqualTo(VpnConstants.TRIGGER_ESTABLISH_FAILURE);
        // The outcome was ambiguous, so a teardown is owed and recorded until the dataplane confirms.
        assertThat(storedBinding.get().getTeardownState()).isEqualTo(VpnConstants.TEARDOWN_PENDING);
    }

    @Test
    void ambiguousEstablishmentIsCleanedUpOnceTheDataplaneRecovers() {
        adapter.setSimulatedOutage(true);
        assertThatThrownBy(() -> service.establish(caller, request())).isInstanceOf(VpnException.class);
        adapter.setSimulatedOutage(false);
        when(bindings.findTeardownDueAcrossTenants(any(), any(Pageable.class))).thenReturn(List.of(storedBinding.get()));
        when(bindings.claimTeardown(any(), eq(tenant), any(), any())).thenReturn(1);
        assertThat(service.retryPendingTeardowns()).isEqualTo(1);
        assertThat(storedBinding.get().getTeardownState()).isEqualTo(VpnConstants.TEARDOWN_CONFIRMED);
    }

    @Test
    void ifTheBindingCannotBeRecordedTheTunnelIsTornDown() {
        when(bindings.save(any(GatewayBinding.class))).thenThrow(new IllegalStateException("db down"))
                .thenAnswer(i -> {
                    storedBinding.set(i.getArgument(0));
                    return i.getArgument(0);
                });
        assertThatThrownBy(() -> service.establish(caller, request()))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(500));
        VpnSession s = storedSession.get();
        assertThat(s.getStatus()).isEqualTo(VpnConstants.SESSION_REVOKED);
        assertThat(adapter.hasSession(s.getId())).isFalse();
    }

    // ================================================================== RE-VALIDATION

    @Test
    void confirmedAuthorizationKeepsTheSessionActive() {
        UUID id = establishAndGetId();
        service.revalidate(tenant, id);
        assertThat(storedSession.get().getStatus()).isEqualTo(VpnConstants.SESSION_ACTIVE);
        assertThat(adapter.hasSession(id)).isTrue();
        verify(sessions).markValidated(id, tenant, NOW, NOW.plusSeconds(60));
        verify(sec).decide(any(SecDecisionRequest.class), eq(props.getSec().getRevalidationAttempts()));
    }

    @Test
    void secUnavailableDuringRevalidationRevokes() {
        UUID id = establishAndGetId();
        when(sec.decide(any(SecDecisionRequest.class), anyInt())).thenReturn(SecDecision.UNAVAILABLE);
        service.revalidate(tenant, id);
        assertRevoked(VpnConstants.TRIGGER_SEC_UNAVAILABLE, true);
    }

    @Test
    void secDenyDuringRevalidationRevokes() {
        UUID id = establishAndGetId();
        when(sec.decide(any(SecDecisionRequest.class), anyInt())).thenReturn(SecDecision.DENY);
        service.revalidate(tenant, id);
        assertRevoked(VpnConstants.TRIGGER_POLICY_DENY, true);
    }

    @Test
    void identityUncertainRevokes() {
        UUID id = establishAndGetId();
        when(subjectService.ensureFresh(tenant, subject.getId())).thenReturn(IdentityState.UNCERTAIN);
        service.revalidate(tenant, id);
        assertRevoked(VpnConstants.TRIGGER_IDENTITY_UNCERTAIN, true);
        verify(sec, times(1)).decide(any(SecDecisionRequest.class), anyInt()); // only the establishment call
    }

    @Test
    void identityInactiveRevokes() {
        UUID id = establishAndGetId();
        when(subjectService.ensureFresh(tenant, subject.getId())).thenReturn(IdentityState.INACTIVE);
        service.revalidate(tenant, id);
        assertRevoked(VpnConstants.TRIGGER_IDENTITY_INACTIVE, true);
    }

    @Test
    void disabledPolicyRevokes() {
        UUID id = establishAndGetId();
        policy.setStatus(VpnConstants.POLICY_DISABLED);
        service.revalidate(tenant, id);
        assertRevoked(VpnConstants.TRIGGER_POLICY_CHANGED, true);
    }

    @Test
    void unassignedPolicyRevokes() {
        UUID id = establishAndGetId();
        when(assignments.existsByTenantIdAndAccessPolicyIdAndVpnSubjectId(tenant, policy.getId(), subject.getId())).thenReturn(false);
        service.revalidate(tenant, id);
        assertRevoked(VpnConstants.TRIGGER_POLICY_CHANGED, true);
    }

    @Test
    void policyThatBecameUnreadableRevokes() {
        UUID id = establishAndGetId();
        policy.setConditions("{{{");
        service.revalidate(tenant, id);
        assertRevoked(VpnConstants.TRIGGER_POLICY_AMBIGUOUS, true);
    }

    @Test
    void revokedDeviceRevokes() {
        UUID id = establishAndGetId();
        when(deviceService.findActiveById(tenant, device.getId())).thenReturn(Optional.empty());
        service.revalidate(tenant, id);
        assertRevoked(VpnConstants.TRIGGER_POLICY_DENY, true);
    }

    @Test
    void aNewlyAssignedDenyPolicyRevokes() {
        UUID id = establishAndGetId();
        AccessPolicy deny = new AccessPolicy();
        deny.setId(UUID.randomUUID());
        deny.setConditions("{\"effect\":\"DENY\"}");
        when(policies.findAssignedByStatus(tenant, subject.getId(), VpnConstants.POLICY_APPROVED)).thenReturn(List.of(policy, deny));
        service.revalidate(tenant, id);
        assertRevoked(VpnConstants.TRIGGER_POLICY_DENY, true);
    }

    @Test
    void missingBindingOnAnActiveSessionIsAnInconsistencyAndRevokes() {
        UUID id = establishAndGetId();
        GatewayBinding real = storedBinding.getAndSet(null);
        service.revalidate(tenant, id);
        // binding is created as PENDING teardown by revocation (a tunnel may exist) and then confirmed by the stub
        assertThat(storedSession.get().getRevocationTrigger()).isEqualTo(VpnConstants.TRIGGER_STATE_INCONSISTENT);
        assertThat(storedSession.get().getStatus()).isEqualTo(VpnConstants.SESSION_REVOKED);
        assertThat(real).isNotNull();
    }

    @Test
    void anyUnexpectedExceptionDuringRevalidationRevokes() {
        UUID id = establishAndGetId();
        when(sec.decide(any(SecDecisionRequest.class), anyInt())).thenThrow(new IllegalStateException("boom"));
        service.revalidate(tenant, id);
        assertRevoked(VpnConstants.TRIGGER_INTERNAL_ERROR, true);
    }

    @Test
    void sessionPastItsLifetimeExpires() {
        UUID id = establishAndGetId();
        storedSession.get().setExpiresAt(NOW.minusSeconds(1));
        service.revalidate(tenant, id);
        assertThat(storedSession.get().getStatus()).isEqualTo(VpnConstants.SESSION_EXPIRED);
        assertThat(storedSession.get().getRevocationTrigger()).isEqualTo(VpnConstants.TRIGGER_EXPIRY);
        assertThat(adapter.hasSession(id)).isFalse();
        verify(audit).record(any(), eq(id), eq(VpnConstants.SYSTEM_ACTOR), any(), any(), eq(VpnConstants.ACTION_SESSION_ENDED),
                any(), any(), any());
    }

    @Test
    void revalidatingAnAlreadyRevokedOrUnknownSessionIsANoOp() {
        UUID id = establishAndGetId();
        storedSession.get().setStatus(VpnConstants.SESSION_REVOKED);
        service.revalidate(tenant, id);
        verify(sec, times(1)).decide(any(SecDecisionRequest.class), anyInt());
        storedSession.set(null);
        service.revalidate(tenant, UUID.randomUUID());
    }

    @Test
    void secOutageDuringRevalidationWithDataplaneAlsoDownStillRevokesInTheControlPlaneAndRetries() {
        UUID id = establishAndGetId();
        when(sec.decide(any(SecDecisionRequest.class), anyInt())).thenReturn(SecDecision.UNAVAILABLE);
        adapter.setSimulatedOutage(true);
        service.revalidate(tenant, id); // must not throw
        assertThat(storedSession.get().getStatus()).isEqualTo(VpnConstants.SESSION_REVOKED);
        assertThat(storedBinding.get().getTeardownState()).isEqualTo(VpnConstants.TEARDOWN_PENDING);
        assertThat(storedBinding.get().getTeardownAttempts()).isEqualTo(1);

        adapter.setSimulatedOutage(false);
        when(bindings.findTeardownDueAcrossTenants(any(), any(Pageable.class))).thenReturn(List.of(storedBinding.get()));
        when(bindings.claimTeardown(any(), eq(tenant), any(), any())).thenReturn(1);
        assertThat(service.retryPendingTeardowns()).isEqualTo(1);
        assertThat(storedBinding.get().getTeardownState()).isEqualTo(VpnConstants.TEARDOWN_CONFIRMED);
        assertThat(adapter.hasSession(id)).isFalse();
    }

    @Test
    void retryLosingTheClaimDoesNothing() {
        establishAndGetId();
        storedBinding.get().setTeardownState(VpnConstants.TEARDOWN_PENDING);
        when(bindings.findTeardownDueAcrossTenants(any(), any(Pageable.class))).thenReturn(List.of(storedBinding.get()));
        when(bindings.claimTeardown(any(), any(), any(), any())).thenReturn(0);
        assertThat(service.retryPendingTeardowns()).isZero();
    }

    @Test
    void ifTheDatabaseIsDownDuringRevocationTheTunnelIsStillKilledDirectly() {
        UUID id = establishAndGetId();
        when(sec.decide(any(SecDecisionRequest.class), anyInt())).thenReturn(SecDecision.UNAVAILABLE);
        when(sessions.findForUpdate(any(), eq(tenant))).thenThrow(new IllegalStateException("db down"));
        service.revalidate(tenant, id); // must not throw
        assertThat(adapter.hasSession(id)).as("fail closed even when bookkeeping fails").isFalse();
    }

    // ================================================================== ADMIN REVOKE / CALLBACK / LIST

    @Test
    void adminRevokeTearsDownAndReportsConfirmation() {
        UUID id = establishAndGetId();
        var r = service.revokeByAdmin(caller, id, "lost laptop");
        assertThat(r.getStatus()).isEqualTo(VpnConstants.SESSION_REVOKED);
        assertThat(r.isDataplaneRevocationConfirmed()).isTrue();
        assertThat(adapter.hasSession(id)).isFalse();
        verify(audit).record(eq(tenant), eq(id), eq(user), any(), any(), eq(VpnConstants.ACTION_SESSION_REVOKED_ADMIN),
                eq(VpnConstants.OUTCOME_SUCCESS), any(), any());
    }

    @Test
    void adminRevokeIsIdempotent() {
        UUID id = establishAndGetId();
        service.revokeByAdmin(caller, id, "x");
        var again = service.revokeByAdmin(caller, id, "x");
        assertThat(again.getStatus()).isEqualTo(VpnConstants.SESSION_REVOKED);
        assertThat(again.isDataplaneRevocationConfirmed()).isTrue();
        verify(audit, times(1)).record(any(), eq(id), any(), any(), any(), eq(VpnConstants.ACTION_SESSION_REVOKED_ADMIN),
                any(), any(), any());
    }

    @Test
    void adminRevokeWithDataplaneDownRevokesAndReportsUnconfirmed() {
        UUID id = establishAndGetId();
        adapter.setSimulatedOutage(true);
        var r = service.revokeByAdmin(caller, id, "x");
        assertThat(r.getStatus()).isEqualTo(VpnConstants.SESSION_REVOKED);
        assertThat(r.isDataplaneRevocationConfirmed()).isFalse();
        assertThat(storedBinding.get().getTeardownState()).isEqualTo(VpnConstants.TEARDOWN_PENDING);
        // a second admin click after recovery completes the teardown
        adapter.setSimulatedOutage(false);
        assertThat(service.revokeByAdmin(caller, id, "retry").isDataplaneRevocationConfirmed()).isTrue();
    }

    @Test
    void adminRevokeUnknownIs404AndCrossTenantIs403() {
        UUID missing = UUID.randomUUID();
        when(sessions.findByIdAndTenantId(missing, tenant)).thenReturn(Optional.empty());
        when(sessions.existsByIdAndTenantIdNot(missing, tenant)).thenReturn(false);
        assertThatThrownBy(() -> service.revokeByAdmin(caller, missing, "x"))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(404));
        when(sessions.existsByIdAndTenantIdNot(missing, tenant)).thenReturn(true);
        assertThatThrownBy(() -> service.revokeByAdmin(caller, missing, "x"))
                .isInstanceOfSatisfying(VpnException.class, e -> assertThat(e.getStatus()).isEqualTo(403));
    }

    @Test
    void dataplaneReportedTerminationEndsTheSessionWithoutCallingTheDataplaneBack() {
        UUID id = establishAndGetId();
        String ref = storedBinding.get().getDataplaneSessionRef();
        storedBinding.get().setTenantId(tenant);
        storedBinding.get().setSessionId(id);
        assertThat(service.onDataplaneTunnelTerminated(ref)).isTrue();
        assertThat(storedSession.get().getStatus()).isEqualTo(VpnConstants.SESSION_EXPIRED);
        assertThat(storedSession.get().getRevocationTrigger()).isEqualTo(VpnConstants.TRIGGER_DATAPLANE_REPORTED);
        assertThat(storedBinding.get().getTeardownState()).isEqualTo(VpnConstants.TEARDOWN_CONFIRMED);
        assertThat(adapter.hasSession(id)).as("callback must not trigger a dataplane call").isTrue();
    }

    @Test
    void dataplaneReportForUnknownRefIsIgnored() {
        when(bindings.findByDataplaneSessionRef("nope")).thenReturn(Optional.empty());
        assertThat(service.onDataplaneTunnelTerminated("nope")).isFalse();
    }

    @Test
    void listMapsSessions() {
        establishOk();
        when(sessions.findByTenantIdOrderByEstablishedAtDesc(eq(tenant), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(storedSession.get())));
        when(sessions.findByTenantIdAndStatusOrderByEstablishedAtDesc(eq(tenant), eq("ACTIVE"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(storedSession.get())));
        assertThat(service.list(tenant, null, 0, 50).getItems()).hasSize(1);
        assertThat(service.list(tenant, "ACTIVE", 0, 50).getItems().get(0).getStatus()).isEqualTo("ACTIVE");
    }
}
