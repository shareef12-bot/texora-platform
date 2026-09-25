-- =============================================================================
-- SEC Service — Initial Schema
-- All tables in schema "sec"; every table carries tenant_id UUID NOT NULL (B.5)
-- Audit table rows: NO UPDATE/DELETE grant for the application role (B.6 §4)
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS sec;

-- ---------------------------------------------------------------------------
-- Policy master record
-- ---------------------------------------------------------------------------
CREATE TABLE sec.sec_policy (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID        NOT NULL,
    name          VARCHAR(255) NOT NULL,
    subject_type  VARCHAR(64)  NOT NULL,
    resource      VARCHAR(255) NOT NULL,
    action        VARCHAR(128) NOT NULL,
    owner_team    VARCHAR(128) NOT NULL,
    status        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_sec_policy PRIMARY KEY (id),
    CONSTRAINT chk_sec_policy_status CHECK (status IN ('ACTIVE','RETIRED'))
);

CREATE INDEX idx_sec_policy_tenant ON sec.sec_policy (tenant_id);
CREATE INDEX idx_sec_policy_tenant_resource_action ON sec.sec_policy (tenant_id, resource, action);

-- ---------------------------------------------------------------------------
-- Policy version — history is NEVER deleted; only ACTIVE versions evaluated
-- ---------------------------------------------------------------------------
CREATE TABLE sec.sec_policy_version (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    policy_id       UUID        NOT NULL,
    tenant_id       UUID        NOT NULL,
    version_number  INTEGER     NOT NULL,
    effect          VARCHAR(16)  NOT NULL,
    conditions      JSONB,
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    authored_by     UUID        NOT NULL,
    authored_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    activated_at    TIMESTAMPTZ,
    retired_at      TIMESTAMPTZ,

    CONSTRAINT pk_sec_policy_version PRIMARY KEY (id),
    CONSTRAINT fk_sec_policy_version_policy FOREIGN KEY (policy_id) REFERENCES sec.sec_policy(id),
    CONSTRAINT chk_sec_policy_version_effect CHECK (effect IN ('ALLOW','DENY')),
    CONSTRAINT chk_sec_policy_version_status CHECK (status IN ('DRAFT','PENDING_APPROVAL','ACTIVE','RETIRED')),
    CONSTRAINT uq_sec_policy_version UNIQUE (policy_id, version_number)
);

CREATE INDEX idx_sec_policy_version_policy ON sec.sec_policy_version (policy_id);
CREATE INDEX idx_sec_policy_version_tenant_status ON sec.sec_policy_version (tenant_id, status);

-- ---------------------------------------------------------------------------
-- Policy evaluation log — every decision, including default-deny outcomes
-- ---------------------------------------------------------------------------
CREATE TABLE sec.sec_policy_evaluation_log (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID        NOT NULL,
    policy_version_id   UUID,
    caller_service      VARCHAR(64)  NOT NULL,
    subject_type        VARCHAR(64),
    subject_id          VARCHAR(255),
    resource            VARCHAR(255) NOT NULL,
    action              VARCHAR(128) NOT NULL,
    decision            VARCHAR(16)  NOT NULL,
    reason              VARCHAR(32)  NOT NULL,
    evaluated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    latency_ms          INTEGER,
    context             JSONB,

    CONSTRAINT pk_sec_policy_evaluation_log PRIMARY KEY (id),
    CONSTRAINT fk_sec_eval_log_version FOREIGN KEY (policy_version_id) REFERENCES sec.sec_policy_version(id),
    CONSTRAINT chk_sec_eval_decision CHECK (decision IN ('ALLOW','DENY')),
    CONSTRAINT chk_sec_eval_reason CHECK (reason IN (
        'EXPLICIT_ALLOW','EXPLICIT_DENY','NO_MATCHING_POLICY','POLICY_CONFLICT','EVALUATION_ERROR'
    ))
);

CREATE INDEX idx_sec_eval_log_tenant ON sec.sec_policy_evaluation_log (tenant_id);
CREATE INDEX idx_sec_eval_log_caller ON sec.sec_policy_evaluation_log (caller_service);
CREATE INDEX idx_sec_eval_log_evaluated_at ON sec.sec_policy_evaluation_log (evaluated_at);

-- ---------------------------------------------------------------------------
-- Secret references — vault path + KMS key id ONLY, never raw material
-- ---------------------------------------------------------------------------
CREATE TABLE sec.sec_secret_reference (
    id               UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id        UUID        NOT NULL,
    logical_name     VARCHAR(255) NOT NULL,
    vault_path       VARCHAR(512) NOT NULL,
    kms_key_id       VARCHAR(512),
    owning_service   VARCHAR(64)  NOT NULL,
    classification   VARCHAR(64)  NOT NULL DEFAULT 'CONFIDENTIAL',
    status           VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_sec_secret_reference PRIMARY KEY (id),
    CONSTRAINT uq_sec_secret_ref_tenant_name UNIQUE (tenant_id, logical_name),
    CONSTRAINT chk_sec_secret_ref_status CHECK (status IN ('ACTIVE','REVOKED'))
);

CREATE INDEX idx_sec_secret_ref_tenant ON sec.sec_secret_reference (tenant_id);
CREATE INDEX idx_sec_secret_ref_owner ON sec.sec_secret_reference (owning_service);

-- ---------------------------------------------------------------------------
-- Key rotation records
-- ---------------------------------------------------------------------------
CREATE TABLE sec.sec_key_rotation_record (
    id                   UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            UUID        NOT NULL,
    secret_reference_id  UUID        NOT NULL,
    rotation_due_at      TIMESTAMPTZ  NOT NULL,
    rotated_at           TIMESTAMPTZ,
    rotated_by           UUID,
    outcome              VARCHAR(32),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_sec_key_rotation_record PRIMARY KEY (id),
    CONSTRAINT fk_sec_key_rotation_ref FOREIGN KEY (secret_reference_id) REFERENCES sec.sec_secret_reference(id),
    CONSTRAINT chk_sec_key_rotation_outcome CHECK (outcome IN ('SUCCESS','FAILED','SKIPPED') OR outcome IS NULL)
);

CREATE INDEX idx_sec_key_rotation_tenant ON sec.sec_key_rotation_record (tenant_id);
CREATE INDEX idx_sec_key_rotation_due ON sec.sec_key_rotation_record (rotation_due_at) WHERE rotated_at IS NULL;

-- ---------------------------------------------------------------------------
-- Privileged requests (dual control)
-- ---------------------------------------------------------------------------
CREATE TABLE sec.sec_privileged_request (
    id            UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id     UUID        NOT NULL,
    request_type  VARCHAR(64)  NOT NULL,
    target_ref    VARCHAR(512) NOT NULL,
    requested_by  UUID        NOT NULL,
    justification TEXT        NOT NULL,
    status        VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ  NOT NULL,
    resolved_at   TIMESTAMPTZ,

    CONSTRAINT pk_sec_privileged_request PRIMARY KEY (id),
    CONSTRAINT chk_sec_priv_req_status CHECK (status IN ('PENDING','APPROVED','REJECTED','EXPIRED'))
);

CREATE INDEX idx_sec_priv_req_tenant ON sec.sec_privileged_request (tenant_id);
CREATE INDEX idx_sec_priv_req_status ON sec.sec_privileged_request (status);
CREATE INDEX idx_sec_priv_req_expires ON sec.sec_privileged_request (expires_at) WHERE status = 'PENDING';

-- ---------------------------------------------------------------------------
-- Approval decisions
-- ---------------------------------------------------------------------------
CREATE TABLE sec.sec_approval_decision (
    id                    UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id             UUID        NOT NULL,
    privileged_request_id UUID        NOT NULL,
    approver_id           UUID        NOT NULL,
    decision              VARCHAR(16)  NOT NULL,
    decided_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    approver_role         VARCHAR(128) NOT NULL,
    comments              TEXT,

    CONSTRAINT pk_sec_approval_decision PRIMARY KEY (id),
    CONSTRAINT fk_sec_approval_request FOREIGN KEY (privileged_request_id) REFERENCES sec.sec_privileged_request(id),
    CONSTRAINT chk_sec_approval_decision CHECK (decision IN ('APPROVED','REJECTED')),
    CONSTRAINT uq_sec_approval_approver UNIQUE (privileged_request_id, approver_id)
);

CREATE INDEX idx_sec_approval_request ON sec.sec_approval_decision (privileged_request_id);

-- ---------------------------------------------------------------------------
-- Audit events — tamper-evident hash chain
-- NO UPDATE or DELETE granted to the application role
-- ---------------------------------------------------------------------------
CREATE TABLE sec.sec_audit_event (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id    UUID        NOT NULL,
    sequence_no  BIGSERIAL   NOT NULL,
    actor_id     VARCHAR(255) NOT NULL,
    action       VARCHAR(128) NOT NULL,
    target_type  VARCHAR(64),
    target_id    VARCHAR(255),
    outcome      VARCHAR(32)  NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    payload      JSONB,
    prev_hash    VARCHAR(64),
    event_hash   VARCHAR(64)  NOT NULL,

    CONSTRAINT pk_sec_audit_event PRIMARY KEY (id),
    CONSTRAINT uq_sec_audit_sequence UNIQUE (sequence_no)
);

CREATE INDEX idx_sec_audit_tenant ON sec.sec_audit_event (tenant_id);
CREATE INDEX idx_sec_audit_sequence ON sec.sec_audit_event (sequence_no);
CREATE INDEX idx_sec_audit_occurred ON sec.sec_audit_event (occurred_at);

-- ---------------------------------------------------------------------------
-- Config store
-- ---------------------------------------------------------------------------
CREATE TABLE sec.sec_config (
    id           UUID        NOT NULL DEFAULT gen_random_uuid(),
    tenant_id    UUID        NOT NULL,
    config_key   VARCHAR(255) NOT NULL,
    config_value TEXT        NOT NULL,
    description  TEXT,
    updated_by   UUID        NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT pk_sec_config PRIMARY KEY (id),
    CONSTRAINT uq_sec_config_tenant_key UNIQUE (tenant_id, config_key)
);

CREATE INDEX idx_sec_config_tenant ON sec.sec_config (tenant_id);

-- ---------------------------------------------------------------------------
-- Grant permissions — application role can INSERT/SELECT on audit table only
-- (no UPDATE/DELETE per B.6 §4)
-- ---------------------------------------------------------------------------
-- GRANT SELECT, INSERT ON sec.sec_audit_event TO sec_app;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA sec TO sec_app;
-- GRANT USAGE ON SCHEMA sec TO sec_app;
-- (Un-comment and adapt for your environment provisioning scripts)
