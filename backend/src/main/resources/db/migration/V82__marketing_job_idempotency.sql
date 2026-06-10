ALTER TABLE marketing_job ADD COLUMN idempotency_key VARCHAR(80) NULL;
CREATE UNIQUE INDEX idx_mj_idempotency ON marketing_job(idempotency_key);
