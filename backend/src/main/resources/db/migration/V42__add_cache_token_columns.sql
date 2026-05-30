-- LLM 호출 로그에 캐시 토큰 및 실제 토큰 수 컬럼 추가
-- cache_read/creation 토큰은 Claude API (claude-api provider) 경로에서만 채워짐
-- CLI(remote) 경로는 usage를 표면화하지 않으므로 NULL 허용

ALTER TABLE llm_call_logs
    ADD COLUMN IF NOT EXISTS cache_read_tokens    INT NULL COMMENT '캐시에서 읽은 입력 토큰 수 (Anthropic cache_read_input_tokens)',
    ADD COLUMN IF NOT EXISTS cache_creation_tokens INT NULL COMMENT '캐시에 새로 저장된 입력 토큰 수 (Anthropic cache_creation_input_tokens)',
    ADD COLUMN IF NOT EXISTS input_tokens         INT NULL COMMENT '실제 입력 토큰 수 (claude-api provider)',
    ADD COLUMN IF NOT EXISTS output_tokens        INT NULL COMMENT '실제 출력 토큰 수 (claude-api provider)';
