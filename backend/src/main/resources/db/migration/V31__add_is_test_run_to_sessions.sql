-- V31: sessions 테이블에 is_test_run 컬럼 추가
-- 마케팅 시뮬레이션 세션은 실제 사용자 통계에서 제외
ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS is_test_run BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Marketing simulation flag (excludes from analytics)';

CREATE INDEX IF NOT EXISTS idx_sessions_is_test_run ON sessions(is_test_run);
