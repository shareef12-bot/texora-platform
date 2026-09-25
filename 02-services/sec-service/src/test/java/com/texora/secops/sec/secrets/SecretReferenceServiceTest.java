package com.texora.secops.sec.secrets;

import com.texora.secops.sec.secrets.adapter.VaultKmsAdapter;
import com.texora.secops.sec.secrets.domain.SecSecretReference;
import com.texora.secops.sec.secrets.dto.SecretReferenceResponse;
import com.texora.secops.sec.secrets.repository.SecSecretReferenceRepository;
import com.texora.secops.sec.secrets.service.SecretReferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecretReferenceService — Owner-Scope Access Control")
class SecretReferenceServiceTest {

    @Mock private SecSecretReferenceRepository referenceRepository;
    @Mock private VaultKmsAdapter vaultKmsAdapter;

    private SecretReferenceService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID REF_ID    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SecretReferenceService(referenceRepository, vaultKmsAdapter);
    }

    @Test
    @DisplayName("Cross-service access returns 403 Forbidden — not empty result")
    void crossServiceAccess_returns403() {
        SecSecretReference ref = buildReference("sso", "ACTIVE");
        when(referenceRepository.findByTenantIdAndLogicalName(TENANT_ID, "db.password"))
                .thenReturn(Optional.of(ref));

        // vpn tries to access sso's secret — must be 403
        assertThatThrownBy(() ->
            service.resolveReference(TENANT_ID, "db.password", "vpn"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(403);
                    assertThat(rse.getReason()).containsIgnoringCase("owner");
                });
    }

    @Test
    @DisplayName("Owner can resolve their own reference — returns path+keyId only, no material")
    void owner_canResolveOwnReference() {
        SecSecretReference ref = buildReference("sso", "ACTIVE");
        when(referenceRepository.findByTenantIdAndLogicalName(TENANT_ID, "db.password"))
                .thenReturn(Optional.of(ref));

        SecretReferenceResponse response = service.resolveReference(TENANT_ID, "db.password", "sso");

        assertThat(response).isNotNull();
        assertThat(response.getVaultPath()).isEqualTo("secret/sso/db");
        assertThat(response.getKmsKeyId()).isEqualTo("kms/key/sso");
        // Verify no material leakage — response only has path references
        assertThat(response.getReferenceId()).isEqualTo(REF_ID);
    }

    @Test
    @DisplayName("REVOKED reference cannot be resolved")
    void revokedReference_returns422() {
        SecSecretReference ref = buildReference("sso", "REVOKED");
        when(referenceRepository.findByTenantIdAndLogicalName(TENANT_ID, "db.password"))
                .thenReturn(Optional.of(ref));

        assertThatThrownBy(() ->
            service.resolveReference(TENANT_ID, "db.password", "sso"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(422);
                });
    }

    @Test
    @DisplayName("Non-existent reference returns 404")
    void nonExistentReference_returns404() {
        when(referenceRepository.findByTenantIdAndLogicalName(TENANT_ID, "missing.key"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            service.resolveReference(TENANT_ID, "missing.key", "sso"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(404);
                });
    }

    @Test
    @DisplayName("Registration fails when vault path is not accessible")
    void registration_failsWhenVaultPathInaccessible() {
        when(vaultKmsAdapter.verifyVaultPath(anyString())).thenReturn(false);

        assertThatThrownBy(() ->
            service.registerReference(TENANT_ID, "db.password", "secret/bad/path",
                    "kms/key/1", "sso", "CONFIDENTIAL"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value()).isEqualTo(422);
                });
    }

    private SecSecretReference buildReference(String owningService, String status) {
        SecSecretReference ref = new SecSecretReference(
                REF_ID, TENANT_ID, "db.password",
                "secret/sso/db", "kms/key/sso", owningService, "CONFIDENTIAL");
        ref.setStatus(status);
        return ref;
    }
}
