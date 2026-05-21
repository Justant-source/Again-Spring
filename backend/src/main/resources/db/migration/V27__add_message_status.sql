-- 스트리밍 초안 메시지 식별용 status 컬럼.
-- streaming: LLM 응답 수신 중인 임시 행. complete: 최종 저장 완료.
ALTER TABLE messages ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'complete';
