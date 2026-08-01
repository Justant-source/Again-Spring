-- W6-C: durable automatic-retry / failure ledger on human interaction inbox.
-- Full WP6 batch tables deferred; attempt_count + last_error_code are enough for
-- automatic_attempts_max=2 and safe failure codes (no LLM error text in body).

ALTER TABLE ai_human_interaction_inbox
    ADD COLUMN attempt_count   INT      NOT NULL DEFAULT 0 AFTER failure_code,
    ADD COLUMN last_error_code VARCHAR(64) NULL AFTER attempt_count,
    ADD COLUMN schema_version  SMALLINT NOT NULL DEFAULT 1 AFTER last_error_code;
