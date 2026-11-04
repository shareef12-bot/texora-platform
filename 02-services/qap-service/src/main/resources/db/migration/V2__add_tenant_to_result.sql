-- Add tenant_id, http_status, and executed_at columns to qap_test_result
-- to support multi-tenant queries and response DTOs.

ALTER TABLE qap.qap_test_result
    ADD COLUMN IF NOT EXISTS tenant_id         UUID,
    ADD COLUMN IF NOT EXISTS http_status       INT,
    ADD COLUMN IF NOT EXISTS executed_at       TIMESTAMPTZ NOT NULL DEFAULT now();

-- Backfill tenant_id from parent run for existing rows
UPDATE qap.qap_test_result r
SET tenant_id = run.tenant_id
FROM qap.qap_test_run run
WHERE r.test_run_id = run.id
  AND r.tenant_id IS NULL;

-- Make tenant_id NOT NULL after backfill
ALTER TABLE qap.qap_test_result
    ALTER COLUMN tenant_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_result_tenant_run
    ON qap.qap_test_result (tenant_id, test_run_id);
