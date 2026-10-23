package com.texora.secops.dc.adapter;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ############################  LOCAL STUB - NOT REAL HRMS DATA  ############################
 *
 * <p>HRMS has no API or event stream yet (ADR D-2 open question). This stub serves a tiny fixed in-memory
 * employee list so the DC projection/sync code paths can be built and tested. It is tenant-agnostic and must
 * NEVER run in production: it refuses to start if an active profile is "prod" or "production".
 *
 * <p>Replace with a real {@link HrmsEmployeeAdapter} implementation once the HRMS team exposes an API/event feed.
 */
@Component
@ConditionalOnProperty(name = "dc.hrms.adapter", havingValue = "stub", matchIfMissing = true)
public class LocalStubHrmsEmployeeAdapter implements HrmsEmployeeAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalStubHrmsEmployeeAdapter.class);

    private final Environment environment;
    private final List<HrmsEmployee> fixtures = new ArrayList<HrmsEmployee>();

    public LocalStubHrmsEmployeeAdapter(Environment environment) {
        this.environment = environment;
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        fixtures.add(new HrmsEmployee("EMP-1001", "jsmith", "Jane Smith", "eng-platform", "ACTIVE", base));
        fixtures.add(new HrmsEmployee("EMP-1002", "rpatel", "Ravi Patel", "eng-platform", "ACTIVE", base));
        fixtures.add(new HrmsEmployee("EMP-1003", "mlopez", "Maria Lopez", "secops", "ACTIVE", base));
        fixtures.add(new HrmsEmployee("EMP-1004", "tformer", "Tom Former", "secops", "TERMINATED", base));
    }

    @PostConstruct
    void warnLoudly() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                throw new IllegalStateException(
                        "LocalStubHrmsEmployeeAdapter must not run in production. HRMS integration is not built yet (ADR D-2).");
            }
        }
        LOGGER.warn("################################################################################");
        LOGGER.warn("# HRMS ADAPTER IS A LOCAL STUB: employee data is FAKE, in-memory, tenant-agnostic. #");
        LOGGER.warn("# HRMS has no API/event stream yet (ADR D-2). Do NOT use this in production.       #");
        LOGGER.warn("################################################################################");
    }

    @Override
    public Optional<HrmsEmployee> fetchEmployee(UUID tenantId, String hrmsEmployeeId) {
        for (HrmsEmployee e : fixtures) {
            if (e.getHrmsEmployeeId().equals(hrmsEmployeeId)) {
                return Optional.of(copy(e));
            }
        }
        return Optional.empty();
    }

    @Override
    public List<HrmsEmployee> fetchChangedSince(UUID tenantId, Instant since) {
        List<HrmsEmployee> out = new ArrayList<HrmsEmployee>();
        for (HrmsEmployee e : fixtures) {
            if (since == null || e.getLastModified().isAfter(since)) {
                out.add(copy(e));
            }
        }
        return out;
    }

    private HrmsEmployee copy(HrmsEmployee e) {
        return new HrmsEmployee(e.getHrmsEmployeeId(), e.getUsername(), e.getDisplayName(), e.getOrgUnitId(),
                e.getStatus(), e.getLastModified());
    }
}
