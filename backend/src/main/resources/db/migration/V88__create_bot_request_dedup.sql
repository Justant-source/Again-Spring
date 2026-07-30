-- Internal bot-write idempotency.  Keep this separate from posts/post_comments:
-- public domain rows must not carry orchestration transport metadata.
CREATE TABLE IF NOT EXISTS bot_request_dedup (
    idempotency_key VARCHAR(160) NOT NULL,
    target_type     VARCHAR(16)  NOT NULL,
    target_id       VARCHAR(64)  NULL,
    bot_user_id     VARCHAR(32)  NOT NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT NOW(3),
    PRIMARY KEY (idempotency_key),
    KEY idx_bot_request_dedup_created_at (created_at),
    CONSTRAINT chk_bot_request_dedup_type CHECK (target_type IN ('POST', 'COMMENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
