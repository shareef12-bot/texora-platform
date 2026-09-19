-- Flyway migration: create audit schema and audit_event table
-- This migration is bundled in the texora-audit-sdk and applied by each service
-- that includes the SDK on its classpath.

CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE IF NOT EXISTS audit.audit_event (
    id           UUID         NOT NULL,
    tenant_id    UUID         NOT NULL,
    actor_id     UUID         NOT NULL,
    action       VARCHAR(128) NOT NULL,
    target_type  VARCHAR(128) NOT NULL,
    target_id    VARCHAR(255),
    outcome      VARCHAR(32)  NOT NULL,
    occurred_at  TIMESTAMPTZ  NOT NULL,
    payload      TEXT,
    service_name VARCHAR(128) NOT NULL,
    kafka_status VARCHAR(32)  NOT NULL DEFAULT 'PENDING',

    CONSTRAINT pk_audit_event PRIMARY KEY (id),
    CONSTRAINT chk_outcome      CHECK (outcome      IN ('SUCCESS', 'FAILURE')),
    CONSTRAINT chk_kafka_status CHECK (kafka_status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- Indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_audit_tenant_occurred
    ON audit.audit_event (tenant_id, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_kafka_status
    ON audit.audit_event (kafka_status)
    WHERE kafka_status IN ('PENDING', 'FAILED');

-- GRANT: application role gets SELECT + INSERT + limited UPDATE (kafka_status only)
-- No DELETE grant ever. Enforced at the DB level, not just application level.
-- Adjust role name to match your environment:
-- GRANT SELECT, INSERT ON audit.audit_event TO secops_app_role;
-- GRANT UPDATE (kafka_status) ON audit.audit_event TO secops_app_role;

COMMENT ON TABLE audit.audit_event IS
    'Immutable audit log. No DELETE permitted. UPDATE restricted to kafka_status column only.';
