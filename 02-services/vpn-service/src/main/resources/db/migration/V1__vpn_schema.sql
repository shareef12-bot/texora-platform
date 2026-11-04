-- VPN control-plane schema. Every table carries tenant_id UUID NOT NULL (decision D-1).
-- NO column anywhere holds a credential, key, certificate private part or MFA secret.

CREATE TABLE vpn.vpn_access_policy (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID         NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    conditions           JSONB        NOT NULL,
    status               VARCHAR(32)  NOT NULL CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','DISABLED')),
    submitted_by         UUID         NOT NULL,
    submitted_at         TIMESTAMPTZ  NOT NULL,
    supersedes_policy_id UUID         NULL REFERENCES vpn.vpn_access_policy(id),
    version              BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_vpn_policy_tenant_status ON vpn.vpn_access_policy (tenant_id, status);

CREATE TABLE vpn.vpn_policy_approval (
    id               UUID PRIMARY KEY,
    tenant_id        UUID        NOT NULL,
    access_policy_id UUID        NOT NULL REFERENCES vpn.vpn_access_policy(id),
    approver_id      UUID        NOT NULL,
    decision         VARCHAR(16) NOT NULL CHECK (decision IN ('APPROVED','REJECTED')),
    comment          VARCHAR(1000) NULL,
    decided_at       TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_vpn_approval_policy ON vpn.vpn_policy_approval (tenant_id, access_policy_id);

CREATE TABLE vpn.vpn_subject (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID        NOT NULL,
    dc_user_id         UUID        NOT NULL,           -- by value, no cross-schema FK
    status             VARCHAR(32) NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED')),
    identity_synced_at TIMESTAMPTZ NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_vpn_subject_dc UNIQUE (tenant_id, dc_user_id)
);

CREATE TABLE vpn.vpn_policy_assignment (
    id               UUID PRIMARY KEY,
    tenant_id        UUID        NOT NULL,
    access_policy_id UUID        NOT NULL REFERENCES vpn.vpn_access_policy(id),
    vpn_subject_id   UUID        NOT NULL REFERENCES vpn.vpn_subject(id),
    assigned_by      UUID        NOT NULL,
    assigned_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_vpn_assignment UNIQUE (access_policy_id, vpn_subject_id)
);

CREATE TABLE vpn.vpn_device_enrollment (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID         NOT NULL,
    vpn_subject_id     UUID         NOT NULL REFERENCES vpn.vpn_subject(id),
    device_fingerprint VARCHAR(255) NOT NULL,
    mfa_factor_ref     VARCHAR(255) NOT NULL,          -- opaque reference only
    status             VARCHAR(32)  NOT NULL,
    enrolled_by        UUID         NOT NULL,
    enrolled_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_vpn_device UNIQUE (tenant_id, vpn_subject_id, device_fingerprint)
);

CREATE TABLE vpn.vpn_session (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID         NOT NULL,
    vpn_subject_id       UUID         NOT NULL REFERENCES vpn.vpn_subject(id),
    access_policy_id     UUID         NOT NULL REFERENCES vpn.vpn_access_policy(id),
    device_enrollment_id UUID         NULL REFERENCES vpn.vpn_device_enrollment(id),
    status               VARCHAR(32)  NOT NULL CHECK (status IN ('ACTIVE','REVOKED','EXPIRED')),
    established_at       TIMESTAMPTZ  NOT NULL,
    expires_at           TIMESTAMPTZ  NOT NULL,
    revoked_at           TIMESTAMPTZ  NULL,
    revocation_trigger   VARCHAR(48)  NULL,
    revocation_reason    VARCHAR(500) NULL,
    last_validated_at    TIMESTAMPTZ  NOT NULL,
    next_validation_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_vpn_session_tenant ON vpn.vpn_session (tenant_id, established_at DESC);
CREATE INDEX idx_vpn_session_due    ON vpn.vpn_session (next_validation_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_vpn_session_stale  ON vpn.vpn_session (last_validated_at) WHERE status = 'ACTIVE';
CREATE INDEX idx_vpn_session_expiry ON vpn.vpn_session (expires_at)        WHERE status = 'ACTIVE';
CREATE INDEX idx_vpn_session_policy ON vpn.vpn_session (tenant_id, access_policy_id) WHERE status = 'ACTIVE';

CREATE TABLE vpn.vpn_session_event (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    session_id  UUID         NOT NULL REFERENCES vpn.vpn_session(id),
    event_type  VARCHAR(64)  NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL,
    detail      VARCHAR(500) NULL
);
CREATE INDEX idx_vpn_session_event ON vpn.vpn_session_event (session_id, occurred_at);

CREATE TABLE vpn.vpn_gateway_binding (
    id                       UUID PRIMARY KEY,
    tenant_id                UUID         NOT NULL,
    session_id               UUID         NOT NULL UNIQUE REFERENCES vpn.vpn_session(id),
    dataplane_session_ref    VARCHAR(255) NULL,        -- OPAQUE reference. No credential material.
    gateway_endpoint         VARCHAR(512) NOT NULL,
    bound_at                 TIMESTAMPTZ  NOT NULL,
    teardown_state           VARCHAR(16)  NOT NULL CHECK (teardown_state IN ('NONE','PENDING','CONFIRMED')),
    teardown_attempts        INTEGER      NOT NULL DEFAULT 0,
    last_teardown_attempt_at TIMESTAMPTZ  NULL
);
CREATE UNIQUE INDEX uq_vpn_binding_ref ON vpn.vpn_gateway_binding (dataplane_session_ref) WHERE dataplane_session_ref IS NOT NULL;
CREATE INDEX idx_vpn_binding_teardown ON vpn.vpn_gateway_binding (last_teardown_attempt_at) WHERE teardown_state = 'PENDING';

CREATE TABLE vpn.vpn_audit_event (
    id          UUID PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    actor_id    UUID         NOT NULL,
    action      VARCHAR(128) NOT NULL,
    target_type VARCHAR(64)  NOT NULL,
    target_id   VARCHAR(128) NOT NULL,
    outcome     VARCHAR(32)  NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL,
    payload     JSONB        NOT NULL
);
CREATE INDEX idx_vpn_audit_tenant_time ON vpn.vpn_audit_event (tenant_id, occurred_at DESC);
CREATE INDEX idx_vpn_audit_target      ON vpn.vpn_audit_event (tenant_id, target_type, target_id);

-- Immutability enforced IN THE DATABASE, even against the table owner (the application role also gets no UPDATE/DELETE grant below).
CREATE OR REPLACE FUNCTION vpn.vpn_audit_event_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'vpn_audit_event is append-only (% not permitted)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_vpn_audit_event_no_update_delete
    BEFORE UPDATE OR DELETE ON vpn.vpn_audit_event
    FOR EACH ROW EXECUTE FUNCTION vpn.vpn_audit_event_immutable();
CREATE TRIGGER trg_vpn_audit_event_no_truncate
    BEFORE TRUNCATE ON vpn.vpn_audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION vpn.vpn_audit_event_immutable();

-- Delivery state kept separately so the audit row itself never changes.
CREATE TABLE vpn.vpn_audit_outbox (
    id             UUID PRIMARY KEY,
    tenant_id      UUID         NOT NULL,
    audit_event_id UUID         NOT NULL UNIQUE REFERENCES vpn.vpn_audit_event(id),
    attempts       INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    published_at   TIMESTAMPTZ  NULL,
    last_error     VARCHAR(255) NULL,
    created_at     TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_vpn_outbox_due ON vpn.vpn_audit_outbox (next_attempt_at) WHERE published_at IS NULL;

CREATE TABLE vpn.vpn_config (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    config_key   VARCHAR(128) NOT NULL,
    config_value VARCHAR(512) NOT NULL,
    updated_by   UUID         NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_vpn_config UNIQUE (tenant_id, config_key)
);

CREATE TABLE vpn.vpn_gateway_endpoint (
    id           UUID PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    name         VARCHAR(128) NOT NULL,
    endpoint_url VARCHAR(512) NOT NULL,
    status       VARCHAR(32)  NOT NULL,
    created_by   UUID         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_vpn_gateway_endpoint_tenant ON vpn.vpn_gateway_endpoint (tenant_id, status);

-- Grants for the application role, if it exists in this environment. Audit gets INSERT/SELECT only.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'vpn_app') THEN
        GRANT USAGE ON SCHEMA vpn TO vpn_app;
        GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA vpn TO vpn_app;
        REVOKE UPDATE, DELETE, TRUNCATE ON vpn.vpn_audit_event FROM vpn_app;
        GRANT SELECT, INSERT ON vpn.vpn_audit_event TO vpn_app;
    END IF;
END $$;
