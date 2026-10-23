-- Secure File Transfer: metadata store ONLY. File bytes live in staging / object storage, referenced by URI.
-- Every table carries tenant_id UUID NOT NULL (Shared Standards B.5). Child tables use composite
-- (id, tenant_id) foreign keys, so a cross-tenant reference is impossible at the database level.

CREATE SCHEMA IF NOT EXISTS ftp;

CREATE TABLE ftp.ftp_endpoint (
    id                    UUID          NOT NULL PRIMARY KEY,
    tenant_id             UUID          NOT NULL,
    host                  VARCHAR(253)  NOT NULL,
    port                  INTEGER       NOT NULL CHECK (port BETWEEN 1 AND 65535),
    direction             VARCHAR(16)   NOT NULL CHECK (direction IN ('SOURCE', 'DEST')),
    protocol              VARCHAR(16)   NOT NULL CHECK (protocol IN ('SFTP', 'FTPS')),
    base_path             VARCHAR(1024) NOT NULL DEFAULT '/',
    credential_ref        VARCHAR(255)  NOT NULL,   -- opaque SEC reference, NEVER the secret itself
    host_key_fingerprint  VARCHAR(255),             -- public SHA256 fingerprint (SFTP host-key pinning)
    allowlisted           BOOLEAN       NOT NULL DEFAULT FALSE,
    added_by              UUID          NOT NULL,
    added_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_ftp_endpoint_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_ftp_endpoint_target UNIQUE (tenant_id, host, port, direction, base_path)
);
CREATE INDEX ix_ftp_endpoint_tenant ON ftp.ftp_endpoint (tenant_id);

CREATE TABLE ftp.ftp_transfer_job (
    id                      UUID          NOT NULL PRIMARY KEY,
    tenant_id               UUID          NOT NULL,
    initiator_sso_subject   UUID          NOT NULL,
    source_endpoint_id      UUID          NOT NULL,
    dest_endpoint_id        UUID          NOT NULL,
    file_name               VARCHAR(255)  NOT NULL,
    require_inspection      BOOLEAN       NOT NULL,
    expected_sha256         VARCHAR(64),
    staging_uri             VARCHAR(1024),
    status                  VARCHAR(16)   NOT NULL
        CHECK (status IN ('SUBMITTED', 'IN_PROGRESS', 'RESUMED', 'COMPLETED', 'FAILED', 'QUARANTINED')),
    failure_reason          VARCHAR(255),
    created_at              TIMESTAMPTZ   NOT NULL,
    updated_at              TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_ftp_job_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT fk_ftp_job_source FOREIGN KEY (source_endpoint_id, tenant_id) REFERENCES ftp.ftp_endpoint (id, tenant_id),
    CONSTRAINT fk_ftp_job_dest   FOREIGN KEY (dest_endpoint_id, tenant_id)   REFERENCES ftp.ftp_endpoint (id, tenant_id)
);
CREATE INDEX ix_ftp_job_tenant_status  ON ftp.ftp_transfer_job (tenant_id, status);
CREATE INDEX ix_ftp_job_tenant_created ON ftp.ftp_transfer_job (tenant_id, created_at DESC);

CREATE TABLE ftp.ftp_transfer_attempt (
    id               UUID          NOT NULL PRIMARY KEY,
    tenant_id        UUID          NOT NULL,
    transfer_job_id  UUID          NOT NULL,
    attempt_number   INTEGER       NOT NULL CHECK (attempt_number >= 1),
    phase            VARCHAR(16)   NOT NULL CHECK (phase IN ('FETCH', 'RELEASE')),
    outcome          VARCHAR(32)   NOT NULL
        CHECK (outcome IN ('IN_PROGRESS', 'SUCCESS', 'FAILED_RETRYABLE', 'FAILED_FATAL', 'CANCELLED')),
    started_at       TIMESTAMPTZ   NOT NULL,
    ended_at         TIMESTAMPTZ,
    error_summary    VARCHAR(1000),
    CONSTRAINT uq_ftp_attempt_job_number UNIQUE (transfer_job_id, attempt_number),
    CONSTRAINT fk_ftp_attempt_job FOREIGN KEY (transfer_job_id, tenant_id) REFERENCES ftp.ftp_transfer_job (id, tenant_id)
);
CREATE INDEX ix_ftp_attempt_tenant_job ON ftp.ftp_transfer_attempt (tenant_id, transfer_job_id);

CREATE TABLE ftp.ftp_checksum_record (
    id               UUID          NOT NULL PRIMARY KEY,
    tenant_id        UUID          NOT NULL,
    transfer_job_id  UUID          NOT NULL,
    algorithm        VARCHAR(32)   NOT NULL,
    expected_value   VARCHAR(128)  NOT NULL,
    computed_value   VARCHAR(128)  NOT NULL,
    verified         BOOLEAN       NOT NULL,
    recorded_at      TIMESTAMPTZ   NOT NULL,
    -- Idempotency anchor (Requirement 4): one record per job+algorithm, however many resumes/retries occur.
    CONSTRAINT uq_ftp_checksum_job_algorithm UNIQUE (transfer_job_id, algorithm),
    CONSTRAINT fk_ftp_checksum_job FOREIGN KEY (transfer_job_id, tenant_id) REFERENCES ftp.ftp_transfer_job (id, tenant_id)
);
CREATE INDEX ix_ftp_checksum_tenant ON ftp.ftp_checksum_record (tenant_id, recorded_at DESC);

CREATE TABLE ftp.ftp_quarantine_record (
    id               UUID          NOT NULL PRIMARY KEY,
    tenant_id        UUID          NOT NULL,
    transfer_job_id  UUID          NOT NULL,
    reason           VARCHAR(500)  NOT NULL,
    quarantined_at   TIMESTAMPTZ   NOT NULL,
    released_by      UUID,
    released_at      TIMESTAMPTZ,
    status           VARCHAR(16)   NOT NULL CHECK (status IN ('QUARANTINED', 'RELEASED', 'DESTROYED')),
    resolution_note  VARCHAR(1000),
    CONSTRAINT uq_ftp_quarantine_id_tenant UNIQUE (id, tenant_id),
    CONSTRAINT uq_ftp_quarantine_job UNIQUE (transfer_job_id),
    CONSTRAINT fk_ftp_quarantine_job FOREIGN KEY (transfer_job_id, tenant_id) REFERENCES ftp.ftp_transfer_job (id, tenant_id)
);
CREATE INDEX ix_ftp_quarantine_tenant_status ON ftp.ftp_quarantine_record (tenant_id, status);

CREATE TABLE ftp.ftp_inspection_result (
    id                    UUID          NOT NULL PRIMARY KEY,
    tenant_id             UUID          NOT NULL,
    transfer_job_id       UUID          NOT NULL,
    quarantine_record_id  UUID,                      -- NULL for CLEAN results (nothing was quarantined)
    inspection_service    VARCHAR(128)  NOT NULL,
    outcome               VARCHAR(16)   NOT NULL CHECK (outcome IN ('CLEAN', 'MALICIOUS', 'INCONCLUSIVE')),
    detail                VARCHAR(500),
    inspected_at          TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_ftp_inspection_job FOREIGN KEY (transfer_job_id, tenant_id) REFERENCES ftp.ftp_transfer_job (id, tenant_id),
    CONSTRAINT fk_ftp_inspection_quarantine FOREIGN KEY (quarantine_record_id, tenant_id)
        REFERENCES ftp.ftp_quarantine_record (id, tenant_id)
);
CREATE INDEX ix_ftp_inspection_job ON ftp.ftp_inspection_result (tenant_id, transfer_job_id);

CREATE TABLE ftp.ftp_audit_event (
    id           UUID          NOT NULL PRIMARY KEY,
    tenant_id    UUID          NOT NULL,
    actor_id     VARCHAR(128)  NOT NULL,     -- SSO subject UUID, or SYSTEM:ftp-orchestrator
    action       VARCHAR(64)   NOT NULL,
    target_type  VARCHAR(64)   NOT NULL,
    target_id    UUID,
    outcome      VARCHAR(32)   NOT NULL,
    occurred_at  TIMESTAMPTZ   NOT NULL,
    payload      JSONB         NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX ix_ftp_audit_tenant_time   ON ftp.ftp_audit_event (tenant_id, occurred_at DESC);
CREATE INDEX ix_ftp_audit_tenant_actor  ON ftp.ftp_audit_event (tenant_id, actor_id);
CREATE INDEX ix_ftp_audit_tenant_action ON ftp.ftp_audit_event (tenant_id, action);
CREATE INDEX ix_ftp_audit_tenant_target ON ftp.ftp_audit_event (tenant_id, target_type, target_id);

-- Non-secret, tenant-scoped service configuration (GET/POST /api/v1/config). Keys are allow-listed in code.
CREATE TABLE ftp.ftp_config (
    id            UUID          NOT NULL PRIMARY KEY,
    tenant_id     UUID          NOT NULL,
    config_key    VARCHAR(128)  NOT NULL,
    config_value  VARCHAR(512)  NOT NULL,
    updated_by    UUID          NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_ftp_config_tenant_key UNIQUE (tenant_id, config_key)
);
