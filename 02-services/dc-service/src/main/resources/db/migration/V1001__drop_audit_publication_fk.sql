-- dc_audit_event is append-only: dc_app has no UPDATE/DELETE (see V1).
-- A foreign key from dc_audit_publication makes Postgres take a FOR KEY SHARE
-- row lock on dc_audit_event, which needs UPDATE rights, so every audited
-- action failed with "permission denied for table dc_audit_event".
-- Audit rows are never updated or deleted, so audit_event_id cannot dangle.
ALTER TABLE dc.dc_audit_publication
    DROP CONSTRAINT IF EXISTS dc_audit_publication_audit_event_id_fkey;
