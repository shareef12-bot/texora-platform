package com.texora.secops.siem.correlation.controller;

import com.texora.secops.siem.correlation.service.RuleApprovalService;
import com.texora.secops.siem.domain.SiemCorrelationRule;
import com.texora.secops.siem.domain.SiemRuleChangeAudit;
import com.texora.secops.siem.dto.correlation.CorrelationRuleRequest;
import com.texora.secops.siem.dto.correlation.CorrelationRuleResponse;
import com.texora.secops.siem.dto.correlation.RuleDecisionRequest;
import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.siem.security.RequiresRole;
import com.texora.secops.siem.security.CallerContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Thin controller (B.3): maps DTO &lt;-&gt; service calls only. All two-person-approval logic
 *  lives in RuleApprovalService. */
@RestController
public class CorrelationRuleController {

    private final RuleApprovalService ruleApprovalService;

    public CorrelationRuleController(RuleApprovalService ruleApprovalService) {
        this.ruleApprovalService = ruleApprovalService;
    }

    @GetMapping("/api/v1/correlation-rules")
    @RequiresRole("siem.rule.read")
    public List<CorrelationRuleResponse> list() {
        return ruleApprovalService.list(CallerContext.currentTenantId()).stream()
                .map(CorrelationRuleResponse::from).toList();
    }

    @GetMapping("/api/v1/correlation-rules/{id}")
    @RequiresRole("siem.rule.read")
    public CorrelationRuleResponse get(@PathVariable UUID id) {
        return CorrelationRuleResponse.from(ruleApprovalService.get(id, CallerContext.currentTenantId()));
    }

    @GetMapping("/api/v1/correlation-rules/{id}/history")
    @RequiresRole("siem.rule.read")
    public List<SiemRuleChangeAudit> history(@PathVariable UUID id) {
        return ruleApprovalService.changeHistory(id, CallerContext.currentTenantId());
    }

    @PostMapping("/api/v1/correlation-rules")
    @RequiresRole("siem.rule.write")
    public ResponseEntity<CorrelationRuleResponse> submit(@Valid @RequestBody CorrelationRuleRequest request) {
        TokenClaims caller = CallerContext.get();
        SiemCorrelationRule rule = request.getTargetRuleId() != null
                ? ruleApprovalService.submitDisableRequest(caller.getTenantId(), caller.getSubject(), request.getTargetRuleId())
                : ruleApprovalService.submitNewRule(caller.getTenantId(), caller.getSubject(), request.getName(), request.getRuleDefinition());
        return ResponseEntity.status(HttpStatus.CREATED).body(CorrelationRuleResponse.from(rule));
    }

    @PostMapping("/api/v1/correlation-rules/{id}/decision")
    @RequiresRole("siem.rule.approve")
    public CorrelationRuleResponse decide(@PathVariable UUID id, @Valid @RequestBody RuleDecisionRequest request) {
        TokenClaims caller = CallerContext.get();
        boolean approve = request.getDecision() == RuleDecisionRequest.Decision.APPROVE;
        SiemCorrelationRule rule = ruleApprovalService.decide(caller.getTenantId(), caller.getSubject(), id, approve, request.getNotes());
        return CorrelationRuleResponse.from(rule);
    }
}
