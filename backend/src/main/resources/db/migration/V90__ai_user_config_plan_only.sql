-- V90: AI User Config PLAN 모드 일원화
-- LEGACY 필드 삭제 + providerVoteLike 추가
-- 변경사항:
--   + ADD COLUMN provider_vote_like VARCHAR(16) NOT NULL DEFAULT 'OFF'
--   - DROP COLUMN backend_post
--   - DROP COLUMN backend_comment
--   - DROP COLUMN backend_reply
--   - DROP COLUMN prompt_caching
--   - DROP COLUMN daily_token_budget
--   - DROP COLUMN scheduler_mode

ALTER TABLE ai_user_generation_config
    ADD COLUMN IF NOT EXISTS provider_vote_like VARCHAR(16) NOT NULL DEFAULT 'OFF' AFTER provider_human_interaction;

ALTER TABLE ai_user_generation_config
    DROP COLUMN IF EXISTS backend_post,
    DROP COLUMN IF EXISTS backend_comment,
    DROP COLUMN IF EXISTS backend_reply,
    DROP COLUMN IF EXISTS prompt_caching,
    DROP COLUMN IF EXISTS daily_token_budget,
    DROP COLUMN IF EXISTS scheduler_mode;
