-- ===========================================================================
-- V1 — LDAP Directory Service control-plane schema
-- Schema: ldap
-- Every table has tenant_id UUID NOT NULL (multi-tenancy requirement B.5)
-- Audit table has no UPDATE/DELETE grant for the application role (B.6.4)
-- ===========================================================================

CREATE SCHEMA IF NOT EXISTS ldap;

-- ---------------------------------------------------------------------------
-- Schema definitions — managed object classes with approval workflow
-- ---------------------------------------------------------------------------
CREATE TABLE ldap.ldap_schema_definition (
    id               UUID         NOT NULL,
    tenant_id        UUID         NOT NULL,
    object_class     VARCHAR(256) NOT NULL,
    status           VARCHAR(32)  NOT NULL CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    submitted_by     UUID         NOT NULL,
    submitted_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    rejection_reason TEXT,
    CONSTRAINT pk_ldap_schema_definition PRIMARY KEY (id)
);

CREATE INDEX idx_ldap_schema_def_tenant ON ldap.ldap_schema_definition (tenant_id);
CREATE INDEX idx_ldap_schema_def_status ON ldap.ldap_schema_definition (tenant_id, status);

-- ---------------------------------------------------------------------------
-- Attribute definitions belonging to a schema object class
-- ---------------------------------------------------------------------------
CREATE TABLE ldap.ldap_attribute_definition (
    id                   UUID         NOT NULL,
    schema_definition_id UUID         NOT NULL REFERENCES ldap.ldap_schema_definition (id),
    tenant_id            UUID         NOT NULL,
    name                 VARCHAR(256) NOT NULL,
    syntax               VARCHAR(256) NOT NULL,
    is_required          BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_ldap_attribute_definition PRIMARY KEY (id)
);

CREATE INDEX idx_ldap_attr_def_schema ON ldap.ldap_attribute_definition (schema_definition_id);
CREATE INDEX idx_ldap_attr_def_tenant ON ldap.ldap_attribute_definition (tenant_id);

-- ---------------------------------------------------------------------------
-- Schema approval records — two-person rule: submitter != approver
-- ---------------------------------------------------------------------------
CREATE TABLE ldap.ldap_schema_approval (
    id                   UUID        NOT NULL,
    schema_definition_id UUID        NOT NULL REFERENCES ldap.ldap_schema_definition (id),
    tenant_id            UUID        NOT NULL,
    approver_id          UUID        NOT NULL,
    decision             VARCHAR(16) NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
    decided_at           TIMESTAMPTZ NOT NULL,
    notes                TEXT,
    CONSTRAINT pk_ldap_schema_approval PRIMARY KEY (id)
);

CREATE INDEX idx_ldap_schema_approval_def ON ldap.ldap_schema_approval (schema_definition_id);
CREATE INDEX idx_ldap_schema_approval_tenant ON ldap.ldap_schema_approval (tenant_id);

-- ---------------------------------------------------------------------------
-- Bind configuration — protected LDAP bind endpoints, TLS requirements
-- ---------------------------------------------------------------------------
CREATE TABLE ldap.ldap_bind_config (
    id             UUID         NOT NULL,
    tenant_id      UUID         NOT NULL,
    bind_dn        VARCHAR(512) NOT NULL,
    tls_required   BOOLEAN      NOT NULL DEFAULT TRUE,
    allowed_source VARCHAR(512),
    status         VARCHAR(32)  NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_by     UUID         NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by     UUID,
    updated_at     TIMESTAMPTZ,
    CONSTRAINT pk_ldap_bind_config PRIMARY KEY (id)
);

CREATE INDEX idx_ldap_bind_config_tenant ON ldap.ldap_bind_config (tenant_id);
CREATE UNIQUE INDEX idx_ldap_bind_config_dn_tenant ON ldap.ldap_bind_config (tenant_id, bind_dn);

-- ---------------------------------------------------------------------------
-- Sync state — one row per directory entry reconciled from DC
-- ---------------------------------------------------------------------------
CREATE TABLE ldap.ldap_sync_state (
    entry_dn       VARCHAR(512) NOT NULL,
    tenant_id      UUID         NOT NULL,
    dc_source_id   UUID         NOT NULL,
    last_synced_at TIMESTAMPTZ,
    status         VARCHAR(16)  NOT NULL CHECK (status IN ('IN_SYNC', 'PENDING', 'CONFLICT', 'FAILED')),
    retry_count    INT          NOT NULL DEFAULT 0,
    last_error     TEXT,
    CONSTRAINT pk_ldap_sync_state PRIMARY KEY (entry_dn, tenant_id)
);

CREATE INDEX idx_ldap_sync_state_status ON ldap.ldap_sync_state (tenant_id, status);
CREATE INDEX idx_ldap_sync_state_dc_src ON ldap.ldap_sync_state (dc_source_id);

-- ---------------------------------------------------------------------------
-- Audit event log — append-only; no UPDATE/DELETE permitted for app role
-- ---------------------------------------------------------------------------
CREATE TABLE ldap.ldap_audit_event (
    id          UUID         NOT NULL,
    tenant_id   UUID         NOT NULL,
    actor_id    UUID         NOT NULL,
    action      VARCHAR(128) NOT NULL,
    target_type VARCHAR(64)  NOT NULL,
    target_id   VARCHAR(256),
    outcome     VARCHAR(32)  NOT NULL CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED')),
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    payload     JSONB,
    CONSTRAINT pk_ldap_audit_event PRIMARY KEY (id)
);

CREATE INDEX idx_ldap_audit_tenant_time ON ldap.ldap_audit_event (tenant_id, occurred_at DESC);
CREATE INDEX idx_ldap_audit_actor       ON ldap.ldap_audit_event (actor_id);
CREATE INDEX idx_ldap_audit_action      ON ldap.ldap_audit_event (action);

-- ---------------------------------------------------------------------------
-- Application role restrictions (security requirement B.6.4)
-- Audit table: no UPDATE or DELETE for the application role
-- ---------------------------------------------------------------------------
-- REVOKE UPDATE, DELETE ON ldap.ldap_audit_event FROM ldap_app_role;
-- (Uncomment and run as a DBA-level migration or Flyway script with elevated privilege)
