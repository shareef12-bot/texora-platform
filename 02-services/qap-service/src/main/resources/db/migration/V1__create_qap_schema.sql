-- =============================================================================
-- QAP Service — Initial Schema Migration
-- Schema: qap
-- Every table carries tenant_id per platform multi-tenancy model (Shared Standards B.5)
-- qap_audit_event is append-only: no UPDATE/DELETE grant for the application role (B.6.4)
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS qap;

-- ---------------------------------------------------------------------------
-- qap_test_suite
-- One suite per product/service under test; target_system maps to a product_id
-- or core-service name.
-- ---------------------------------------------------------------------------
CREATE TABLE qap.qap_test_suite (
    id              UUID        NOT NULL,
    tenant_id       UUID        NOT NULL,
    name            VARCHAR(255) NOT NULL,
    target_system   VARCHAR(255) NOT NULL,
    suite_type      VARCHAR(10)  NOT NULL CHECK (suite_type IN ('UI', 'API')),
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_qap_test_suite PRIMARY KEY (id)
);

CREATE INDEX idx_qap_test_suite_tenant ON qap.qap_test_suite (tenant_id);

-- ---------------------------------------------------------------------------
-- qap_test_case
-- Individual test definitions; steps is a structured, versionable JSONB.
-- ---------------------------------------------------------------------------
CREATE TABLE qap.qap_test_case (
    id              UUID        NOT NULL,
    suite_id        UUID        NOT NULL,
    name            VARCHAR(255) NOT NULL,
    steps           JSONB        NOT NULL DEFAULT '[]',
    expected_result TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_qap_test_case    PRIMARY KEY (id),
    CONSTRAINT fk_test_case_suite  FOREIGN KEY (suite_id)
        REFERENCES qap.qap_test_suite (id) ON DELETE CASCADE
);

CREATE INDEX idx_qap_test_case_suite ON qap.qap_test_case (suite_id);

-- ---------------------------------------------------------------------------
-- qap_test_run
-- One row per execution of a suite.
-- ---------------------------------------------------------------------------
CREATE TABLE qap.qap_test_run (
    id              UUID        NOT NULL,
    suite_id        UUID        NOT NULL,
    tenant_id       UUID        NOT NULL,
    triggered_by    VARCHAR(16)  NOT NULL CHECK (triggered_by IN ('CI', 'SCHEDULE', 'MANUAL')),
    started_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    status          VARCHAR(16)  NOT NULL DEFAULT 'RUNNING'
        CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'INCONCLUSIVE')),
    CONSTRAINT pk_qap_test_run    PRIMARY KEY (id),
    CONSTRAINT fk_test_run_suite  FOREIGN KEY (suite_id)
        REFERENCES qap.qap_test_suite (id)
);

CREATE INDEX idx_qap_test_run_suite  ON qap.qap_test_run (suite_id);
CREATE INDEX idx_qap_test_run_tenant ON qap.qap_test_run (tenant_id);

-- ---------------------------------------------------------------------------
-- qap_test_result
-- Per-test-case result; trace_id is the OpenTelemetry trace of the failing
-- request — the key to failure→trace correlation.
-- outcome FLAKY = this specific execution was intermittent.
-- ---------------------------------------------------------------------------
CREATE TABLE qap.qap_test_result (
    id                  UUID        NOT NULL,
    test_run_id         UUID        NOT NULL,
    test_case_id        UUID        NOT NULL,
    outcome             VARCHAR(10)  NOT NULL CHECK (outcome IN ('PASS', 'FAIL', 'FLAKY', 'INCONCLUSIVE')),
    duration_ms         BIGINT,
    screenshot_ref      VARCHAR(1024),   -- object-storage path, not inline bytes
    redacted_request    JSONB,           -- redacted before persistence (§5.4)
    redacted_response   JSONB,           -- redacted before persistence (§5.4)
    trace_id            VARCHAR(64),     -- OpenTelemetry trace ID
    correlated_service  VARCHAR(255),    -- filled by ObservabilityCorrelationClient
    correlated_endpoint VARCHAR(512),
    correlated_http_status INT,
    correlated_exception   TEXT,
    recorded_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_qap_test_result       PRIMARY KEY (id),
    CONSTRAINT fk_result_run            FOREIGN KEY (test_run_id)
        REFERENCES qap.qap_test_run (id),
    CONSTRAINT fk_result_case           FOREIGN KEY (test_case_id)
        REFERENCES qap.qap_test_case (id)
);

CREATE INDEX idx_qap_test_result_run  ON qap.qap_test_result (test_run_id);
CREATE INDEX idx_qap_test_result_case ON qap.qap_test_result (test_case_id);

-- ---------------------------------------------------------------------------
-- qap_defect
-- failure_signature_hash deduplicates multiple failures of the same root cause
-- into a single ticket (idempotency requirement).
-- ---------------------------------------------------------------------------
CREATE TABLE qap.qap_defect (
    id                      UUID        NOT NULL,
    test_result_id          UUID        NOT NULL,
    tenant_id               UUID        NOT NULL,
    issue_tracker_ref       VARCHAR(256),       -- external ticket ID, e.g. QAP-1042
    failure_signature_hash  VARCHAR(64)  NOT NULL,  -- SHA-256 of (endpoint+errorType+service)
    status                  VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
    first_seen_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_seen_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    occurrence_count        INT          NOT NULL DEFAULT 1,
    CONSTRAINT pk_qap_defect           PRIMARY KEY (id),
    CONSTRAINT uq_defect_hash_tenant   UNIQUE (failure_signature_hash, tenant_id),
    CONSTRAINT fk_defect_result        FOREIGN KEY (test_result_id)
        REFERENCES qap.qap_test_result (id)
);

CREATE INDEX idx_qap_defect_tenant ON qap.qap_defect (tenant_id);
CREATE INDEX idx_qap_defect_hash   ON qap.qap_defect (failure_signature_hash);

-- ---------------------------------------------------------------------------
-- qap_flaky_test_record
-- Tracks flake_rate per test case; quarantined tests stop generating defects.
-- ---------------------------------------------------------------------------
CREATE TABLE qap.qap_flaky_test_record (
    id              UUID        NOT NULL,
    test_case_id    UUID        NOT NULL,
    tenant_id       UUID        NOT NULL,
    run_count       INT          NOT NULL DEFAULT 0,
    fail_count      INT          NOT NULL DEFAULT 0,
    flake_rate      NUMERIC(5,4) NOT NULL DEFAULT 0.0,
    quarantined     BOOLEAN      NOT NULL DEFAULT FALSE,
    quarantined_at  TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_qap_flaky_test_record  PRIMARY KEY (id),
    CONSTRAINT uq_flaky_case_tenant      UNIQUE (test_case_id, tenant_id),
    CONSTRAINT fk_flaky_case             FOREIGN KEY (test_case_id)
        REFERENCES qap.qap_test_case (id) ON DELETE CASCADE
);

CREATE INDEX idx_qap_flaky_tenant ON qap.qap_flaky_test_record (tenant_id);

-- ---------------------------------------------------------------------------
-- qap_credential_reference
-- Points to a SEC secret reference. NO credential material stored here, ever.
-- ---------------------------------------------------------------------------
CREATE TABLE qap.qap_credential_reference (
    id                      UUID        NOT NULL,
    tenant_id               UUID        NOT NULL,
    product_id              VARCHAR(128) NOT NULL,
    sec_secret_reference_id VARCHAR(256) NOT NULL,   -- opaque reference ID from SEC
    description             VARCHAR(512),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_qap_credential_reference PRIMARY KEY (id)
);

CREATE INDEX idx_qap_cred_ref_tenant ON qap.qap_credential_reference (tenant_id);
CREATE INDEX idx_qap_cred_ref_product ON qap.qap_credential_reference (tenant_id, product_id);

-- ---------------------------------------------------------------------------
-- qap_audit_event
-- Append-only; also published to Kafka topic qap.audit.events.
-- The application role must NOT have UPDATE or DELETE on this table.
-- ---------------------------------------------------------------------------
CREATE TABLE qap.qap_audit_event (
    id          UUID        NOT NULL,
    tenant_id   UUID        NOT NULL,
    actor_id    UUID        NOT NULL,
    action      VARCHAR(128) NOT NULL,
    target_type VARCHAR(128),
    target_id   UUID,
    outcome     VARCHAR(32)  NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    payload     JSONB,
    CONSTRAINT pk_qap_audit_event PRIMARY KEY (id)
);

CREATE INDEX idx_qap_audit_tenant     ON qap.qap_audit_event (tenant_id);
CREATE INDEX idx_qap_audit_actor      ON qap.qap_audit_event (actor_id);
CREATE INDEX idx_qap_audit_occurred   ON qap.qap_audit_event (occurred_at DESC);

-- ---------------------------------------------------------------------------
-- Revoke UPDATE/DELETE on qap_audit_event from the application role.
-- The application role name must match what is granted in the environment.
-- Uncomment and replace <qap_app_role> with the real role name before deploy.
-- ---------------------------------------------------------------------------
-- REVOKE UPDATE, DELETE ON qap.qap_audit_event FROM <qap_app_role>;
