-- Add tenant_id and status to qap_test_case
ALTER TABLE qap.qap_test_case
    ADD COLUMN IF NOT EXISTS tenant_id UUID,
    ADD COLUMN IF NOT EXISTS status    VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

-- Backfill tenant_id from parent suite
UPDATE qap.qap_test_case tc
SET tenant_id = s.tenant_id
FROM qap.qap_test_suite s
WHERE tc.suite_id = s.id
  AND tc.tenant_id IS NULL;

ALTER TABLE qap.qap_test_case
    ALTER COLUMN tenant_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_test_case_tenant
    ON qap.qap_test_case (tenant_id);
