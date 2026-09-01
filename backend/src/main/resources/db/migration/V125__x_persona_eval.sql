-- V125: held-out persona mimicry scores (shadow eval)
-- Date: 2026-09-01
-- Purpose: Store comparison scores of bot reproduction vs operator gold
--          (말투/길이/결/내용 + overall). No FK — example_id is a logical pointer.

CREATE TABLE x_persona_eval (
  id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  example_id      BIGINT NOT NULL COMMENT 'x_persona_example.id (logical, no FK)',
  tweet_id        VARCHAR(64) NULL COMMENT 'Gold tweet_id at eval time',
  bot_body        TEXT NULL COMMENT 'Held-out composeOutbound reproduction',
  score_overall   INT NULL COMMENT '0-100 overall resemblance',
  score_tone      INT NULL COMMENT '0-100 말투',
  score_length    INT NULL COMMENT '0-100 길이',
  score_texture   INT NULL COMMENT '0-100 결',
  score_content   INT NULL COMMENT '0-100 내용',
  judge_note      TEXT NULL,
  created_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  INDEX idx_xpeval_example (example_id),
  INDEX idx_xpeval_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE x_persona_example
  MODIFY COLUMN source VARCHAR(16) NOT NULL
    COMMENT 'TIMELINE_POST | TIMELINE | DELETED_AUTO | DRILL leftover';
