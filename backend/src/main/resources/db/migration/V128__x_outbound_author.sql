-- V128: Outbound 대상 작성자 핸들 + 계정 집계
-- Date: 2026-10-10
-- Purpose: 선댓글이 소수 계정에 쏠리는지 DB 값만으로 분석한다 (과거엔 트윗 ID로 X를 재조회해야 했다).
--          target_author_handle 은 소문자·@ 제거 정규화 값. 과거 행은 NULL (별도 백필).
--          x_target_account 는 계정별 후보 노출/게시 집계.

ALTER TABLE x_ops_action
  ADD COLUMN target_author_handle VARCHAR(64) NULL COMMENT 'OUTBOUND 대상 트윗 작성자 (lower, no @)',
  ADD INDEX idx_xoa_author_created (target_author_handle, created_at);

ALTER TABLE x_comment_trace
  ADD COLUMN target_author_handle VARCHAR(64) NULL COMMENT 'OUTBOUND 대상 트윗 작성자 (lower, no @)',
  ADD INDEX idx_xct_author_created (target_author_handle, created_at);

CREATE TABLE x_target_account (
  handle          VARCHAR(64) NOT NULL PRIMARY KEY COMMENT 'lower, no @',
  first_seen_at   TIMESTAMP(3) NOT NULL,
  last_seen_at    TIMESTAMP(3) NOT NULL,
  seen_count      INT NOT NULL DEFAULT 0 COMMENT '선댓글 후보 틱에 등장한 횟수',
  posted_count    INT NOT NULL DEFAULT 0 COMMENT '선댓글 POSTED 누계',
  last_posted_at  TIMESTAMP(3) NULL,

  INDEX idx_xta_last_posted (last_posted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
