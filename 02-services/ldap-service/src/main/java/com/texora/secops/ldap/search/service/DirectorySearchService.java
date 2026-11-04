package com.texora.secops.ldap.search.service;

import com.texora.secops.ldap.audit.LdapAuditHelper;
import com.texora.secops.ldap.domain.LdapAuditEvent;
import com.texora.secops.ldap.search.dto.DirectorySearchRequest;
import com.texora.secops.ldap.search.dto.DirectorySearchResponse;
import com.texora.secops.ldap.security.PolicyDecisionClient;
import com.texora.secops.ldap.sync.client.LdapEngineAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles directory search requests through the {@link LdapEngineAdapter}.
 *
 * <p><b>Fail-closed policy:</b> if the LDAP filter fails validation or the engine
 * is unreachable, the search is denied and audited. Sensitive attribute names
 * (e.g. {@code userPassword}) are stripped from responses.
 */
@Service
public class DirectorySearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DirectorySearchService.class);

    /** Attributes that must never appear in search responses (fail-closed). */
    private static final List<String> BLOCKED_ATTRIBUTES = List.of(
            "userPassword", "unicodePwd", "ntPwdHistory", "lmPwdHistory",
            "pwdHistory", "sambaNTPassword", "sambaLMPassword"
    );

    private final LdapEngineAdapter ldapEngineAdapter;
    private final PolicyDecisionClient policyDecisionClient;
    private final LdapAuditHelper auditHelper;

    public DirectorySearchService(LdapEngineAdapter ldapEngineAdapter,
                                   PolicyDecisionClient policyDecisionClient,
                                   LdapAuditHelper auditHelper) {
        this.ldapEngineAdapter = ldapEngineAdapter;
        this.policyDecisionClient = policyDecisionClient;
        this.auditHelper = auditHelper;
    }

    public DirectorySearchResponse search(DirectorySearchRequest request,
                                           UUID actorId, UUID tenantId) {
        if (request.getBaseDn() == null || request.getBaseDn().isBlank()) {
            throw new IllegalArgumentException("baseDn must not be blank");
        }
        if (request.getFilter() == null || request.getFilter().isBlank()) {
            throw new IllegalArgumentException("filter must not be blank");
        }

        // Validate filter syntax before sending to engine (fail-closed)
        if (!ldapEngineAdapter.isValidFilter(request.getFilter())) {
            auditHelper.record(tenantId, actorId, "DIRECTORY_SEARCH_DENIED",
                    "DirectorySearch", request.getBaseDn(), LdapAuditEvent.Outcome.DENIED, null);
            throw new IllegalArgumentException("Invalid LDAP filter syntax: " + request.getFilter());
        }

        // Policy check (fail-closed)
        boolean permitted = policyDecisionClient.isPermitted(String.valueOf(actorId), "ldap.search.read", String.valueOf(tenantId));
        if (!permitted) {
            auditHelper.record(tenantId, actorId, "DIRECTORY_SEARCH_DENIED_BY_POLICY",
                    "DirectorySearch", request.getBaseDn(), LdapAuditEvent.Outcome.DENIED, null);
            throw new IllegalStateException("Search denied by policy engine");
        }

        List<Map<String, List<String>>> raw = ldapEngineAdapter.search(
                request.getBaseDn(), request.getFilter(), request.getAttributes());

        List<DirectorySearchResponse.Entry> entries = raw.stream()
                .map(attrs -> {
                    DirectorySearchResponse.Entry entry = new DirectorySearchResponse.Entry();
                    // Strip blocked attributes — fail-closed
                    Map<String, List<String>> safe = attrs.entrySet().stream()
                            .filter(e -> {
                                boolean blocked = BLOCKED_ATTRIBUTES.contains(e.getKey());
                                if (blocked) {
                                    LOGGER.warn("Stripping blocked attribute '{}' from search response for tenant {}",
                                            e.getKey(), tenantId);
                                }
                                return !blocked;
                            })
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                    // dn is conventionally in the map under "dn"
                    String dn = safe.containsKey("dn")
                            ? safe.get("dn").stream().findFirst().orElse("")
                            : "";
                    entry.setDn(dn);
                    entry.setAttributes(safe);
                    return entry;
                })
                .collect(Collectors.toList());

        auditHelper.record(tenantId, actorId, "DIRECTORY_SEARCH",
                "DirectorySearch", request.getBaseDn(), LdapAuditEvent.Outcome.SUCCESS, null);

        DirectorySearchResponse response = new DirectorySearchResponse();
        response.setBaseDn(request.getBaseDn());
        response.setFilter(request.getFilter());
        response.setCount(entries.size());
        response.setEntries(entries);
        return response;
    }
}
