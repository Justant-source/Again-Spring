ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE or SUSPENDED' AFTER roles;
ALTER TABLE users ADD COLUMN suspended_until TIMESTAMP NULL;
ALTER TABLE users ADD COLUMN suspended_reason VARCHAR(200) NULL;
ALTER TABLE users ADD COLUMN tokens_invalidated_at TIMESTAMP NULL COMMENT 'Force logout: tokens issued before this time are rejected';
ALTER TABLE users ADD INDEX idx_user_status (status);
