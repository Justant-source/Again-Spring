ALTER TABLE post_comments ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT "ACTIVE" AFTER body;

CREATE TABLE IF NOT EXISTS blocked_users (
  id VARCHAR(32) PRIMARY KEY,
  blocker_user_id VARCHAR(32) NOT NULL,
  blocked_user_id VARCHAR(32) NOT NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_blocked_users (blocker_user_id, blocked_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
