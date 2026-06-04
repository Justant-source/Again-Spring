ALTER TABLE notifications
    ADD COLUMN ref_comment_id BIGINT NULL DEFAULT NULL AFTER ref_post_id;
