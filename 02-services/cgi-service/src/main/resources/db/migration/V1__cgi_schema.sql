-- CGI Gateway schema. Every table carries tenant_id UUID NOT NULL (decision D-1).
-- Tables marked [ADDED] are not in the LLD table list; they are required by endpoints the LLD
-- defines (config, execution-timeout-controls) or by the "audit must never block" guarantee (outbox).

CREATE TABLE cgi.cgi_route_allowlist_entry (
    id                     UUID         PRIMARY KEY,
    tenant_id              UUID         NOT NULL,
    route_pattern          VARCHAR(255) NOT NULL,
    approved_handler_id    VARCHAR(128) NOT NULL,
    max_execution_seconds  INTEGER      NOT NULL CHECK (max_execution_seconds BETWEEN 1 AND 3600),
    status                 VARCHAR(32)  NOT NULL CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED')),
    submitted_by           UUID         NOT NULL,
    created_at             TIMESTAMPTZ  NOT NULL,
    updated_at             TIMESTAMPTZ  NOT NULL,
    version                BIGINT       NOT NULL DEFAULT 0
);
-- 409 on duplicate pattern within a tenant among "live" entries
CREATE UNIQUE INDEX uq_cgi_route_live_pattern
    ON cgi.cgi_route_allowlist_entry (tenant_id, route_pattern)
    WHERE status IN ('DRAFT','PENDING_APPROVAL','APPROVED');
CREATE INDEX ix_cgi_route_tenant_created ON cgi.cgi_route_allowlist_entry (tenant_id, created_at DESC);

CREATE TABLE cgi.cgi_route_approval (
    id                        UUID        PRIMARY KEY,
    tenant_id                 UUID        NOT NULL,
    route_allowlist_entry_id  UUID        NOT NULL REFERENCES cgi.cgi_route_allowlist_entry (id),
    approver_id               UUID        NOT NULL,
    decision                  VARCHAR(16) NOT NULL CHECK (decision IN ('APPROVED','REJECTED')),
    decided_at                TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_cgi_route_approval_entry ON cgi.cgi_route_approval (tenant_id, route_allowlist_entry_id);

-- LLD columns + [ADDED] status/submitted_by/decided_by/decided_at/created_at: changing isolation settings
-- needs the same two-person approval as the route allowlist (LLD section 9.4).
CREATE TABLE cgi.cgi_worker_isolation_config (
    id                UUID         PRIMARY KEY,
    tenant_id         UUID         NOT NULL,
    handler_id        VARCHAR(128) NOT NULL,
    sandbox_profile   VARCHAR(64)  NOT NULL,
    network_allowed   BOOLEAN      NOT NULL DEFAULT FALSE,
    run_as_non_root   BOOLEAN      NOT NULL DEFAULT TRUE,
    status            VARCHAR(32)  NOT NULL CHECK (status IN ('PENDING_APPROVAL','ACTIVE','REJECTED','SUPERSEDED')),
    submitted_by      UUID         NOT NULL,
    decided_by        UUID,
    decided_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX uq_cgi_isolation_one_active
    ON cgi.cgi_worker_isolation_config (tenant_id, handler_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uq_cgi_isolation_one_pending
    ON cgi.cgi_worker_isolation_config (tenant_id, handler_id) WHERE status = 'PENDING_APPROVAL';

-- [ADDED] per-route timeout override (backs /api/v1/execution-timeout-controls)
CREATE TABLE cgi.cgi_execution_timeout_control (
    id                        UUID        PRIMARY KEY,
    tenant_id                 UUID        NOT NULL,
    route_allowlist_entry_id  UUID        NOT NULL REFERENCES cgi.cgi_route_allowlist_entry (id),
    max_execution_seconds     INTEGER     NOT NULL CHECK (max_execution_seconds BETWEEN 1 AND 3600),
    updated_by                UUID        NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    UNIQUE (tenant_id, route_allowlist_entry_id)
);

-- Parameters are REDACTED before this row is written.
CREATE TABLE cgi.cgi_request_log (
    id                        UUID        PRIMARY KEY,
    tenant_id                 UUID        NOT NULL,
    route_allowlist_entry_id  UUID        NOT NULL REFERENCES cgi.cgi_route_allowlist_entry (id),
    caller_sso_subject        UUID        NOT NULL,
    received_at               TIMESTAMPTZ NOT NULL,
    redacted_params           JSONB       NOT NULL
);
CREATE INDEX ix_cgi_request_log_tenant_time ON cgi.cgi_request_log (tenant_id, received_at DESC);

-- exit_reason holds a short reason CODE only - never handler stdout/stderr.
CREATE TABLE cgi.cgi_execution_record (
    id              UUID         PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    request_log_id  UUID         NOT NULL UNIQUE REFERENCES cgi.cgi_request_log (id),
    worker_id       VARCHAR(128) NOT NULL,
    started_at      TIMESTAMPTZ  NOT NULL,
    ended_at        TIMESTAMPTZ  NOT NULL,
    outcome         VARCHAR(16)  NOT NULL CHECK (outcome IN ('COMPLETED','TIMED_OUT','FAILED')),
    exit_reason     VARCHAR(255)
);
CREATE INDEX ix_cgi_execution_tenant_time ON cgi.cgi_execution_record (tenant_id, started_at DESC);

-- Append-only. Payload is redacted before the row is written.
CREATE TABLE cgi.cgi_audit_event (
    id           UUID         PRIMARY KEY,
    tenant_id    UUID         NOT NULL,
    actor_id     UUID,
    action       VARCHAR(64)  NOT NULL,
    target_type  VARCHAR(64)  NOT NULL,
    target_id    VARCHAR(255) NOT NULL,
    outcome      VARCHAR(16)  NOT NULL CHECK (outcome IN ('SUCCESS','DENIED','FAILURE')),
    occurred_at  TIMESTAMPTZ  NOT NULL,
    payload      JSONB        NOT NULL
);
CREATE INDEX ix_cgi_audit_tenant_time ON cgi.cgi_audit_event (tenant_id, occurred_at DESC);

-- [ADDED] Kafka forwarding state lives OUTSIDE the append-only audit table so that cgi_audit_event
-- never needs UPDATE. id = cgi_audit_event.id. last_error is an exception CLASS NAME only.
CREATE TABLE cgi.cgi_audit_outbox (
    id            UUID        PRIMARY KEY REFERENCES cgi.cgi_audit_event (id),
    tenant_id     UUID        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    published_at  TIMESTAMPTZ,
    attempts      INTEGER     NOT NULL DEFAULT 0,
    last_error    VARCHAR(128)
);
CREATE INDEX ix_cgi_audit_outbox_pending ON cgi.cgi_audit_outbox (created_at) WHERE published_at IS NULL;

-- [ADDED] backs /api/v1/config. Only whitelisted, non-secret keys are accepted by the service.
CREATE TABLE cgi.cgi_config_entry (
    id            UUID          PRIMARY KEY,
    tenant_id     UUID          NOT NULL,
    config_key    VARCHAR(100)  NOT NULL,
    config_value  VARCHAR(1000) NOT NULL,
    updated_by    UUID          NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    UNIQUE (tenant_id, config_key)
);
