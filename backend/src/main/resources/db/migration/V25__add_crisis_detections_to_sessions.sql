-- crisis_detections 컬럼이 마이그레이션에서 누락되어 추가
ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS crisis_detections JSON COMMENT 'Crisis detection log (List<String>)';
