package com.texora.secops.sec.policy.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sec_policy_evaluation_log", schema = "sec")
public class SecPolicyEvaluationLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "policy_version_id")
    private UUID policyVersionId;

    @Column(name = "caller_service", nullable = false, length = 64)
    private String callerService;

    @Column(name = "subject_type", length = 64)
    private String subjectType;

    @Column(name = "subject_id", length = 255)
    private String subjectId;

    @Column(name = "resource", nullable = false, length = 255)
    private String resource;

    @Column(name = "action", nullable = false, length = 128)
    private String action;

    @Column(name = "decision", nullable = false, length = 16)
    private String decision;

    @Column(name = "reason", nullable = false, length = 32)
    private String reason;

    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private Instant evaluatedAt;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Type(JsonBinaryType.class)
    @Column(name = "context", columnDefinition = "jsonb")
    private Map<String, Object> context;

    protected SecPolicyEvaluationLog() {
        // required by JPA
    }

    public SecPolicyEvaluationLog(UUID id, UUID tenantId, UUID policyVersionId,
                                   String callerService, String subjectType, String subjectId,
                                   String resource, String action, String decision, String reason,
                                   Integer latencyMs, Map<String, Object> context) {
        this.id = id;
        this.tenantId = tenantId;
        this.policyVersionId = policyVersionId;
        this.callerService = callerService;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.resource = resource;
        this.action = action;
        this.decision = decision;
        this.reason = reason;
        this.evaluatedAt = Instant.now();
        this.latencyMs = latencyMs;
        this.context = context;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getPolicyVersionId() { return policyVersionId; }
    public void setPolicyVersionId(UUID policyVersionId) { this.policyVersionId = policyVersionId; }

    public String getCallerService() { return callerService; }
    public void setCallerService(String callerService) { this.callerService = callerService; }

    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }

    public String getSubjectId() { return subjectId; }
    public void setSubjectId(String subjectId) { this.subjectId = subjectId; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(Instant evaluatedAt) { this.evaluatedAt = evaluatedAt; }

    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }

    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SecPolicyEvaluationLog)) return false;
        SecPolicyEvaluationLog that = (SecPolicyEvaluationLog) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "SecPolicyEvaluationLog{id=" + id + ", caller='" + callerService
                + "', decision='" + decision + "', reason='" + reason + "'}";
    }
}
