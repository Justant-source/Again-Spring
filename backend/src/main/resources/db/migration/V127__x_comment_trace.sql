-- V127: x_comment_trace — Justant-Bot 선댓글·대댓글의 모델 입력과 결과
-- Date: 2026-10-08
-- Purpose: One row per compose attempt (posted, guard-skipped, or publish-failed)
--          so later analysis can join the source text, prompt, raw model output,
--          and what was actually posted. No FK: the ledger row is optional.
--          Photo bytes are not stored (has_photo only).

CREATE TABLE x_comment_trace (
  id               BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  action_id        BIGINT NULL COMMENT 'x_ops_action.id when the ledger row exists',
  kind             VARCHAR(16) NOT NULL COMMENT 'OUTBOUND | INBOUND',
  target_tweet_id  VARCHAR(64) NULL,
  model            VARCHAR(64) NULL,
  effort           VARCHAR(16) NULL,
  input_text       MEDIUMTEXT NULL COMMENT '대상 트윗 또는 받은 댓글',
  context_text     MEDIUMTEXT NULL COMMENT '다른 사람 댓글 또는 부모 맥락',
  llm_prompt       LONGTEXT NULL COMMENT '모델에 보낸 프롬프트 (사진 바이트 없음)',
  llm_response     LONGTEXT NULL COMMENT '파싱 전 모델 출력',
  body             TEXT NULL COMMENT '파싱된 댓글. 가드에 걸려도 남긴다',
  has_photo        TINYINT(1) NOT NULL DEFAULT 0,
  status           VARCHAR(16) NOT NULL COMMENT 'POSTED | SKIPPED | FAILED',
  skip_reason      VARCHAR(32) NULL,
  posted_tweet_id  VARCHAR(64) NULL,
  created_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  INDEX idx_xct_kind_created (kind, created_at),
  INDEX idx_xct_status_created (status, created_at),
  INDEX idx_xct_target (target_tweet_id),
  INDEX idx_xct_action (action_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
