-- Add run summary counters to qap_test_run
ALTER TABLE qap.qap_test_run
    ADD COLUMN IF NOT EXISTS total_cases        INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS passed_cases       INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS failed_cases       INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS inconclusive_cases INT NOT NULL DEFAULT 0;
