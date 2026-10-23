package com.texora.secops.ldap.schema.controller;

import com.texora.secops.iam.token.TokenClaims;
import com.texora.secops.ldap.schema.dto.SchemaDecisionRequest;
import com.texora.secops.ldap.schema.dto.SchemaDefinitionRequest;
import com.texora.secops.ldap.schema.dto.SchemaDefinitionResponse;
import com.texora.secops.ldap.schema.service.SchemaApprovalService;
import com.texora.secops.ldap.security.RbacInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schema")
public class SchemaController {

    private final SchemaApprovalService schemaApprovalService;

    public SchemaController(SchemaApprovalService schemaApprovalService) {
        this.schemaApprovalService = schemaApprovalService;
    }

    @GetMapping("/attribute-management")
    public ResponseEntity<List<SchemaDefinitionResponse>> list(HttpServletRequest request) {
        TokenClaims claims = (TokenClaims) request.getAttribute(RbacInterceptor.CLAIMS_ATTR);
        return ResponseEntity.ok(schemaApprovalService.listDefinitions(claims.getTenantId()));
    }

    @PostMapping("/attribute-management")
    public ResponseEntity<SchemaDefinitionResponse> submit(@RequestBody SchemaDefinitionRequest body,
                                                            HttpServletRequest request) {
        TokenClaims claims = (TokenClaims) request.getAttribute(RbacInterceptor.CLAIMS_ATTR);
        SchemaDefinitionResponse resp = schemaApprovalService.submitDefinition(
                body, claims.getSubject(), claims.getTenantId());
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    @PostMapping("/{id}/submit-for-approval")
    public ResponseEntity<SchemaDefinitionResponse> submitForApproval(@PathVariable UUID id,
                                                                        HttpServletRequest request) {
        TokenClaims claims = (TokenClaims) request.getAttribute(RbacInterceptor.CLAIMS_ATTR);
        return ResponseEntity.ok(schemaApprovalService.submitForApproval(
                id, claims.getSubject(), claims.getTenantId()));
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<SchemaDefinitionResponse> decide(@PathVariable UUID id,
                                                            @RequestBody SchemaDecisionRequest body,
                                                            HttpServletRequest request) {
        TokenClaims claims = (TokenClaims) request.getAttribute(RbacInterceptor.CLAIMS_ATTR);
        return ResponseEntity.ok(schemaApprovalService.recordDecision(
                id, body, claims.getSubject(), claims.getTenantId()));
    }
}
