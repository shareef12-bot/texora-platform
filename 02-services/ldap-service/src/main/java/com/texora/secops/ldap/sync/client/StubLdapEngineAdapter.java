package com.texora.secops.ldap.sync.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * =====================================================================
 * ⚠️  STUB IMPLEMENTATION — NOT PRODUCTION-READY ⚠️
 * =====================================================================
 *
 * <p>This is a LOCAL STUB for {@link LdapEngineAdapter}. It is active because
 * the LDAP engine product has not yet been selected (see ADR-D5).
 *
 * <p><b>Every call this class receives is logged at WARN level.</b> If you see
 * WARN lines from this class in production logs, it means no real LDAP engine
 * is wired — stop and investigate immediately.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>{@link #ping()} returns {@code false} — the engine is not reachable.</li>
 *   <li>{@link #search} returns an empty list.</li>
 *   <li>{@link #addOrReplace}, {@link #modify}, {@link #delete} are no-ops that log loudly.</li>
 *   <li>{@link #applySchemaChange} is a no-op that logs loudly.</li>
 *   <li>{@link #isValidFilter} and {@link #isValidSyntax} return {@code true}
 *       so the approval workflow can be exercised without a real engine.</li>
 * </ul>
 *
 * <p><b>To replace this stub:</b>
 * <ol>
 *   <li>Select the LDAP engine product (update ADR-D5).</li>
 *   <li>Add the vendor's Java client library dependency to {@code pom.xml}
 *       (ONLY inside the adapter package — never imported elsewhere).</li>
 *   <li>Create {@code RealLdapEngineAdapter implements LdapEngineAdapter} in this package.</li>
 *   <li>Annotate it with {@code @Primary} or remove the
 *       {@code @ConditionalOnMissingBean} from this class.</li>
 *   <li>Delete this stub (or keep it as a test double).</li>
 * </ol>
 *
 * @see LdapEngineAdapter
 */
@Component
@ConditionalOnMissingBean(name = "realLdapEngineAdapter")
public class StubLdapEngineAdapter implements LdapEngineAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(StubLdapEngineAdapter.class);

    private static final String STUB_WARNING =
            "⚠ StubLdapEngineAdapter is active — no real LDAP engine is connected. " +
            "This call has been silently ignored. See ADR-D5 for resolution.";

    public StubLdapEngineAdapter() {
        LOGGER.warn("=================================================================");
        LOGGER.warn("⚠  StubLdapEngineAdapter initialised. LDAP engine NOT connected.");
        LOGGER.warn("   Every adapter call will be a no-op and logged at WARN level.");
        LOGGER.warn("   Replace with RealLdapEngineAdapter once engine is selected.");
        LOGGER.warn("   See ADR-D5 for the engine selection decision.");
        LOGGER.warn("=================================================================");
    }

    @Override
    public boolean ping() {
        LOGGER.warn("STUB [ping] called — {}", STUB_WARNING);
        return false;
    }

    @Override
    public List<Map<String, List<String>>> search(String tenantBaseDn, String filter, List<String> attributes) {
        LOGGER.warn("STUB [search] called with baseDn='{}', filter='{}' — {}", tenantBaseDn, filter, STUB_WARNING);
        return List.of();
    }

    @Override
    public void addOrReplace(String entryDn, Map<String, List<String>> attributes) {
        LOGGER.warn("STUB [addOrReplace] called for entryDn='{}' — {}", entryDn, STUB_WARNING);
    }

    @Override
    public void modify(String entryDn, Map<String, List<String>> modifications) {
        LOGGER.warn("STUB [modify] called for entryDn='{}' — {}", entryDn, STUB_WARNING);
    }

    @Override
    public void delete(String entryDn) {
        LOGGER.warn("STUB [delete] called for entryDn='{}' — {}", entryDn, STUB_WARNING);
    }

    @Override
    public void applySchemaChange(String objectClass, Map<String, String> attributes, List<String> requiredAttrs) {
        LOGGER.warn("STUB [applySchemaChange] called for objectClass='{}' — {}", objectClass, STUB_WARNING);
    }

    @Override
    public boolean isValidFilter(String filter) {
        LOGGER.warn("STUB [isValidFilter] called for filter='{}' — returning true (stub mode)", filter);
        // In stub mode, accept all filters so the workflow can be exercised
        return filter != null && !filter.isBlank();
    }

    @Override
    public boolean isValidSyntax(String syntaxOid) {
        LOGGER.warn("STUB [isValidSyntax] called for syntaxOid='{}' — returning true (stub mode)", syntaxOid);
        // In stub mode, accept all OIDs so the workflow can be exercised
        return syntaxOid != null && !syntaxOid.isBlank();
    }
}
