-- V122: x_ops_action ledger for X inbound/outbound/ritual attempts
-- Date: 2026-08-31
-- Purpose: Prevent double-replies and cap daily POSTED actions (KST). Ritual may have
--          null target_tweet_id until posted. Any row for a target (POSTED/SKIPPED/FAILED)
--          counts as already handled.

CREATE TABLE x_ops_action (
  id                 BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  kind               VARCHAR(16) NOT NULL COMMENT 'RITUAL | INBOUND | OUTBOUND',
  target_tweet_id    VARCHAR(64) NULL COMMENT 'Tweet we replied to; ritual may be null until posted',
  parent_tweet_id    VARCHAR(64) NULL,
  our_post_tweet_id  VARCHAR(64) NULL COMMENT 'Our originating post when the action is a reply-to-our-thread',
  posted_tweet_id    VARCHAR(64) NULL COMMENT 'Id of the tweet we actually posted',
  body               TEXT NULL,
  status             VARCHAR(16) NOT NULL COMMENT 'POSTED | SKIPPED | FAILED',
  skip_reason        VARCHAR(32) NULL COMMENT 'NO_VOICE | SAFETY | LLM_ERROR | CAP | DISABLED | DEV_LLM_OFF',
  created_at         TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  INDEX idx_xoa_kind_created (kind, created_at),
  INDEX idx_xoa_target_tweet (target_tweet_id),
  INDEX idx_xoa_our_post_created (our_post_tweet_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
