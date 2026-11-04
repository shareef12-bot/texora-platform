package com.texora.secops.dc;

import com.texora.secops.dc.infra.TimeSource;
import com.texora.secops.dc.security.CallerContext;
import com.texora.secops.dc.security.TenantScopedLookup;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.UUID;

/** Shared test helpers (plain Mockito; no Spring context). */
public final class TestSupport {

    public static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

    private TestSupport() {
    }

    public static CallerContext caller(UUID tenant, UUID subject, String... roles) {
        return new CallerContext(tenant, subject, null, new HashSet<String>(Arrays.asList(roles)));
    }

    public static TimeSource fixedTime() {
        TimeSource ts = Mockito.mock(TimeSource.class);
        Mockito.when(ts.now()).thenReturn(NOW);
        return ts;
    }

    /** A lookup that returns the value if present, else throws 404 (cross-tenant probing is covered by the IT). */
    public static TenantScopedLookup passThroughLookup() {
        TenantScopedLookup lookup = Mockito.mock(TenantScopedLookup.class);
        Mockito.when(lookup.require(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenAnswer(inv -> {
            Optional<?> found = inv.getArgument(0);
            if (found.isPresent()) {
                return found.get();
            }
            throw com.texora.secops.dc.exception.DcException.notFound("not found");
        });
        return lookup;
    }
}
