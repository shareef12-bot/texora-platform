package com.texora.secops.starter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Base class for all JPA entities in the Texora SecOps platform.
 *
 * <p>Every concrete entity inherits:
 * <ul>
 *   <li>{@code tenant_id} — mandatory for multi-tenancy (standard B.5)</li>
 *   <li>{@code created_at} — set once on first persist</li>
 *   <li>{@code updated_at} — updated on every merge</li>
 * </ul>
 *
 * <p>Subclasses must declare their own {@code @Id} field. No Lombok — explicit
 * getters, setters, equals, hashCode, and toString are required (standard B.2).
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BaseEntity() {
        // required by JPA
    }

    protected BaseEntity(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId must not be null");
        }
        this.tenantId = tenantId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Subclasses must implement equals using their primary key.
     * Standard pattern: two entities of the same type are equal iff their IDs are equal.
     */
    @Override
    public abstract boolean equals(Object other);

    @Override
    public abstract int hashCode();

    /**
     * Helper: identity check + null/type check reusable in subclass equals().
     * Usage:
     * <pre>{@code
     *   if (!sameClassAs(other)) return false;
     *   MyEntity that = (MyEntity) other;
     *   return Objects.equals(id, that.id);
     * }</pre>
     */
    protected boolean sameClassAs(Object other) {
        if (other == null) {
            return false;
        }
        return this.getClass() == other.getClass();
    }

    protected static boolean nullSafeEquals(Object a, Object b) {
        return Objects.equals(a, b);
    }
}
