package com.texora.secops.ftp.admin.service;

import com.texora.secops.ftp.audit.FtpAuditTrail;
import com.texora.secops.ftp.config.FtpProperties;
import com.texora.secops.ftp.domain.ConfigEntry;
import com.texora.secops.ftp.dto.ConfigEntryRequest;
import com.texora.secops.ftp.dto.ConfigEntryResponse;
import com.texora.secops.ftp.exception.UnprocessableException;
import com.texora.secops.ftp.repository.ConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped, NON-SECRET operational settings. Only allow-listed keys are accepted, so this store can never be
 * used to smuggle credentials into the database. Safe defaults apply when a key is unset or unparseable
 * (inspection settings default to the STRICT value - fail closed).
 */
@Service
public class ServiceConfigService {

    public static final String RETRY_MAX_ATTEMPTS = "retry.max-attempts";
    public static final String INSPECTION_DEFAULT_REQUIRED = "inspection.default-required";
    public static final String INSPECTION_MANDATORY = "inspection.mandatory";
    public static final String MAX_FILE_BYTES = "transfer.max-file-bytes";

    private static final long MAX_ALLOWED_FILE_BYTES = 53_687_091_200L;
    private static final List<String> KEYS = List.of(RETRY_MAX_ATTEMPTS, INSPECTION_DEFAULT_REQUIRED,
            INSPECTION_MANDATORY, MAX_FILE_BYTES);

    private final ConfigRepository repository;
    private final FtpAuditTrail audit;
    private final FtpProperties props;

    public ServiceConfigService(ConfigRepository repository, FtpAuditTrail audit, FtpProperties props) {
        this.repository = repository;
        this.audit = audit;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<ConfigEntryResponse> list(UUID tenantId) {
        List<ConfigEntryResponse> result = new ArrayList<>();
        for (String key : KEYS) {
            Optional<ConfigEntry> stored = repository.findByTenantIdAndConfigKey(tenantId, key);
            if (stored.isPresent()) {
                result.add(new ConfigEntryResponse(key, stored.get().getConfigValue(), false, stored.get().getUpdatedAt()));
            } else {
                result.add(new ConfigEntryResponse(key, defaultFor(key), true, null));
            }
        }
        return result;
    }

    @Transactional
    public ConfigEntryResponse upsert(UUID tenantId, UUID actorSubject, String actorId, ConfigEntryRequest request) {
        String key = request.getKey();
        String value = request.getValue().trim();
        if (!KEYS.contains(key)) {
            throw new UnprocessableException("CONFIG_KEY_UNKNOWN", "Unknown configuration key");
        }
        validate(key, value);
        Instant now = Instant.now();
        ConfigEntry entry = repository.findByTenantIdAndConfigKey(tenantId, key).orElse(null);
        String previous = null;
        if (entry == null) {
            entry = new ConfigEntry(UUID.randomUUID(), tenantId, key, value, actorSubject, now);
        } else {
            previous = entry.getConfigValue();
            entry.setConfigValue(value);
            entry.setUpdatedBy(actorSubject);
            entry.setUpdatedAt(now);
        }
        repository.save(entry);
        audit.configChanged(tenantId, actorId, entry.getId(),
                Map.<String, Object>of("key", key, "previous", previous == null ? "(default)" : previous, "new", value));
        return new ConfigEntryResponse(key, value, false, now);
    }

    @Transactional(readOnly = true)
    public int maxAttempts(UUID tenantId) {
        try {
            return Integer.parseInt(resolve(tenantId, RETRY_MAX_ATTEMPTS));
        } catch (NumberFormatException e) {
            return props.getTransfer().getDefaultMaxAttempts();
        }
    }

    @Transactional(readOnly = true)
    public long maxFileBytes(UUID tenantId) {
        try {
            return Long.parseLong(resolve(tenantId, MAX_FILE_BYTES));
        } catch (NumberFormatException e) {
            return props.getTransfer().getDefaultMaxFileBytes();
        }
    }

    /** Anything other than an explicit "false" means true (fail closed). */
    @Transactional(readOnly = true)
    public boolean inspectionDefaultRequired(UUID tenantId) {
        return !"false".equalsIgnoreCase(resolve(tenantId, INSPECTION_DEFAULT_REQUIRED));
    }

    @Transactional(readOnly = true)
    public boolean inspectionMandatory(UUID tenantId) {
        return !"false".equalsIgnoreCase(resolve(tenantId, INSPECTION_MANDATORY));
    }

    private String resolve(UUID tenantId, String key) {
        return repository.findByTenantIdAndConfigKey(tenantId, key).map(ConfigEntry::getConfigValue)
                .orElseGet(() -> defaultFor(key));
    }

    private String defaultFor(String key) {
        if (RETRY_MAX_ATTEMPTS.equals(key)) {
            return String.valueOf(props.getTransfer().getDefaultMaxAttempts());
        }
        if (MAX_FILE_BYTES.equals(key)) {
            return String.valueOf(props.getTransfer().getDefaultMaxFileBytes());
        }
        return "true";
    }

    private static void validate(String key, String value) {
        try {
            if (RETRY_MAX_ATTEMPTS.equals(key)) {
                int v = Integer.parseInt(value);
                if (v < 1 || v > 10) {
                    throw new UnprocessableException("CONFIG_VALUE_INVALID", "retry.max-attempts must be between 1 and 10");
                }
            } else if (MAX_FILE_BYTES.equals(key)) {
                long v = Long.parseLong(value);
                if (v < 1 || v > MAX_ALLOWED_FILE_BYTES) {
                    throw new UnprocessableException("CONFIG_VALUE_INVALID", "transfer.max-file-bytes is out of range");
                }
            } else if (!"true".equals(value) && !"false".equals(value)) {
                throw new UnprocessableException("CONFIG_VALUE_INVALID", "Value must be 'true' or 'false'");
            }
        } catch (NumberFormatException e) {
            throw new UnprocessableException("CONFIG_VALUE_INVALID", "Value must be a number");
        }
    }
}
