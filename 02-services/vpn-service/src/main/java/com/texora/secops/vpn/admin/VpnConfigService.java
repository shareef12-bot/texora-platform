package com.texora.secops.vpn.admin;

import com.texora.secops.vpn.VpnConstants;
import com.texora.secops.vpn.audit.VpnAuditRecorder;
import com.texora.secops.vpn.config.VpnProperties;
import com.texora.secops.vpn.domain.VpnConfigEntry;
import com.texora.secops.vpn.dto.ConfigEntryResponse;
import com.texora.secops.vpn.exception.VpnException;
import com.texora.secops.vpn.repository.VpnConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-tenant, allow-listed, bounds-checked settings. Non-secret by construction: only the keys below exist, and each
 * is a bounded integer. The revalidation interval additionally cannot be set so high that it breaks the global
 * max-unverified horizon (that would be silent UNDER-triggering).
 */
@Service
public class VpnConfigService {

    public static final String KEY_SESSION_MAX_MINUTES = "session.max-minutes";
    public static final String KEY_REVALIDATION_INTERVAL = "session.revalidation-interval-seconds";
    public static final String KEY_IDENTITY_STALENESS = "identity.staleness-seconds";

    private static final Map<String, int[]> BOUNDS = Map.of(
            KEY_SESSION_MAX_MINUTES, new int[]{1, 1440},
            KEY_REVALIDATION_INTERVAL, new int[]{15, 600},
            KEY_IDENTITY_STALENESS, new int[]{30, 3600});

    private final VpnConfigRepository repository;
    private final VpnProperties properties;
    private final VpnAuditRecorder audit;
    private final Clock clock;

    public VpnConfigService(VpnConfigRepository repository, VpnProperties properties, VpnAuditRecorder audit, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    public int sessionMaxMinutes(UUID tenantId) {
        return effective(tenantId, KEY_SESSION_MAX_MINUTES);
    }

    public int revalidationIntervalSeconds(UUID tenantId) {
        return effective(tenantId, KEY_REVALIDATION_INTERVAL);
    }

    public int identityStalenessSeconds(UUID tenantId) {
        return effective(tenantId, KEY_IDENTITY_STALENESS);
    }

    @Transactional(readOnly = true)
    public List<ConfigEntryResponse> list(UUID tenantId) {
        Map<String, VpnConfigEntry> overrides = new LinkedHashMap<>();
        for (VpnConfigEntry e : repository.findByTenantIdOrderByConfigKey(tenantId)) {
            overrides.put(e.getConfigKey(), e);
        }
        List<ConfigEntryResponse> out = new ArrayList<>();
        for (String key : BOUNDS.keySet().stream().sorted().toList()) {
            ConfigEntryResponse r = new ConfigEntryResponse();
            r.setKey(key);
            VpnConfigEntry o = overrides.get(key);
            if (o != null) {
                r.setValue(o.getConfigValue());
                r.setUpdatedBy(o.getUpdatedBy());
                r.setUpdatedAt(o.getUpdatedAt());
            } else {
                r.setValue(Integer.toString(globalDefault(key)));
            }
            out.add(r);
        }
        return out;
    }

    @Transactional
    public ConfigEntryResponse set(UUID tenantId, UUID actorId, String key, String rawValue) {
        int[] bounds = BOUNDS.get(key);
        if (bounds == null) {
            throw VpnException.unprocessable("CONFIG_KEY_UNKNOWN", "Unknown configuration key");
        }
        int value;
        try {
            value = Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException e) {
            throw VpnException.unprocessable("CONFIG_VALUE_INVALID", "Value must be an integer");
        }
        if (value < bounds[0] || value > bounds[1]) {
            throw VpnException.unprocessable("CONFIG_VALUE_OUT_OF_BOUNDS",
                    "Value must be between " + bounds[0] + " and " + bounds[1]);
        }
        if (KEY_REVALIDATION_INTERVAL.equals(key)
                && 2L * value + properties.getSession().getClaimLeaseSeconds() > properties.getSession().getMaxUnverifiedSeconds()) {
            throw VpnException.unprocessable("CONFIG_VALUE_BREAKS_GUARANTEE",
                    "Interval is too long for the max-unverified horizon; stale sessions could survive");
        }
        VpnConfigEntry entry = repository.findByTenantIdAndConfigKey(tenantId, key).orElseGet(() -> {
            VpnConfigEntry fresh = new VpnConfigEntry();
            fresh.setId(UUID.randomUUID());
            fresh.setTenantId(tenantId);
            fresh.setConfigKey(key);
            return fresh;
        });
        String previous = entry.getConfigValue();
        entry.setConfigValue(Integer.toString(value));
        entry.setUpdatedBy(actorId);
        entry.setUpdatedAt(clock.instant());
        repository.save(entry);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("key", key);
        payload.put("previous", previous);
        payload.put("value", entry.getConfigValue());
        audit.record(tenantId, actorId, VpnConstants.ACTION_CONFIG_CHANGED, "CONFIG", key, VpnConstants.OUTCOME_SUCCESS, payload);
        ConfigEntryResponse r = new ConfigEntryResponse();
        r.setKey(key);
        r.setValue(entry.getConfigValue());
        r.setUpdatedBy(actorId);
        r.setUpdatedAt(entry.getUpdatedAt());
        return r;
    }

    private int effective(UUID tenantId, String key) {
        Optional<VpnConfigEntry> override = repository.findByTenantIdAndConfigKey(tenantId, key);
        if (override.isPresent()) {
            try {
                int v = Integer.parseInt(override.get().getConfigValue());
                int[] b = BOUNDS.get(key);
                if (v >= b[0] && v <= b[1]) {
                    return v;
                }
            } catch (NumberFormatException ignored) {
                // fall through to the global default; a corrupt override must not weaken anything
            }
        }
        return globalDefault(key);
    }

    private int globalDefault(String key) {
        switch (key) {
            case KEY_SESSION_MAX_MINUTES:
                return properties.getSession().getMaxMinutes();
            case KEY_REVALIDATION_INTERVAL:
                return properties.getSession().getRevalidationIntervalSeconds();
            case KEY_IDENTITY_STALENESS:
                return properties.getSession().getIdentityStalenessSeconds();
            default:
                throw new IllegalArgumentException("unknown config key");
        }
    }
}
