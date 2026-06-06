-- AI 유저 생성 정책 싱글톤 (§11 토큰 관제).
-- 관리자가 /admin/ai-user 에서 일일 생성량·백엔드를 조정하면 이 행이 업데이트됨.
-- orchestrator(DailyPlanner)가 읽어 페르소나별 쿼터를 분배하고, llm이 CLI/API 라우팅에 사용.

CREATE TABLE IF NOT EXISTS ai_user_generation_config (
    id               INT            NOT NULL DEFAULT 1,          -- 항상 1 (싱글톤)
    -- 일일 목표 생성량 (0 = 해당 타입 OFF)
    target_posts     INT            NOT NULL DEFAULT 0,          -- 0~100
    target_comments  INT            NOT NULL DEFAULT 0,          -- 0~1200
    target_replies   INT            NOT NULL DEFAULT 0,          -- 0~900
    target_votes     INT            NOT NULL DEFAULT 0,          -- LLM 미사용, 분배에만 사용
    target_likes     INT            NOT NULL DEFAULT 0,          -- LLM 미사용
    -- 자동 비율 연동 여부 (프론트엔드 슬라이더 연동)
    auto_comment     BOOLEAN        NOT NULL DEFAULT TRUE,
    auto_reply       BOOLEAN        NOT NULL DEFAULT TRUE,
    -- 콘텐츠 타입별 생성 백엔드: CLI | API | OFF
    backend_post     VARCHAR(8)     NOT NULL DEFAULT 'OFF',
    backend_comment  VARCHAR(8)     NOT NULL DEFAULT 'OFF',
    backend_reply    VARCHAR(8)     NOT NULL DEFAULT 'OFF',
    -- API 경로 옵션
    prompt_caching   BOOLEAN        NOT NULL DEFAULT TRUE,
    daily_token_budget BIGINT       NULL,                        -- NULL = 예산 무제한
    -- 메타
    updated_by       VARCHAR(32),
    updated_at       DATETIME(3)    NOT NULL DEFAULT NOW(3),
    PRIMARY KEY (id),
    CONSTRAINT chk_gen_singleton    CHECK (id = 1),
    CONSTRAINT chk_gen_backend_post    CHECK (backend_post    IN ('CLI', 'API', 'OFF')),
    CONSTRAINT chk_gen_backend_comment CHECK (backend_comment IN ('CLI', 'API', 'OFF')),
    CONSTRAINT chk_gen_backend_reply   CHECK (backend_reply   IN ('CLI', 'API', 'OFF')),
    CONSTRAINT chk_gen_target_posts    CHECK (target_posts    BETWEEN 0 AND 100),
    CONSTRAINT chk_gen_target_comments CHECK (target_comments BETWEEN 0 AND 1200),
    CONSTRAINT chk_gen_target_replies  CHECK (target_replies  BETWEEN 0 AND 900)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기본 행 생성 (최초 배포 시 OFF 안전값으로 시작)
INSERT INTO ai_user_generation_config (id)
VALUES (1)
ON DUPLICATE KEY UPDATE id = id;
