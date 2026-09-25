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
@Table(name = "sec_policy_version", schema = "sec")
public class SecPolicyVersion {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_RETIRED = "RETIRED";

    public static final String EFFECT_ALLOW = "ALLOW";
    public static final String EFFECT_DENY = "DENY";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "policy_id", nullable = false)
    private UUID policyId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "effect", nullable = false, length = 16)
    private String effect;

    @Type(JsonBinaryType.class)
    @Column(name = "conditions", columnDefinition = "jsonb")
    private Map<String, Object> conditions;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "authored_by", nullable = false)
    private UUID authoredBy;

    @Column(name = "authored_at", nullable = false, updatable = false)
    private Instant authoredAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    protected SecPolicyVersion() {
        // required by JPA
    }

    public SecPolicyVersion(UUID id, UUID policyId, UUID tenantId, Integer versionNumber,
                            String effect, Map<String, Object> conditions, UUID authoredBy) {
        this.id = id;
        this.policyId = policyId;
        this.tenantId = tenantId;
        this.versionNumber = versionNumber;
        this.effect = effect;
        this.conditions = conditions;
        this.status = STATUS_DRAFT;
        this.authoredBy = authoredBy;
        this.authoredAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getPolicyId() { return policyId; }
    public void setPolicyId(UUID policyId) { this.policyId = policyId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }

    public String getEffect() { return effect; }
    public void setEffect(String effect) { this.effect = effect; }

    public Map<String, Object> getConditions() { return conditions; }
    public void setConditions(Map<String, Object> conditions) { this.conditions = conditions; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public UUID getAuthoredBy() { return authoredBy; }
    public void setAuthoredBy(UUID authoredBy) { this.authoredBy = authoredBy; }

    public Instant getAuthoredAt() { return authoredAt; }
    public void setAuthoredAt(Instant authoredAt) { this.authoredAt = authoredAt; }

    public Instant getActivatedAt() { return activatedAt; }
    public void setActivatedAt(Instant activatedAt) { this.activatedAt = activatedAt; }

    public Instant getRetiredAt() { return retiredAt; }
    public void setRetiredAt(Instant retiredAt) { this.retiredAt = retiredAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SecPolicyVersion)) return false;
        SecPolicyVersion that = (SecPolicyVersion) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SecPolicyVersion{id=" + id + ", policyId=" + policyId
                + ", version=" + versionNumber + ", effect='" + effect
                + "', status='" + status + "'}";
    }
}
