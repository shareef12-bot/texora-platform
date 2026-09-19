package com.texora.secops.sec.stub.config;

import com.texora.secops.sec.stub.dto.StubDecisionRule;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the SEC stub.
 *
 * <p>Example {@code sec-stub-rules.yml}:
 * <pre>
 * texora:
 *   sec:
 *     stub:
 *       default-decision: DENY   # global fallback when no rule matches
 *       rules:
 *         - resourceType: VPN_GATEWAY
 *           action: CONNECT
 *           decision: PERMIT
 *           reason: "Dev: VPN connect always permitted"
 *           policyId: "aaaaaaaa-0000-0000-0000-000000000001"
 *         - resourceType: LDAP_USER
 *           action: DELETE
 *           decision: DENY
 *           reason: "Dev: LDAP user deletion always denied in stub"
 * </pre>
 */
@ConfigurationProperties(prefix = "texora.sec.stub")
public class StubProperties {

    /** Global default decision when no rule matches. PERMIT or DENY. Defaults to DENY (fail-closed). */
    private String defaultDecision = "DENY";

    /** Ordered list of matching rules. First match wins. */
    private List<StubDecisionRule> rules = new ArrayList<>();

    public String getDefaultDecision()          { return defaultDecision; }
    public void setDefaultDecision(String v)    { this.defaultDecision = v; }

    public List<StubDecisionRule> getRules()              { return rules; }
    public void setRules(List<StubDecisionRule> rules)    { this.rules = rules; }
}
