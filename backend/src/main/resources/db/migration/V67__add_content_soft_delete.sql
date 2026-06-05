ALTER TABLE posts ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE posts ADD COLUMN deleted_by_admin_id VARCHAR(32) NULL;
ALTER TABLE posts ADD INDEX idx_post_deleted_at (deleted_at);

ALTER TABLE post_comments ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE post_comments ADD COLUMN deleted_by_admin_id VARCHAR(32) NULL;
ALTER TABLE post_comments ADD INDEX idx_comment_deleted_at (deleted_at);
