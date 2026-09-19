-- SSO / IAM Service — initial schema (deliverable D-002 migration set)
-- Every table carries tenant_id per shared standards B.5 / ADR D-1.

CREATE SCHEMA IF NOT EXISTS sso;

CREATE TABLE sso.sso_application (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    client_id       VARCHAR(128) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    product_id      VARCHAR(64)  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    registered_by   UUID NOT NULL,
    registered_at   TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_sso_application_tenant ON sso.sso_application (tenant_id);
CREATE INDEX idx_sso_application_product ON sso.sso_application (tenant_id, product_id);

CREATE TABLE sso.sso_oidc_saml_config (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    application_id     UUID NOT NULL REFERENCES sso.sso_application (id),
    protocol           VARCHAR(16) NOT NULL,
    redirect_uris      TEXT NOT NULL,
    signing_cert_ref   VARCHAR(512) NOT NULL
);
CREATE INDEX idx_sso_oidc_saml_config_tenant ON sso.sso_oidc_saml_config (tenant_id);
CREATE INDEX idx_sso_oidc_saml_config_app ON sso.sso_oidc_saml_config (application_id);

CREATE TABLE sso.sso_app_role_mapping (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    application_id        UUID NOT NULL REFERENCES sso.sso_application (id),
    dc_role_or_group_id   UUID NOT NULL,
    mapped_scope          VARCHAR(128) NOT NULL
);
CREATE INDEX idx_sso_app_role_mapping_tenant ON sso.sso_app_role_mapping (tenant_id);
CREATE INDEX idx_sso_app_role_mapping_app ON sso.sso_app_role_mapping (application_id);

CREATE TABLE sso.sso_session (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    dc_user_id      UUID NOT NULL,
    application_id  UUID NOT NULL REFERENCES sso.sso_application (id),
    status          VARCHAR(32) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_sso_session_tenant ON sso.sso_session (tenant_id);
CREATE INDEX idx_sso_session_user ON sso.sso_session (tenant_id, dc_user_id, status);

CREATE TABLE sso.sso_token (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    session_id      UUID NOT NULL REFERENCES sso.sso_session (id),
    token_type      VARCHAR(16) NOT NULL,
    issued_at       TIMESTAMPTZ NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ
);
CREATE INDEX idx_sso_token_tenant ON sso.sso_token (tenant_id);
CREATE INDEX idx_sso_token_session ON sso.sso_token (session_id);

CREATE TABLE sso.sso_mfa_enrollment (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    dc_user_id      UUID NOT NULL,
    factor_type     VARCHAR(32) NOT NULL,
    status          VARCHAR(32) NOT NULL,
    enrolled_at     TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_sso_mfa_enrollment_tenant ON sso.sso_mfa_enrollment (tenant_id);
CREATE INDEX idx_sso_mfa_enrollment_user ON sso.sso_mfa_enrollment (tenant_id, dc_user_id, status);

CREATE TABLE sso.sso_consent_grant (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    dc_user_id        UUID NOT NULL,
    application_id    UUID NOT NULL REFERENCES sso.sso_application (id),
    scopes_granted    TEXT NOT NULL,
    granted_at        TIMESTAMPTZ NOT NULL,
    revoked_at        TIMESTAMPTZ
);
CREATE INDEX idx_sso_consent_grant_tenant ON sso.sso_consent_grant (tenant_id);
CREATE INDEX idx_sso_consent_grant_lookup ON sso.sso_consent_grant (tenant_id, dc_user_id, application_id, revoked_at);

CREATE TABLE sso.sso_audit_event (
    id              UUID PRIMARY KEY,
    tenant_id       UUID,
    actor_id        UUID,
    action          VARCHAR(128) NOT NULL,
    target_type     VARCHAR(64) NOT NULL,
    target_id       VARCHAR(255),
    outcome         VARCHAR(32) NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,
    payload         JSONB
);
CREATE INDEX idx_sso_audit_event_tenant ON sso.sso_audit_event (tenant_id);
CREATE INDEX idx_sso_audit_event_occurred ON sso.sso_audit_event (occurred_at);

-- Audit tables get no UPDATE/DELETE grant for the application role (shared
-- standards B.6.4). The application connects as sso_app; run this against
-- the actual role name used in each environment.
REVOKE UPDATE, DELETE ON sso.sso_audit_event FROM sso_app;
