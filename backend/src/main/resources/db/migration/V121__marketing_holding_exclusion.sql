-- V121: Create marketing_holding_exclusion table for holding-pool content guard
-- Date: 2026-08-29
-- Purpose: 2026-08-29 X 상위 노출 5건 중 2건이 갈등 사연이 아니었음
--          ("덕혜옹주가 일본 친구한테 털어놓은 고종 독살 얘기", "여초회사 1년 근무자가 쓰는 장단점").
--          MarketingHoldingContentGuard가 홀딩 풀 적재 시(findActiveCandidates) 후보를
--          걸러내면 그 사유를 이 표에 남긴다 — 조용히 사라지지 않게, 나중에 오탐 검증 가능하게.
--          post_id당 1행 (최초 감지 사유만 유지, 매 스케줄러 tick마다 재기록하지 않음).

CREATE TABLE marketing_holding_exclusion (
  post_id     VARCHAR(32) NOT NULL PRIMARY KEY COMMENT 'FK to posts.id — 홀딩 풀에서 제외된 사연',
  reason      VARCHAR(64) NOT NULL COMMENT 'MarketingHoldingContentGuard reason code (예: YEAR_TRIVIA_PATTERN, PROS_CONS_LISTICLE)',
  detected_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '최초 감지 시각',

  INDEX idx_mhe_reason (reason)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
