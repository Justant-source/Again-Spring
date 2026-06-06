-- V69: ai_content_corrections에 status 컬럼 추가
-- PENDING: 일반 수정(수정 버튼)에서 캡처 — 아직 규칙 미적용
-- PROCESSED: AI 개선 플로우로 규칙까지 적용 완료
-- SKIPPED: 관리자가 학습 데이터로 사용 안 하기로 결정

ALTER TABLE ai_content_corrections
  ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
  COMMENT 'PENDING | PROCESSED | SKIPPED'
  AFTER persona_caution;

-- 기존 레코드(AI 개선 플로우 생성)는 PROCESSED로 일괄 업데이트
UPDATE ai_content_corrections SET status = 'PROCESSED' WHERE pushed_to_bank = 1 OR persona_caution IS NOT NULL;

CREATE INDEX idx_corr_status ON ai_content_corrections (status);
