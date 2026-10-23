package com.texora.secops.cgi.security;

import com.texora.secops.cgi.security.dto.PolicyInfo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Lists the policies that govern gateway/route decisions (CGI-F-005). Read-only and local: the fixed
 * invariants are compiled into the service and cannot be switched off by configuration.
 * ASSUMPTION: no SEC "list policies" endpoint is called, because only /api/v1/policies/evaluate is confirmed.
 */
@Service
public class PolicyCatalogService {

    public List<PolicyInfo> listPolicies() {
        return List.of(
                new PolicyInfo("ROUTE_ALLOWLIST_ONLY",
                        "Only APPROVED route-allowlist entries are matched; anything else is 404 and no worker is spawned.", "FIXED_INVARIANT"),
                new PolicyInfo("WORKER_ISOLATION_DEFAULTS",
                        "Workers run sandboxed, non-root, with no network, scoped to the approved handler. Changing a setting needs two-person approval.", "FIXED_INVARIANT"),
                new PolicyInfo("FORCED_TIMEOUT_KILL",
                        "A worker that exceeds its limit is force-killed, recorded TIMED_OUT, and the caller receives 504.", "FIXED_INVARIANT"),
                new PolicyInfo("PARAMETER_REDACTION",
                        "Sensitive parameter values are stripped before anything is written to the request log or audit trail.", "FIXED_INVARIANT"),
                new PolicyInfo("AUDIT_NEVER_BLOCKS",
                        "Audit is written locally and durably first, then forwarded to Kafka asynchronously; Kafka/SIEM outages never block requests.", "FIXED_INVARIANT"),
                new PolicyInfo("TWO_PERSON_APPROVAL",
                        "A submitter can never approve their own route-allowlist or isolation change.", "FIXED_INVARIANT"),
                new PolicyInfo("SEC_ROUTE_INVOKE",
                        "Each gateway invocation (action cgi.route.invoke) is evaluated by the Security Server; any failure is a DENY.", "SEC_EVALUATED"));
    }
}
