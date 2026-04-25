-- Phase 0: Remove relationship temperature columns (v2 redesign)
-- 관계 온도 기능 완전 제거 (REFINEMENT_WORK_ORDER.md Phase 0-1)

-- 1. reports 테이블에서 temperature 컬럼 제거
ALTER TABLE reports DROP COLUMN IF EXISTS temperature;

-- 2. user_relationships 테이블에서 average_temperature 컬럼 제거
ALTER TABLE user_relationships DROP COLUMN IF EXISTS average_temperature;

-- 3. conflict_history 테이블에서 temperature 컬럼 제거
ALTER TABLE conflict_history DROP COLUMN IF EXISTS temperature;

-- 4. temperature_history 테이블 전체 삭제
DROP TABLE IF EXISTS temperature_history;
