package com.texora.secops.dc.security;

import com.texora.secops.dc.exception.DcException;
import com.texora.secops.starter.domain.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * The ONLY class in DC that touches the shared {@code TenantContext}.
 *
 * <p>The tenant used for every query is the one in the validated SSO token ({@link CallerContext#getTenantId()}),
 * never a request parameter (B.5). If the shared starter has ALSO populated {@code TenantContext} for this
 * request, the two MUST agree, otherwise the request is denied.
 *
 * <p>UNVERIFIED ASSUMPTION: {@code TenantContext.getOrNull()} is a static accessor. If the real API differs,
 * only this one method needs to change. See README.
 */
@Component
public class TenantResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(TenantResolver.class);

    public void assertConsistentWithSharedContext(UUID claimsTenantId) {
        Object shared;
        try {
            shared = TenantContext.getOrNull();
        } catch (RuntimeException e) {
            // Shared context not populated for this request - the token claim remains the single source of truth.
            LOGGER.debug("Shared TenantContext not populated; relying on token claim");
            return;
        }
        if (shared instanceof Optional) {
            shared = ((Optional<?>) shared).orElse(null);
        }
        if (shared != null && !claimsTenantId.toString().equals(String.valueOf(shared))) {
            LOGGER.warn("Tenant mismatch between token claim and shared TenantContext - denying");
            throw DcException.forbidden("TENANT_MISMATCH", "Tenant context mismatch");
        }
    }
}
