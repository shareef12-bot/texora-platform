package com.texora.secops.cgi.config.service;

import com.texora.secops.cgi.audit.service.AuditRecord;
import com.texora.secops.cgi.audit.service.CgiAuditWriter;
import com.texora.secops.cgi.common.CgiApiException;
import com.texora.secops.cgi.common.PageResponse;
import com.texora.secops.cgi.config.domain.ConfigEntry;
import com.texora.secops.cgi.config.dto.ConfigRequest;
import com.texora.secops.cgi.config.dto.ConfigResponse;
import com.texora.secops.cgi.config.props.ExecutionProperties;
import com.texora.secops.cgi.repository.ConfigEntryRepository;
import com.texora.secops.cgi.security.CallerContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Whitelisted, NON-SECRET tenant settings (CGI-F-004). Unknown keys are rejected, so a secret can never be
 * parked here. Both keys can only make the gateway STRICTER:
 * <ul>
 *   <li>{@code redaction.extra-sensitive-keys} - adds parameter names to redact (never removes built-ins)</li>
 *   <li>{@code gateway.max-request-body-bytes} - lowers (never raises) the platform body cap</li>
 * </ul>
 * Reads on the request path are cached for 15 s per pod; a read failure propagates (fail closed - a missing
 * redaction rule must reject the request rather than be silently skipped).
 */
@Service
public class TenantConfigService {

    public static final String KEY_EXTRA_SENSITIVE = "redaction.extra-sensitive-keys";
    public static final String KEY_MAX_BODY = "gateway.max-request-body-bytes";
    static final Set<String> ALLOWED_KEYS = Set.of(KEY_EXTRA_SENSITIVE, KEY_MAX_BODY);

    private static final long CACHE_TTL_MILLIS = 15_000L;
    private static final Pattern PARAM_NAME = Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");

    private record Cached(long loadedAtMillis, Map<String, String> values) {
    }

    private final ConfigEntryRepository repository;
    private final ExecutionProperties executionProperties;
    private final CgiAuditWriter auditWriter;
    private final Clock clock;
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    public TenantConfigService(ConfigEntryRepository repository, ExecutionProperties executionProperties,
                               CgiAuditWriter auditWriter, Clock clock) {
        this.repository = repository;
        this.executionProperties = executionProperties;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    /** Extra parameter names to redact for this tenant (empty set if none configured). */
    public Set<String> extraSensitiveKeys(UUID tenantId) {
        String raw = load(tenantId).get(KEY_EXTRA_SENSITIVE);
        Set<String> out = new HashSet<>();
        if (raw != null && !raw.isBlank()) {
            for (String part : raw.split(",")) {
                if (!part.isBlank()) {
                    out.add(part.trim());
                }
            }
        }
        return out;
    }

    /** Effective request-body cap: the tenant value if it is lower than the platform cap, else the platform cap. */
    public long maxRequestBodyBytes(UUID tenantId) {
        long platform = executionProperties.getMaxRequestBodyBytes();
        String raw = load(tenantId).get(KEY_MAX_BODY);
        if (raw == null) {
            return platform;
        }
        try {
            long tenant = Long.parseLong(raw.trim());
            return tenant > 0 ? Math.min(tenant, platform) : platform;
        } catch (NumberFormatException e) {
            return platform;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<ConfigResponse> list(UUID tenantId) {
        List<ConfigResponse> items = new ArrayList<>();
        for (ConfigEntry e : repository.findByTenantId(tenantId)) {
            items.add(toResponse(e));
        }
        return new PageResponse<>(items, 0, items.size(), items.size(), 1);
    }

    @Transactional
    public ConfigResponse upsert(CallerContext caller, ConfigRequest request) {
        String key = request.getKey() == null ? "" : request.getKey().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_KEYS.contains(key)) {
            throw CgiApiException.unprocessable("UNKNOWN_CONFIG_KEY", "Configuration key is not recognised");
        }
        String value = validate(key, request.getValue());
        Instant now = clock.instant();
        ConfigEntry entry = repository.findByTenantIdAndConfigKey(caller.getTenantId(), key)
                .orElseGet(() -> new ConfigEntry(UUID.randomUUID(), caller.getTenantId(), key, value, caller.getSubject(), now));
        entry.setConfigValue(value);
        entry.setUpdatedBy(caller.getSubject());
        entry.setUpdatedAt(now);
        repository.save(entry);
        cache.remove(caller.getTenantId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("key", key);
        auditWriter.recordInTransaction(AuditRecord.of(caller.getTenantId(), caller.getSubject(), "CONFIG_UPDATED",
                "CONFIG", key, CgiAuditWriter.OUTCOME_SUCCESS, payload));
        return toResponse(entry);
    }

    private String validate(String key, String value) {
        String v = value == null ? "" : value.trim();
        if (KEY_MAX_BODY.equals(key)) {
            try {
                long n = Long.parseLong(v);
                if (n <= 0 || n > executionProperties.getMaxRequestBodyBytes()) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                throw CgiApiException.unprocessable("INVALID_CONFIG_VALUE", "Value must be an integer between 1 and the platform cap");
            }
            return v;
        }
        // KEY_EXTRA_SENSITIVE
        String[] parts = v.isEmpty() ? new String[0] : v.split(",");
        if (parts.length > 50) {
            throw CgiApiException.unprocessable("INVALID_CONFIG_VALUE", "At most 50 parameter names");
        }
        List<String> cleaned = new ArrayList<>();
        for (String p : parts) {
            String name = p.trim();
            if (!PARAM_NAME.matcher(name).matches()) {
                throw CgiApiException.unprocessable("INVALID_CONFIG_VALUE", "Parameter names must match [A-Za-z0-9_.-]{1,64}");
            }
            cleaned.add(name);
        }
        return String.join(",", cleaned);
    }

    private Map<String, String> load(UUID tenantId) {
        long now = clock.millis();
        Cached cached = cache.get(tenantId);
        if (cached != null && now - cached.loadedAtMillis() < CACHE_TTL_MILLIS) {
            return cached.values();
        }
        Map<String, String> values = new HashMap<>();
        for (ConfigEntry e : repository.findByTenantId(tenantId)) {
            values.put(e.getConfigKey(), e.getConfigValue());
        }
        cache.put(tenantId, new Cached(now, values));
        return values;
    }

    private static ConfigResponse toResponse(ConfigEntry e) {
        return new ConfigResponse(e.getConfigKey(), e.getConfigValue(), e.getUpdatedBy(), e.getUpdatedAt());
    }
}
