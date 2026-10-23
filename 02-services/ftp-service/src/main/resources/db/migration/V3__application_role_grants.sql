-- Least privilege for the runtime role. No DELETE anywhere; audit table is INSERT/SELECT only.
-- ${app_role} is a Flyway placeholder. Skipped if the role does not exist (local dev connecting as owner;
-- the V2 trigger still protects the audit table).
DO $$
DECLARE
    r text := '${app_role}';
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
        EXECUTE format('GRANT USAGE ON SCHEMA ftp TO %I', r);
        EXECUTE format('GRANT SELECT, INSERT, UPDATE ON ftp.ftp_endpoint, ftp.ftp_transfer_job, ftp.ftp_transfer_attempt, '
                    || 'ftp.ftp_checksum_record, ftp.ftp_quarantine_record, ftp.ftp_inspection_result, ftp.ftp_config TO %I', r);
        EXECUTE format('GRANT SELECT, INSERT ON ftp.ftp_audit_event TO %I', r);
        EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON ftp.ftp_audit_event FROM %I', r);
    END IF;
END
$$;
