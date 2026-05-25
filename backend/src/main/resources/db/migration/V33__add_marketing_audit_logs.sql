CREATE TABLE IF NOT EXISTS marketing_audit_logs (
    id          BIGINT NOT NULL AUTO_INCREMENT,
    content_id  BIGINT,
    action      VARCHAR(50) NOT NULL,
    actor_user_id VARCHAR(32) NOT NULL,
    payload_json JSON,
    created_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_mal_content_id (content_id),
    INDEX idx_mal_created_at (created_at DESC),
    CONSTRAINT fk_mal_content FOREIGN KEY (content_id)
        REFERENCES marketing_contents(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
