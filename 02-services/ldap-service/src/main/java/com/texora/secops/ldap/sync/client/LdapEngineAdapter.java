package com.texora.secops.ldap.sync.client;

import java.util.List;
import java.util.Map;

/**
 * =====================================================================
 * ADAPTER INTERFACE — THE ONLY PLACE PERMITTED TO SPEAK THE LDAP WIRE PROTOCOL
 * =====================================================================
 *
 * <p>Every interaction with the underlying LDAP directory engine MUST go through
 * this interface. No other class in {@code ldap-service} may import the LDAP
 * client library directly. This is the adapter rule (Shared Standards §B.4)
 * and the single most important structural rule for this service.
 *
 * <h2>Engine selection (ADR-D5)</h2>
 * <p>The LDAP engine product has not yet been selected. The real implementation
 * of this interface will be written once the product is chosen (e.g. a managed
 * directory service or a standards-based open-source LDAP server). Until then,
 * {@link StubLdapEngineAdapter} is the active bean and every call it receives is
 * logged at {@code WARN} level so no production traffic can silently pass through
 * a stub.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>All methods fail closed: if the engine is unreachable or the answer is
 *       uncertain, throw {@link LdapEngineException} — callers must treat this
 *       as a DENY or leave the pending change in PENDING state.</li>
 *   <li>No password or credential material is passed through or logged.</li>
 *   <li>The adapter enforces TLS; implementations must reject non-TLS connections.</li>
 * </ul>
 *
 * @see StubLdapEngineAdapter
 */
public interface LdapEngineAdapter {

    /**
     * Tests connectivity to the engine. Returns {@code true} if the engine is
     * reachable and the service bind succeeds.
     *
     * <p><b>Fail closed:</b> any exception MUST be thrown as {@link LdapEngineException},
     * never silently swallowed.
     */
    boolean ping();

    /**
     * Searches the directory for entries matching the given LDAP filter.
     *
     * @param tenantBaseDn the base DN scoped to the tenant
     * @param filter       LDAP filter string (e.g. {@code (uid=jsmith)})
     * @param attributes   attribute names to return; empty means return all
     * @return list of entries, each as a map of attribute name → values
     * @throws LdapEngineException if the engine is unreachable or returns an error
     */
    List<Map<String, List<String>>> search(String tenantBaseDn, String filter, List<String> attributes);

    /**
     * Creates or replaces a directory entry.
     *
     * @param entryDn    distinguished name of the entry
     * @param attributes attribute map (name → values)
     * @throws LdapEngineException if the engine is unreachable or rejects the operation
     */
    void addOrReplace(String entryDn, Map<String, List<String>> attributes);

    /**
     * Modifies specific attributes of an existing directory entry.
     *
     * @param entryDn       distinguished name of the entry
     * @param modifications attribute name → new values (empty list means delete attribute)
     * @throws LdapEngineException if the engine is unreachable or rejects the modification
     */
    void modify(String entryDn, Map<String, List<String>> modifications);

    /**
     * Deletes a directory entry.
     *
     * @param entryDn distinguished name of the entry to delete
     * @throws LdapEngineException if the engine is unreachable or the entry does not exist
     */
    void delete(String entryDn);

    /**
     * Applies a schema object-class definition to the LDAP engine.
     * Called only after APPROVED status is confirmed by {@link com.texora.secops.ldap.schema.service.SchemaApprovalService}.
     *
     * @param objectClass   LDAP object-class name
     * @param attributes    attribute definitions (name → syntax OID)
     * @param requiredAttrs names of required attributes
     * @throws LdapEngineException if the engine is unreachable or rejects the schema
     */
    void applySchemaChange(String objectClass,
                           Map<String, String> attributes,
                           List<String> requiredAttrs);

    /**
     * Validates a proposed LDAP filter string.
     *
     * @param filter LDAP filter to validate
     * @return {@code true} if the filter is syntactically valid
     */
    boolean isValidFilter(String filter);

    /**
     * Validates a proposed LDAP syntax OID.
     *
     * @param syntaxOid the OID string (e.g. {@code 1.3.6.1.4.1.1466.115.121.1.15})
     * @return {@code true} if the OID is recognized by the engine
     */
    boolean isValidSyntax(String syntaxOid);
}
