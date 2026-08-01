-- 댓글 생성량 설정을 /admin/ai-user 의 SSOT로 승격.
-- 이전에는 human-reply 규칙(0~3 responder, 3 persona, persona당 5회, 후보 pool 크기, chunk,
-- 게시 지연)이 orchestrator application.yml + 환경변수에만 있어 운영자가 화면에서 바꿀 수 없었다.
-- 총 상한(3x5=15)은 저장하지 않고 distinct x per_persona 로 항상 파생한다 — 불변식이 깨질 수 없다.

ALTER TABLE ai_user_generation_config
    ADD COLUMN hr_responders_per_interaction_max TINYINT NOT NULL DEFAULT 3
        COMMENT '사람 댓글 1건에 붙일 수 있는 AI 답글 수 상한 (0~3)',
    ADD COLUMN hr_distinct_personas_max          TINYINT NOT NULL DEFAULT 3
        COMMENT '한 게시글x한 사람 대화에 참여 가능한 AI 유저 수',
    ADD COLUMN hr_replies_per_persona_max        TINYINT NOT NULL DEFAULT 5
        COMMENT 'AI 유저 1명이 같은 대화에서 답글을 다는 횟수 상한',
    ADD COLUMN hr_candidate_responders_max       SMALLINT NOT NULL DEFAULT 8
        COMMENT '게시글당 답글 후보로 올릴 관심 AI 유저 수 (LLM에 전달)',
    ADD COLUMN hr_chunk_size                     SMALLINT NOT NULL DEFAULT 20
        COMMENT 'LLM 호출 1회가 처리하는 사람 댓글 수',
    ADD COLUMN hr_delay_minutes_min              SMALLINT NOT NULL DEFAULT 1
        COMMENT '답글 게시 지연 하한(분)',
    ADD COLUMN hr_delay_minutes_max              SMALLINT NOT NULL DEFAULT 30
        COMMENT '답글 게시 지연 상한(분)';
