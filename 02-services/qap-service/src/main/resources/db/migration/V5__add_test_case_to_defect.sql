-- Add test_case_id to qap_defect (points to the most recent failing test case)
ALTER TABLE qap.qap_defect
    ADD COLUMN IF NOT EXISTS test_case_id UUID;

UPDATE qap.qap_defect d
SET test_case_id = r.test_case_id
FROM qap.qap_test_result r
WHERE d.test_result_id = r.id
  AND d.test_case_id IS NULL;