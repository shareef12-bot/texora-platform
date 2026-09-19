package com.texora.secops.sso.authn.service;

import com.texora.secops.sso.authn.client.DcIdentityClient;
import com.texora.secops.sso.authn.client.LdapBindClient;
import com.texora.secops.sso.authn.client.MfaVerificationClient;
import com.texora.secops.sso.authn.model.BindResult;
import com.texora.secops.sso.authn.model.LoginOutcome;
import com.texora.secops.sso.authn.model.LoginResult;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginOrchestrationServiceTest {

    @Mock private SsoApplicationRepository applicationRepository;
    @Mock private LdapBindClient ldapBindClient;
    @Mock private SsoMfaEnrollmentRepository mfaEnrollmentRepository;
    @Mock private ConsentPolicyService consentPolicyService;
    @Mock private SessionService sessionService;
    @Mock private TokenService tokenService;
    @Mock private DcIdentityClient dcIdentityClient;
    @Mock private MfaVerificationClient mfaVerificationClient;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();
    private final UUID dcUserId = UUID.randomUUID();

    private LoginOrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new LoginOrchestrationService(applicationRepository, ldapBindClient, mfaEnrollmentRepository,
                consentPolicyService, sessionService, tokenService, dcIdentityClient, mfaVerificationClient);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void stubActiveApplication() {
        SsoApplication application = new SsoApplication(applicationId, tenantId, "ilmora-" + applicationId,
                "Ilmora", "ilmora", ApplicationStatus.ACTIVE, UUID.randomUUID(), Instant.now());
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(application));
    }

    @Test
    void deniesLoginWhenApplicationIsUnknown() {
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

        LoginResult result = service.login("alice", "any-password", applicationId, null);

        assertThat(result.getOutcome()).isEqualTo(LoginOutcome.APPLICATION_UNKNOWN);
        verifyNoInteractions(ldapBindClient, mfaEnrollmentRepository, consentPolicyService, sessionService, tokenService);
    }

    @Test
    void deniesLoginWhenApplicationIsSuspended() {
        SsoApplication suspended = new SsoApplication(applicationId, tenantId, "ilmora-" + applicationId,
                "Ilmora", "ilmora", ApplicationStatus.SUSPENDED, UUID.randomUUID(), Instant.now());
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(suspended));

        LoginResult result = service.login("alice", "any-password", applicationId, null);

        assertThat(result.getOutcome()).isEqualTo(LoginOutcome.APPLICATION_UNKNOWN);
        verifyNoInteractions(ldapBindClient);
    }

    @Test
    void deniesLoginWhenLdapBindFails() {
        stubActiveApplication();
        when(ldapBindClient.verifyBind("alice", "bad-password")).thenReturn(BindResult.failure("BIND_REJECTED"));

        LoginResult result = service.login("alice", "bad-password", applicationId, null);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getOutcome()).isEqualTo(LoginOutcome.BIND_FAILED);
        verifyNoInteractions(mfaEnrollmentRepository, consentPolicyService, sessionService, tokenService);
    }

    @Test
    void deniesLoginWhenMfaRequiredButNotEnrolled() {
        stubActiveApplication();
        when(ldapBindClient.verifyBind("alice", "good-password")).thenReturn(BindResult.success(dcUserId));
        when(mfaEnrollmentRepository.findAllByDcUserIdAndTenantIdAndStatus(dcUserId, tenantId, MfaFactorStatus.ACTIVE))
                .thenReturn(List.of());

        LoginResult result = service.login("alice", "good-password", applicationId, "123456");

        assertThat(result.getOutcome()).isEqualTo(LoginOutcome.MFA_NOT_ENROLLED);
        verifyNoInteractions(consentPolicyService, sessionService, tokenService);
    }

    @Test
    void deniesLoginWhenMfaCodeMissing() {
        stubActiveApplication();
        when(ldapBindClient.verifyBind("alice", "good-password")).thenReturn(BindResult.success(dcUserId));
        when(mfaEnrollmentRepository.findAllByDcUserIdAndTenantIdAndStatus(dcUserId, tenantId, MfaFactorStatus.ACTIVE))
                .thenReturn(List.of(enrolledFactor()));

        LoginResult result = service.login("alice", "good-password", applicationId, null);

        assertThat(result.getOutcome()).isEqualTo(LoginOutcome.MFA_FAILED);
        verifyNoInteractions(consentPolicyService, sessionService, tokenService);
    }

    @Test
    void deniesLoginWhenPolicyOrConsentDenied() {
        stubActiveApplication();
        when(ldapBindClient.verifyBind(any(), any())).thenReturn(BindResult.success(dcUserId));
        when(mfaEnrollmentRepository.findAllByDcUserIdAndTenantIdAndStatus(dcUserId, tenantId, MfaFactorStatus.ACTIVE))
                .thenReturn(List.of(enrolledFactor()));
        when(mfaVerificationClient.verify(dcUserId, "TOTP", "000000")).thenReturn(true);
        when(consentPolicyService.isAccessAllowed(tenantId, applicationId, dcUserId)).thenReturn(false);

        LoginResult result = service.login("alice", "good-password", applicationId, "000000");

        assertThat(result.getOutcome()).isEqualTo(LoginOutcome.POLICY_DENIED);
        verifyNoInteractions(sessionService, tokenService);
    }

    @Test
    void succeedsAndIssuesSessionAndTokensWhenEveryStepPasses() {
        stubActiveApplication();
        when(ldapBindClient.verifyBind(any(), any())).thenReturn(BindResult.success(dcUserId));
        when(mfaEnrollmentRepository.findAllByDcUserIdAndTenantIdAndStatus(dcUserId, tenantId, MfaFactorStatus.ACTIVE))
                .thenReturn(List.of(enrolledFactor()));
        when(mfaVerificationClient.verify(dcUserId, "TOTP", "000000")).thenReturn(true);
        when(consentPolicyService.isAccessAllowed(tenantId, applicationId, dcUserId)).thenReturn(true);
        when(dcIdentityClient.fetchProfile(dcUserId)).thenReturn(Optional.empty());

        SsoSession session = new SsoSession(UUID.randomUUID(), tenantId, dcUserId, applicationId,
                com.texora.secops.sso.domain.SessionStatus.ACTIVE, Instant.now(), Instant.now().plusSeconds(3600));
        when(sessionService.createActiveSession(dcUserId, applicationId)).thenReturn(session);
        when(tokenService.issueTokenSet(session.getId())).thenReturn(List.of());

        LoginResult result = service.login("alice", "good-password", applicationId, "000000");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getSessionId()).isEqualTo(session.getId());
        assertThat(result.getDcUserId()).isEqualTo(dcUserId);
        verify(sessionService).createActiveSession(dcUserId, applicationId);
        verify(tokenService).issueTokenSet(session.getId());
    }

    private SsoMfaEnrollment enrolledFactor() {
        return new SsoMfaEnrollment(UUID.randomUUID(), tenantId, dcUserId, "TOTP",
                MfaFactorStatus.ACTIVE, Instant.now());
    }
}
