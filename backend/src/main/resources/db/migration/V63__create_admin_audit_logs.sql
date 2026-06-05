CREATE TABLE IF NOT EXISTS admin_audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id VARCHAR(32) NOT NULL,
    action VARCHAR(60) NOT NULL,
    target_type VARCHAR(40),
    target_id VARCHAR(64),
    before_json JSON,
    after_json JSON,
    ip VARCHAR(45),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_aal_actor (actor_user_id),
    INDEX idx_aal_created_at (created_at DESC),
    INDEX idx_aal_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
