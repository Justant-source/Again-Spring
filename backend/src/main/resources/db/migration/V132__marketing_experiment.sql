-- V132: 마케팅 포맷 실험(arm) + 게시 성과 점수(outcome) + 계정 지표
-- Date: 2026-11-21
-- Purpose: 게시 성공이 아니라 사람의 반응을 기준으로 영상 포맷·소재를 고르는 반응 루프의 저장소.
--          기본값은 전부 꺼짐(marketing.experiment.* = false, arm은 baseline만 enabled).

ALTER TABLE marketing_job
  ADD COLUMN format_arm VARCHAR(32) NULL,
  ADD INDEX idx_marketing_job_format_arm (format_arm);

CREATE TABLE marketing_format_arm (
  arm_key          VARCHAR(32)  NOT NULL PRIMARY KEY,
  render_profile   VARCHAR(32)  NOT NULL,
  script_style     VARCHAR(16)  NOT NULL,
  comment_count    INT          NOT NULL,
  outro_mode       VARCHAR(16)  NOT NULL,
  max_duration_sec INT          NULL,
  enabled          TINYINT(1)   NOT NULL DEFAULT 0,
  description      VARCHAR(200) NULL,
  created_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO marketing_format_arm
  (arm_key, render_profile, script_style, comment_count, outro_mode, max_duration_sec, enabled, description)
VALUES
  ('baseline',        'marketing_hook_a', 'narrative', 2, 'partner_cta', NULL, 1, '현행 포맷(댓글 2개 낭독 + 파트너 CTA)'),
  ('short_reveal',    'marketing_hook_a', 'narrative', 0, 'vote_reveal',  25,   0, '댓글 장면 없이 25초 + 공감 비율 공개'),
  ('dilemma',         'marketing_hook_a', 'dilemma',   0, 'vote_reveal',  30,   0, '양쪽 입장만 제시하고 결말 없이 투표 유도'),
  ('kinetic_reveal',  'marketing_hook_b', 'narrative', 1, 'vote_reveal',  30,   0, '키네틱 타이포 + 댓글 1개 + 공감 비율 공개'),
  ('reaction_reveal', 'marketing_hook_c', 'dilemma',   0, 'vote_reveal',  30,   0, '반응형 레이아웃 + 딜레마 대본 + 공감 비율 공개');

CREATE TABLE marketing_publication_outcome (
  id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  job_id         BIGINT       NOT NULL,
  post_id        VARCHAR(32)  NOT NULL,
  platform       VARCHAR(40)  NOT NULL,
  format_arm     VARCHAR(32)  NULL,
  category       VARCHAR(20)  NULL,
  hook_emotion   VARCHAR(16)  NULL,
  status         VARCHAR(16)  NOT NULL COMMENT 'SCORED | MISSING',
  published_at   TIMESTAMP(3) NOT NULL,
  snapshot_at    TIMESTAMP(3) NULL,
  primary_metric BIGINT       NULL,
  engagement     BIGINT       NULL,
  view_ratio     DOUBLE       NULL,
  eng_ratio      DOUBLE       NULL,
  reward         DOUBLE       NULL,
  breakout       TINYINT(1)   NOT NULL DEFAULT 0,
  computed_at    TIMESTAMP(3) NOT NULL,
  UNIQUE KEY uk_outcome_job_platform (job_id, platform),
  KEY idx_outcome_platform_time (platform, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE marketing_account_stats (
  id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  platform     VARCHAR(40)  NOT NULL,
  collected_on DATE         NOT NULL,
  followers    BIGINT       NULL,
  extra_json   LONGTEXT     NULL,
  created_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_account_stats (platform, collected_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO system_setting (setting_key, setting_value, updated_at, updated_by)
VALUES
  ('marketing.experiment.enabled',            'false', CURRENT_TIMESTAMP(6), 'migration:V132'),
  ('marketing.experiment.topic_bias_enabled', 'false', CURRENT_TIMESTAMP(6), 'migration:V132'),
  ('marketing.experiment.min_samples',        '8',     CURRENT_TIMESTAMP(6), 'migration:V132')
ON DUPLICATE KEY UPDATE setting_key = setting_key;
