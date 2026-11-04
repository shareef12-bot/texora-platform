-- NOT run by Flyway (outside db/migration). Run once by the DBA when provisioning the schema.
-- The application connects as cgi_app with NO password stored in this repo (injected from the secret store).
GRANT USAGE ON SCHEMA cgi TO cgi_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON
    cgi.cgi_route_allowlist_entry, cgi.cgi_route_approval, cgi.cgi_worker_isolation_config,
    cgi.cgi_execution_timeout_control, cgi.cgi_config_entry, cgi.cgi_audit_outbox TO cgi_app;
GRANT SELECT, INSERT ON cgi.cgi_request_log, cgi.cgi_execution_record TO cgi_app;
-- Audit table: append-only. No UPDATE, no DELETE, no TRUNCATE.
GRANT SELECT, INSERT ON cgi.cgi_audit_event TO cgi_app;
REVOKE UPDATE, DELETE, TRUNCATE ON cgi.cgi_audit_event FROM cgi_app;
