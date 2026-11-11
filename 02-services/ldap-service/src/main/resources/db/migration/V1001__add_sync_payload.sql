-- Retries need the original operation and payload to re-send a failed sync.
ALTER TABLE ldap.ldap_sync_state ADD COLUMN IF NOT EXISTS operation VARCHAR(16);
ALTER TABLE ldap.ldap_sync_state ADD COLUMN IF NOT EXISTS payload   TEXT;
