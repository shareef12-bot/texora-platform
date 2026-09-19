package com.texora.secops.sso.application.service;

import com.texora.secops.sso.domain.Protocol;
import com.texora.secops.sso.domain.SsoApplication;
import com.texora.secops.sso.dto.ApplicationRegistrationRequest;
import com.texora.secops.sso.exception.ConflictException;
import com.texora.secops.sso.repository.SsoApplicationRepository;
import com.texora.secops.sso.repository.SsoOidcSamlConfigRepository;
import com.texora.secops.sso.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicationRegistrationServiceTest {

    @Mock private SsoApplicationRepository applicationRepository;
    @Mock private SsoOidcSamlConfigRepository configRepository;

    private final UUID tenantId = UUID.randomUUID();
    private ApplicationRegistrationService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        service = new ApplicationRegistrationService(applicationRepository, configRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void registersOneOfTheFiveCanonicalProductsWithAMatchingFederationConfig() {
        ApplicationRegistrationRequest request = new ApplicationRegistrationRequest();
        request.setName("Ilmora");
        request.setProductId("ilmora");
        request.setProtocol(Protocol.OIDC);
        request.setRedirectUris(List.of("https://ilmora.texora.com/callback"));

        when(applicationRepository.existsByClientId(any())).thenReturn(false);
        when(applicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SsoApplication application = service.register(request, UUID.randomUUID());

        assertThat(application.getProductId()).isEqualTo("ilmora");
        assertThat(application.getTenantId()).isEqualTo(tenantId);
        assertThat(application.getClientId()).startsWith("ilmora-");
        verify(configRepository).save(any());
    }

    @Test
    void rejectsRegistrationOnClientIdCollision() {
        ApplicationRegistrationRequest request = new ApplicationRegistrationRequest();
        request.setName("Ilmora");
        request.setProductId("ilmora");
        request.setProtocol(Protocol.OIDC);
        request.setRedirectUris(List.of("https://ilmora.texora.com/callback"));

        when(applicationRepository.existsByClientId(any())).thenReturn(true);

        org.junit.jupiter.api.Assertions.assertThrows(ConflictException.class,
                () -> service.register(request, UUID.randomUUID()));
        verify(configRepository, never()).save(any());
    }
}
