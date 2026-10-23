-- SOC/SIEM Log Server - initial schema
-- All tables carry tenant_id per platform decision D-1 (B.5). Ingestion-channel tables
-- (siem_ingestion_source, siem_ingestion_log) use the reserved platform tenant id
-- (00000000-0000-0000-0000-000000000000) because an ingestion channel (e.g. crm.audit.events)
-- multiplexes events for every tenant of that product; the real tenant is carried per-event on
-- normalized_event.tenant_scope and on siem_alert once a correlation match occurs.

CREATE SCHEMA IF NOT EXISTS siem;

SET search_path TO siem;

CREATE TABLE siem_ingestion_source (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    protocol        VARCHAR(16)  NOT NULL CHECK (protocol IN ('SYSLOG','HTTP','KAFKA')),
    mtls_cert_ref   VARCHAR(512) NOT NULL,
    status          VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED','REVOKED')),
    product_id      VARCHAR(64),
    registered_by   UUID NOT NULL,
    registered_at   TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_ingestion_source_name UNIQUE (tenant_id, name)
);
CREATE INDEX idx_ingestion_source_tenant ON siem_ingestion_source (tenant_id);
CREATE INDEX idx_ingestion_source_status ON siem_ingestion_source (status);

CREATE TABLE siem_ingestion_log (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    ingestion_source_id UUID NOT NULL REFERENCES siem_ingestion_source (id),
    received_at         TIMESTAMPTZ NOT NULL,
    status              VARCHAR(16) NOT NULL CHECK (status IN ('BUFFERED','RETRIED','DLQ','PROCESSED')),
    attempt_count       INTEGER NOT NULL DEFAULT 0,
    next_retry_at       TIMESTAMPTZ,
    error_summary       TEXT,
    raw_payload         TEXT NOT NULL
);
CREATE INDEX idx_ingestion_log_tenant ON siem_ingestion_log (tenant_id);
CREATE INDEX idx_ingestion_log_source ON siem_ingestion_log (ingestion_source_id);
CREATE INDEX idx_ingestion_log_status ON siem_ingestion_log (status);
CREATE INDEX idx_ingestion_log_next_retry ON siem_ingestion_log (status, next_retry_at);

CREATE TABLE siem_correlation_rule (
    id               UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    name             VARCHAR(255) NOT NULL,
    rule_definition  TEXT NOT NULL,
    status           VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','DISABLED')),
    submitted_by     UUID NOT NULL,
    target_rule_id   UUID REFERENCES siem_correlation_rule (id),
    decision_by      UUID,
    decision_at      TIMESTAMPTZ,
    decision_notes   TEXT,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_rule_tenant ON siem_correlation_rule (tenant_id);
CREATE INDEX idx_rule_status ON siem_correlation_rule (status);

CREATE TABLE siem_rule_change_audit (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    correlation_rule_id  UUID NOT NULL REFERENCES siem_correlation_rule (id),
    changed_by           UUID NOT NULL,
    change_type          VARCHAR(20) NOT NULL CHECK (change_type IN ('SUBMITTED','APPROVED','REJECTED','DISABLED')),
    changed_at           TIMESTAMPTZ NOT NULL,
    notes                TEXT
);
CREATE INDEX idx_rule_change_audit_rule ON siem_rule_change_audit (correlation_rule_id);
CREATE INDEX idx_rule_change_audit_tenant ON siem_rule_change_audit (tenant_id);

CREATE TABLE siem_alert (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    correlation_rule_id  UUID NOT NULL REFERENCES siem_correlation_rule (id),
    triggered_at         TIMESTAMPTZ NOT NULL,
    severity             VARCHAR(16) NOT NULL CHECK (severity IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    status               VARCHAR(16) NOT NULL CHECK (status IN ('OPEN','TRIAGED','ESCALATED','CLOSED')),
    tenant_scope         UUID NOT NULL,
    triggering_event_id  VARCHAR(255),
    summary              VARCHAR(1024)
);
CREATE INDEX idx_alert_tenant ON siem_alert (tenant_id);
CREATE INDEX idx_alert_status ON siem_alert (status);
CREATE INDEX idx_alert_rule ON siem_alert (correlation_rule_id);

CREATE TABLE siem_alert_transition (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    alert_id        UUID NOT NULL REFERENCES siem_alert (id),
    from_status     VARCHAR(16) NOT NULL,
    to_status       VARCHAR(16) NOT NULL,
    actor_id        UUID NOT NULL,
    transitioned_at TIMESTAMPTZ NOT NULL,
    notes           TEXT
);
CREATE INDEX idx_alert_transition_alert ON siem_alert_transition (alert_id);
CREATE INDEX idx_alert_transition_tenant ON siem_alert_transition (tenant_id);

CREATE TABLE siem_audit_event (
    id           UUID PRIMARY KEY,
    tenant_id    UUID NOT NULL,
    actor_id     UUID NOT NULL,
    action       VARCHAR(128) NOT NULL,
    target_type  VARCHAR(128) NOT NULL,
    target_id    VARCHAR(255),
    outcome      VARCHAR(16) NOT NULL CHECK (outcome IN ('SUCCESS','FAILURE','DENIED')),
    occurred_at  TIMESTAMPTZ NOT NULL,
    payload      JSONB
);
CREATE INDEX idx_audit_event_tenant ON siem_audit_event (tenant_id);
CREATE INDEX idx_audit_event_occurred ON siem_audit_event (occurred_at);
CREATE INDEX idx_audit_event_action ON siem_audit_event (action);

CREATE TABLE siem_config_item (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    config_key  VARCHAR(255) NOT NULL,
    config_value TEXT NOT NULL,
    updated_by  UUID NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_config_key UNIQUE (tenant_id, config_key)
);
CREATE INDEX idx_config_item_tenant ON siem_config_item (tenant_id);

-- Append-only enforcement (B.6.4): the application role gets no UPDATE/DELETE on audit tables.
-- Replace 'siem_app' below with the actual application DB role at deploy time if it differs.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'siem_app') THEN
        REVOKE UPDATE, DELETE ON siem.siem_audit_event FROM siem_app;
        REVOKE UPDATE, DELETE ON siem.siem_rule_change_audit FROM siem_app;
        REVOKE UPDATE, DELETE ON siem.siem_alert_transition FROM siem_app;
    END IF;
END $$;
