package com.texora.secops.ldap.bind.service;

import com.texora.secops.ldap.audit.LdapAuditHelper;
import com.texora.secops.ldap.bind.dto.BindConfigRequest;
import com.texora.secops.ldap.bind.dto.BindConfigResponse;
import com.texora.secops.ldap.domain.LdapBindConfig;
import com.texora.secops.ldap.repository.BindConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecureBindConfigService")
class SecureBindConfigServiceTest {

    @Mock
    private BindConfigRepository bindConfigRepository;
    @Mock
    private LdapAuditHelper auditHelper;

    private SecureBindConfigService service;

    private final UUID tenantId  = UUID.randomUUID();
    private final UUID actorId   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SecureBindConfigService(bindConfigRepository, auditHelper);
    }

    @Test
    @DisplayName("createConfig: rejects non-TLS binds")
    void createConfig_nonTlsRejected() {
        BindConfigRequest req = new BindConfigRequest();
        req.setBindDn("cn=admin,dc=example,dc=com");
        req.setTlsRequired(false);

        assertThatThrownBy(() -> service.createConfig(req, actorId, tenantId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tlsRequired");
    }

    @Test
    @DisplayName("createConfig: rejects blank bindDn")
    void createConfig_blankBindDn() {
        BindConfigRequest req = new BindConfigRequest();
        req.setBindDn("   ");
        req.setTlsRequired(true);

        assertThatThrownBy(() -> service.createConfig(req, actorId, tenantId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bindDn");
    }

    @Test
    @DisplayName("createConfig: saves and audits valid config")
    void createConfig_happy() {
        BindConfigRequest req = new BindConfigRequest();
        req.setBindDn("cn=svc,dc=example,dc=com");
        req.setTlsRequired(true);
        req.setAllowedSource("10.0.0.0/8");

        when(bindConfigRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BindConfigResponse resp = service.createConfig(req, actorId, tenantId);

        verify(bindConfigRepository).save(any(LdapBindConfig.class));
        verify(auditHelper).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("getConfig: throws when not found for tenant")
    void getConfig_notFound() {
        UUID id = UUID.randomUUID();
        when(bindConfigRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConfig(id, tenantId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("updateConfig: rejects status not in enum")
    void updateConfig_invalidStatus() {
        UUID id = UUID.randomUUID();
        LdapBindConfig existing = new LdapBindConfig(
                id, tenantId, "cn=svc,dc=x,dc=com", true, null,
                LdapBindConfig.Status.ACTIVE, actorId, Instant.now());
        when(bindConfigRepository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(existing));

        BindConfigRequest req = new BindConfigRequest();
        req.setBindDn("cn=svc,dc=x,dc=com");
        req.setTlsRequired(true);
        req.setStatus("INVALID_STATUS");

        assertThatThrownBy(() -> service.updateConfig(id, req, actorId, tenantId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid status");
    }
}
