package com.texora.secops.ldap.policy.controller;

import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.ldap.security.PolicyDecisionClient;
import com.texora.secops.ldap.security.RbacInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/policy")
public class PolicyController {

    private final PolicyDecisionClient policyDecisionClient;

    public PolicyController(PolicyDecisionClient policyDecisionClient) {
        this.policyDecisionClient = policyDecisionClient;
    }

    /**
     * Checks whether the calling principal is permitted to perform the requested action.
     * Fail-closed: any error from the policy engine returns {@code permitted=false}.
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> check(@RequestParam String action,
                                                      HttpServletRequest request) {
        TokenClaims claims = (TokenClaims) request.getAttribute(RbacInterceptor.CLAIMS_ATTR);
        boolean permitted = policyDecisionClient.isPermitted(String.valueOf(claims.getSubject()), action, String.valueOf(claims.getTenantId()));
        return ResponseEntity.ok(Map.of(
                "principal", claims.getSubject().toString(),
                "action", action,
                "permitted", permitted
        ));
    }
}
