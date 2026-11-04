-- Defence in depth: cgi_audit_event is append-only for EVERY role (including the table owner that runs
-- Flyway). The application role additionally gets no UPDATE/DELETE grant (see db/ops/cgi_app_role_grants.sql).
CREATE FUNCTION cgi.cgi_audit_event_reject_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'cgi_audit_event is append-only (% not permitted)', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_cgi_audit_event_no_update_delete
    BEFORE UPDATE OR DELETE ON cgi.cgi_audit_event
    FOR EACH ROW EXECUTE FUNCTION cgi.cgi_audit_event_reject_mutation();

CREATE TRIGGER trg_cgi_audit_event_no_truncate
    BEFORE TRUNCATE ON cgi.cgi_audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION cgi.cgi_audit_event_reject_mutation();
