package com.texora.secops.cgi.config;

import com.texora.secops.cgi.audit.service.CgiAuditWriter;
import com.texora.secops.cgi.common.CgiApiException;
import com.texora.secops.cgi.config.domain.ConfigEntry;
import com.texora.secops.cgi.config.dto.ConfigRequest;
import com.texora.secops.cgi.config.props.ExecutionProperties;
import com.texora.secops.cgi.config.service.TenantConfigService;
import com.texora.secops.cgi.repository.ConfigEntryRepository;
import com.texora.secops.cgi.security.CallerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantConfigServiceTest {

    private final ConfigEntryRepository repo = mock(ConfigEntryRepository.class);
    private final CgiAuditWriter audit = mock(CgiAuditWriter.class);
    private final ExecutionProperties props = new ExecutionProperties();
    private TenantConfigService service;
    private final UUID tenant = UUID.randomUUID();
    private final CallerContext caller = new CallerContext(tenant, UUID.randomUUID(), Set.of());

    @BeforeEach
    void setUp() {
        service = new TenantConfigService(repo, props, audit, Clock.systemUTC());
        when(repo.findByTenantId(tenant)).thenReturn(List.of());
        when(repo.findByTenantIdAndConfigKey(any(), any())).thenReturn(Optional.empty());
    }

    private ConfigEntry entry(String key, String value) {
        return new ConfigEntry(UUID.randomUUID(), tenant, key, value, UUID.randomUUID(), Instant.now());
    }

    @Test
    void unknownKeysAreRejectedSoNoSecretCanBeParkedHere() {
        assertThatThrownBy(() -> service.upsert(caller, new ConfigRequest("db.password", "x")))
                .isInstanceOfSatisfying(CgiApiException.class, e -> assertThat(e.getCode()).isEqualTo("UNKNOWN_CONFIG_KEY"));
    }

    @Test
    void defaultsApplyWhenNothingConfigured() {
        assertThat(service.extraSensitiveKeys(tenant)).isEmpty();
        assertThat(service.maxRequestBodyBytes(tenant)).isEqualTo(props.getMaxRequestBodyBytes());
    }

    @Test
    void extraSensitiveKeysAreValidatedAndStored() {
        assertThat(service.upsert(caller, new ConfigRequest("redaction.extra-sensitive-keys", " badge , employeeNo ")).getValue())
                .isEqualTo("badge,employeeNo");
        assertThatThrownBy(() -> service.upsert(caller, new ConfigRequest("redaction.extra-sensitive-keys", "bad name!")))
                .isInstanceOf(CgiApiException.class);
        verify(audit).recordInTransaction(any());
    }

    @Test
    void tenantBodyLimitCanOnlyLowerThePlatformCap() {
        when(repo.findByTenantId(tenant)).thenReturn(List.of(entry(TenantConfigService.KEY_MAX_BODY, "100")));
        assertThat(service.maxRequestBodyBytes(tenant)).isEqualTo(100L);
        assertThatThrownBy(() -> service.upsert(caller, new ConfigRequest(TenantConfigService.KEY_MAX_BODY, String.valueOf(props.getMaxRequestBodyBytes() + 1))))
                .isInstanceOf(CgiApiException.class);
        assertThatThrownBy(() -> service.upsert(caller, new ConfigRequest(TenantConfigService.KEY_MAX_BODY, "abc"))).isInstanceOf(CgiApiException.class);
    }

    @Test
    void garbageStoredValueFallsBackToPlatformCap() {
        UUID other = UUID.randomUUID();
        when(repo.findByTenantId(other)).thenReturn(List.of(new ConfigEntry(UUID.randomUUID(), other, TenantConfigService.KEY_MAX_BODY, "xx", UUID.randomUUID(), Instant.now())));
        assertThat(service.maxRequestBodyBytes(other)).isEqualTo(props.getMaxRequestBodyBytes());
    }

    @Test
    void readsAreCachedAndWritesInvalidateTheCache() {
        service.extraSensitiveKeys(tenant);
        service.extraSensitiveKeys(tenant);
        verify(repo, times(1)).findByTenantId(tenant);
        service.upsert(caller, new ConfigRequest(TenantConfigService.KEY_MAX_BODY, "50"));
        service.extraSensitiveKeys(tenant);
        verify(repo, times(2)).findByTenantId(tenant);
    }

    @Test
    void readFailurePropagatesSoRedactionRulesAreNeverSilentlySkipped() {
        UUID broken = UUID.randomUUID();
        when(repo.findByTenantId(broken)).thenThrow(new IllegalStateException("db down"));
        assertThatThrownBy(() -> service.extraSensitiveKeys(broken)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void listReturnsTenantEntries() {
        when(repo.findByTenantId(tenant)).thenReturn(List.of(entry(TenantConfigService.KEY_MAX_BODY, "5")));
        assertThat(service.list(tenant).getItems()).hasSize(1);
    }
}
