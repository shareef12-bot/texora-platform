package com.texora.secops.sec.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sec_policy", schema = "sec")
public class SecPolicy {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "subject_type", nullable = false, length = 64)
    private String subjectType;

    @Column(name = "resource", nullable = false, length = 255)
    private String resource;

    @Column(name = "action", nullable = false, length = 128)
    private String action;

    @Column(name = "owner_team", nullable = false, length = 128)
    private String ownerTeam;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SecPolicy() {
        // required by JPA
    }

    public SecPolicy(UUID id, UUID tenantId, String name, String subjectType,
                     String resource, String action, String ownerTeam) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.subjectType = subjectType;
        this.resource = resource;
        this.action = action;
        this.ownerTeam = ownerTeam;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getOwnerTeam() { return ownerTeam; }
    public void setOwnerTeam(String ownerTeam) { this.ownerTeam = ownerTeam; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SecPolicy)) return false;
        SecPolicy that = (SecPolicy) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "SecPolicy{id=" + id + ", name='" + name + "', resource='" + resource + "', action='" + action + "'}";
    }
}
