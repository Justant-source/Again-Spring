ALTER TABLE posts
  ADD COLUMN user_title VARCHAR(200) AFTER title,
  ADD COLUMN juror_count INT NOT NULL DEFAULT 3 AFTER user_title,
  ADD COLUMN invite_token VARCHAR(64) UNIQUE AFTER juror_count,
  ADD COLUMN partner_user_id VARCHAR(32) AFTER invite_token,
  ADD COLUMN partner_body_raw LONGTEXT AFTER partner_user_id,
  ADD COLUMN partner_body_published LONGTEXT AFTER partner_body_raw,
  ADD COLUMN partner_answered_at TIMESTAMP(6) AFTER partner_body_published,
  ADD COLUMN publish_mode VARCHAR(30) NOT NULL DEFAULT "PUBLISH_NOW" AFTER partner_answered_at,
  ADD COLUMN vote_duration_hours INT AFTER publish_mode;
