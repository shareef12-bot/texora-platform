-- ftp_audit_event is append-only (LLD 9.3). Two independent layers:
--  1. a trigger that rejects UPDATE/DELETE/TRUNCATE for EVERY role (including the schema owner), and
--  2. privilege grants (V3) that never give the application role UPDATE/DELETE on the table.

CREATE OR REPLACE FUNCTION ftp.reject_audit_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'ftp_audit_event is append-only: % is not permitted', TG_OP USING ERRCODE = '42501';
END;
$$;

CREATE TRIGGER trg_ftp_audit_event_no_update_delete
    BEFORE UPDATE OR DELETE ON ftp.ftp_audit_event
    FOR EACH ROW EXECUTE FUNCTION ftp.reject_audit_mutation();

CREATE TRIGGER trg_ftp_audit_event_no_truncate
    BEFORE TRUNCATE ON ftp.ftp_audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION ftp.reject_audit_mutation();
