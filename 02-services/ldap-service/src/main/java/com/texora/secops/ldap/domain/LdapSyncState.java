package com.texora.secops.ldap.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Per-entry reconciliation state tracking the sync status between DC and the LDAP engine.
 * Lives in {@code ldap.ldap_sync_state}.
 *
 * <p>Composite PK: {@code (entry_dn, tenant_id)}.
 * Status drives retry/backoff logic in {@link com.texora.secops.ldap.sync.service.DirectoryReconciliationService}.
 */
@Entity
@Table(name = "ldap_sync_state", schema = "ldap")
@IdClass(LdapSyncState.SyncStateId.class)
public class LdapSyncState {

    public enum Status {
        IN_SYNC, PENDING, CONFLICT, FAILED
    }

    /** Composite primary key type for JPA. */
    public static class SyncStateId implements Serializable {

        private String entryDn;
        private UUID tenantId;

        public SyncStateId() {
        }

        public SyncStateId(String entryDn, UUID tenantId) {
            this.entryDn = entryDn;
            this.tenantId = tenantId;
        }

        public String getEntryDn() {
            return entryDn;
        }

        public void setEntryDn(String entryDn) {
            this.entryDn = entryDn;
        }

        public UUID getTenantId() {
            return tenantId;
        }

        public void setTenantId(UUID tenantId) {
            this.tenantId = tenantId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof SyncStateId)) {
                return false;
            }
            SyncStateId that = (SyncStateId) other;
            return Objects.equals(entryDn, that.entryDn) && Objects.equals(tenantId, that.tenantId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entryDn, tenantId);
        }
    }

    @Id
    @Column(name = "entry_dn", nullable = false, length = 512)
    private String entryDn;

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "dc_source_id", nullable = false)
    private UUID dcSourceId;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "last_error")
    private String lastError;

    protected LdapSyncState() {
        // required by JPA
    }

    public LdapSyncState(String entryDn, UUID tenantId, UUID dcSourceId, Status status) {
        this.entryDn = entryDn;
        this.tenantId = tenantId;
        this.dcSourceId = dcSourceId;
        this.status = status.name();
        this.retryCount = 0;
    }

    /** Convenience constructor for new entries without a known DC source yet. */
    public LdapSyncState(String entryDn, UUID tenantId) {
        this.entryDn = entryDn;
        this.tenantId = tenantId;
        this.status = Status.PENDING.name();
        this.retryCount = 0;
    }

    public String getEntryDn() {
        return entryDn;
    }

    public void setEntryDn(String entryDn) {
        this.entryDn = entryDn;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getDcSourceId() {
        return dcSourceId;
    }

    public void setDcSourceId(UUID dcSourceId) {
        this.dcSourceId = dcSourceId;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Status getStatusEnum() {
        return Status.valueOf(this.status);
    }

    public void setStatusEnum(Status status) {
        this.status = status.name();
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LdapSyncState)) {
            return false;
        }
        LdapSyncState that = (LdapSyncState) other;
        return Objects.equals(entryDn, that.entryDn) && Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entryDn, tenantId);
    }

    @Override
    public String toString() {
        return "LdapSyncState{entryDn='" + entryDn + "', status='" + status + "', retryCount=" + retryCount + "}";
    }
}
