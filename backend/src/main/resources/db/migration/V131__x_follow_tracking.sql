-- V131: 팔로잉 계정 추적 + 상호 액션 이벤트 (7일 무반응 대형 계정 새벽 정리용)
-- Date: 2026-11-06
-- Purpose: 우리가 팔로우한 계정마다 언제 팔로우했는지, 맞팔 여부, 서로 주고받은 액션을 DB로 관리한다.
--          followed_at_source: LEDGER(맞팔 장부 시각) / ESTIMATED(팔로잉 목록 순서 보간, 늦은 쪽 경계) / FIRST_SEEN(스냅샷 첫 관측).

CREATE TABLE x_follow_account (
  id                          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  handle                      VARCHAR(64) NOT NULL COMMENT 'lower, no @',
  status                      VARCHAR(16) NOT NULL DEFAULT 'FOLLOWING' COMMENT 'FOLLOWING / UNFOLLOWED',
  followed_at                 TIMESTAMP(3) NULL,
  followed_at_source          VARCHAR(16) NULL,
  first_seen_at               TIMESTAMP(3) NOT NULL,
  last_seen_following_at      TIMESTAMP(3) NOT NULL,
  follows_us                  BOOLEAN NOT NULL DEFAULT FALSE,
  follows_us_since            TIMESTAMP(3) NULL,
  verified                    BOOLEAN NOT NULL DEFAULT FALSE,
  follower_count              INT NULL,
  follower_count_checked_at   TIMESTAMP(3) NULL,
  last_inbound_interaction_at TIMESTAMP(3) NULL COMMENT '상대가 우리에게 한 마지막 액션(좋아요·리포스트·답글)',
  unfollowed_at               TIMESTAMP(3) NULL,
  unfollow_reason             VARCHAR(32) NULL,
  created_at                  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at                  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

  UNIQUE KEY uq_xfa_handle (handle),
  INDEX idx_xfa_status_followed (status, followed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE x_follow_event (
  id           BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  handle       VARCHAR(64) NOT NULL COMMENT 'lower, no @',
  direction    VARCHAR(8) NOT NULL COMMENT 'IN(상대→우리) / OUT(우리→상대)',
  kind         VARCHAR(24) NOT NULL COMMENT 'FOLLOW / UNFOLLOW / FOLLOW_BACK / LIKE / REPOST / REPLY',
  tweet_id     VARCHAR(64) NULL,
  occurred_at  TIMESTAMP(3) NOT NULL,
  created_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  UNIQUE KEY uq_xfe_dedupe (handle, direction, kind, occurred_at),
  INDEX idx_xfe_handle_occurred (handle, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
