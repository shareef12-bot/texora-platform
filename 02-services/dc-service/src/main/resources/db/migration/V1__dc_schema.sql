-- dc-service schema. Every table carries tenant_id UUID NOT NULL (decision D-1, B.5).
-- Cross-tenant integrity is also enforced by composite FKs (tenant_id, id).
-- PII classification: dc_user, dc_group (see COMMENT ON below).

CREATE TABLE dc.dc_user (
    id                 UUID         PRIMARY KEY,
    tenant_id          UUID         NOT NULL,
    username           VARCHAR(128) NOT NULL,
    status             VARCHAR(32)  NOT NULL,           -- raw HRMS employment status (ADR D-2)
    display_name       VARCHAR(255) NOT NULL,
    org_unit_id        VARCHAR(128),
    hrms_employee_id   VARCHAR(128) NOT NULL,           -- ADR D-2: projection key
    hrms_last_modified TIMESTAMPTZ,                     -- ADR D-2: staleness guard for upserts
    security_locked    BOOLEAN      NOT NULL DEFAULT FALSE,  -- DC-owned overlay (not an HRMS concern)
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_dc_user_tenant_id        UNIQUE (tenant_id, id),
    CONSTRAINT uq_dc_user_tenant_username  UNIQUE (tenant_id, username),
    CONSTRAINT uq_dc_user_tenant_hrms      UNIQUE (tenant_id, hrms_employee_id)
);
COMMENT ON TABLE dc.dc_user IS 'PII: read-mostly projection of HRMS employee data (ADR D-2). Not authored in DC.';

CREATE TABLE dc.dc_group (
    id          UUID         PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    org_unit_id VARCHAR(128),
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_dc_group_tenant_id   UNIQUE (tenant_id, id),
    CONSTRAINT uq_dc_group_tenant_name UNIQUE (tenant_id, name)
);
COMMENT ON TABLE dc.dc_group IS 'PII-classified (names may reference org units). SECURITY groups only (ADR D-2).';

CREATE TABLE dc.dc_group_membership (
    user_id     UUID        NOT NULL,
    group_id    UUID        NOT NULL,
    tenant_id   UUID        NOT NULL,
    assigned_by UUID        NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, group_id),
    CONSTRAINT fk_membership_user  FOREIGN KEY (tenant_id, user_id)  REFERENCES dc.dc_user  (tenant_id, id),
    CONSTRAINT fk_membership_group FOREIGN KEY (tenant_id, group_id) REFERENCES dc.dc_group (tenant_id, id)
);
CREATE INDEX ix_membership_tenant_group ON dc.dc_group_membership (tenant_id, group_id);

CREATE TABLE dc.dc_policy (
    id           UUID         PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    subject_type VARCHAR(32)  NOT NULL,
    resource     VARCHAR(255) NOT NULL,
    action       VARCHAR(128) NOT NULL,
    effect       VARCHAR(16)  NOT NULL,
    version      INTEGER      NOT NULL,
    created_by   UUID         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_dc_policy_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_dc_policy_effect  CHECK (effect IN ('ALLOW', 'DENY')),
    CONSTRAINT ck_dc_policy_subject CHECK (subject_type IN ('USER', 'GROUP', 'ROLE'))
);

CREATE TABLE dc.dc_policy_assignment (
    id           UUID         PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    policy_id    UUID         NOT NULL,
    subject_id   VARCHAR(255) NOT NULL,   -- UUID string for USER/GROUP, role name for ROLE
    subject_type VARCHAR(32)  NOT NULL,
    assigned_by  UUID         NOT NULL,
    assigned_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT fk_assignment_policy FOREIGN KEY (tenant_id, policy_id) REFERENCES dc.dc_policy (tenant_id, id),
    CONSTRAINT uq_assignment UNIQUE (tenant_id, policy_id, subject_type, subject_id)
);
CREATE INDEX ix_assignment_subject ON dc.dc_policy_assignment (tenant_id, subject_type, subject_id);

CREATE TABLE dc.dc_config_item (
    id               UUID          PRIMARY KEY,
    tenant_id        UUID          NOT NULL,
    key              VARCHAR(255)  NOT NULL,
    value            VARCHAR(4000) NOT NULL,
    status           VARCHAR(32)   NOT NULL,
    submitted_by     UUID          NOT NULL,
    submitted_at     TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    rejection_reason VARCHAR(1000),
    activated_at     TIMESTAMPTZ,
    CONSTRAINT uq_dc_config_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_dc_config_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED'))
);
CREATE INDEX ix_config_tenant_key ON dc.dc_config_item (tenant_id, key);
CREATE INDEX ix_config_rejections ON dc.dc_config_item (tenant_id, submitted_by, status, updated_at);

CREATE TABLE dc.dc_config_approval (
    id             UUID          PRIMARY KEY,
    tenant_id      UUID          NOT NULL,
    config_item_id UUID          NOT NULL,
    approver_id    UUID          NOT NULL,
    decision       VARCHAR(16)   NOT NULL,
    reason         VARCHAR(1000),
    decided_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT fk_approval_item FOREIGN KEY (tenant_id, config_item_id) REFERENCES dc.dc_config_item (tenant_id, id),
    CONSTRAINT uq_approval_once UNIQUE (tenant_id, config_item_id, approver_id),
    CONSTRAINT ck_approval_decision CHECK (decision IN ('APPROVED', 'REJECTED'))
);

CREATE TABLE dc.dc_sync_job (
    id                UUID          PRIMARY KEY,
    tenant_id         UUID          NOT NULL,
    source            VARCHAR(32)   NOT NULL,    -- LDAP_PUSH | HRMS_PULL
    entity_type       VARCHAR(32)   NOT NULL,    -- USER | GROUP | GROUP_MEMBERSHIP | EMPLOYEE | FULL_SYNC
    entity_id         VARCHAR(255),
    operation         VARCHAR(32)   NOT NULL,
    idempotency_key   VARCHAR(512)  NOT NULL,
    status            VARCHAR(32)   NOT NULL,    -- PENDING | SENT | DEAD_LETTERED | RUNNING | COMPLETED | COMPLETED_WITH_ERRORS | FAILED
    started_at        TIMESTAMPTZ   NOT NULL,
    completed_at      TIMESTAMPTZ,
    records_processed INTEGER       NOT NULL DEFAULT 0,
    error_summary     VARCHAR(1000),
    attempts          INTEGER       NOT NULL DEFAULT 0,
    next_retry_at     TIMESTAMPTZ,
    requested_by      UUID          NOT NULL,
    payload           JSONB,
    CONSTRAINT uq_sync_idempotency UNIQUE (tenant_id, idempotency_key)
);
CREATE INDEX ix_sync_due ON dc.dc_sync_job (status, next_retry_at);

CREATE TABLE dc.dc_audit_event (
    id          UUID         PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    actor_id    UUID         NOT NULL,
    action      VARCHAR(64)  NOT NULL,
    target_type VARCHAR(64)  NOT NULL,
    target_id   VARCHAR(255),
    outcome     VARCHAR(32)  NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL,
    payload     JSONB
);
CREATE INDEX ix_audit_search ON dc.dc_audit_event (tenant_id, occurred_at DESC);
CREATE INDEX ix_audit_actor  ON dc.dc_audit_event (tenant_id, actor_id, occurred_at DESC);

-- Immutability, defence in depth: even the table owner cannot UPDATE/DELETE audit rows.
CREATE FUNCTION dc.fn_audit_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'dc_audit_event is append-only (% not permitted)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_immutable
    BEFORE UPDATE OR DELETE ON dc.dc_audit_event
    FOR EACH ROW EXECUTE FUNCTION dc.fn_audit_immutable();
CREATE TRIGGER trg_audit_no_truncate
    BEFORE TRUNCATE ON dc.dc_audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION dc.fn_audit_immutable();

-- Kafka publication state lives in its OWN mutable table so dc_audit_event can stay strictly immutable.
CREATE TABLE dc.dc_audit_publication (
    audit_event_id  UUID         PRIMARY KEY REFERENCES dc.dc_audit_event (id),
    tenant_id       UUID         NOT NULL,
    attempts        INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL,
    published_at    TIMESTAMPTZ,
    last_error      VARCHAR(500)
);
CREATE INDEX ix_audit_pub_due ON dc.dc_audit_publication (published_at, next_attempt_at);

-- Application role grants (role name 'dc_app' is an ASSUMPTION - see README). No UPDATE/DELETE on audit.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'dc_app') THEN
        GRANT USAGE ON SCHEMA dc TO dc_app;
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA dc TO dc_app;
        REVOKE UPDATE, DELETE, TRUNCATE ON dc.dc_audit_event FROM dc_app;
    END IF;
END
$$;
