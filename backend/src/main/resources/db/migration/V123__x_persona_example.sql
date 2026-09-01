-- V123: operator voice corpus for Justant-Bot (timeline comments + Telegram drills)
-- Date: 2026-09-01
-- Purpose: Store (situation, operator reply) pairs. DRILL rows include the source tweet
--          text; TIMELINE rows may have null post_text. tweet_id unique so outbound
--          does not comment on a tweet the operator already labeled.

CREATE TABLE x_persona_example (
  id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  source          VARCHAR(16) NOT NULL COMMENT 'DRILL | TIMELINE',
  tweet_id        VARCHAR(64) NULL COMMENT 'Original tweet (drill) or reply status (timeline)',
  post_text       TEXT NULL COMMENT 'Situation caption; null for timeline-only ingest',
  has_photo       TINYINT(1) NOT NULL DEFAULT 0,
  operator_body   TEXT NOT NULL COMMENT 'Operator-typed reply; never auto-posted in v1',
  created_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  UNIQUE INDEX uk_xpe_tweet (tweet_id),
  INDEX idx_xpe_source_created (source, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
