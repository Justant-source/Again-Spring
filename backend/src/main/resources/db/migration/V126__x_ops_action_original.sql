-- V126: ORIGINAL scoop ledger — community post ref + kind comment
-- Date: 2026-09-01
-- Purpose: Story-scoop original posts (Justant-Bot). Default off in application
--          (marketing.x.original_post_enabled). Do not enable in prod before
--          the 95% mimicry gate. ref_post_id dedupes already-scooped 사연.

ALTER TABLE x_ops_action
  ADD COLUMN ref_post_id BIGINT NULL COMMENT 'Scooped community post (parsed/hash of posts.id) for ORIGINAL',
  ADD INDEX idx_xoa_ref_post (ref_post_id);

ALTER TABLE x_ops_action
  MODIFY COLUMN kind VARCHAR(16) NOT NULL COMMENT 'RITUAL | INBOUND | OUTBOUND | ORIGINAL';
