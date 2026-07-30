-- 계획형 AI-user 실행의 영속 기반.
-- LLM 결과, 예약 실행 및 사람 상호작용을 분리해 재시작/중복 전달에도 안전하게 처리한다.

ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS content_revision INT UNSIGNED NOT NULL DEFAULT 1 AFTER updated_at;

ALTER TABLE post_comments
    ADD COLUMN IF NOT EXISTS content_revision INT UNSIGNED NOT NULL DEFAULT 1 AFTER updated_at;

CREATE TABLE IF NOT EXISTS ai_user_outbox (
    id               CHAR(36)       NOT NULL,
    aggregate_type   VARCHAR(32)    NOT NULL,
    aggregate_id     VARCHAR(64)    NOT NULL,
    event_type       VARCHAR(64)    NOT NULL,
    idempotency_key  VARCHAR(160)   NOT NULL,
    payload          LONGTEXT       NOT NULL,
    status           VARCHAR(16)    NOT NULL DEFAULT 'PENDING',
    occurred_at      DATETIME(3)    NOT NULL DEFAULT NOW(3),
    available_at     DATETIME(3)    NOT NULL DEFAULT NOW(3),
    published_at     DATETIME(3)    NULL,
    lease_owner      VARCHAR(96)    NULL,
    lease_until      DATETIME(3)    NULL,
    attempt_count    INT UNSIGNED   NOT NULL DEFAULT 0,
    last_error_code  VARCHAR(80)    NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_user_outbox_idempotency (idempotency_key),
    KEY idx_ai_user_outbox_dispatch (status, available_at, occurred_at),
    KEY idx_ai_user_outbox_aggregate (aggregate_type, aggregate_id, occurred_at),
    CONSTRAINT chk_ai_user_outbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ai_thread_plans / ai_thread_plan_items / ai_human_interaction_inbox are
-- physically owned by the ai-user orchestrator Flyway history.  Do not create
-- them here: both applications connect to the same schema but maintain
-- independent Flyway histories.

CREATE TABLE IF NOT EXISTS ai_llm_jobs (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    job_key               VARCHAR(180)    NOT NULL,
    job_type              VARCHAR(40)     NOT NULL,
    state                 VARCHAR(20)     NOT NULL DEFAULT 'REQUESTED',
    provider              VARCHAR(16)     NOT NULL,
    model                 VARCHAR(80)     NULL,
    -- no FK: ai_thread_plans is owned/migrated independently by ai-user.
    plan_id               VARCHAR(36)     NULL,
    requested_by_event_id CHAR(36)        NULL,
    input_fingerprint     CHAR(64)        NULL,
    output_fingerprint    CHAR(64)        NULL,
    attempt_count         INT UNSIGNED    NOT NULL DEFAULT 0,
    max_attempts          TINYINT UNSIGNED NOT NULL DEFAULT 2,
    lease_owner           VARCHAR(96)     NULL,
    lease_until           DATETIME(3)     NULL,
    started_at            DATETIME(3)     NULL,
    completed_at          DATETIME(3)     NULL,
    failure_code          VARCHAR(80)     NULL,
    failure_detail        VARCHAR(1000)   NULL,
    created_at            DATETIME(3)     NOT NULL DEFAULT NOW(3),
    updated_at            DATETIME(3)     NOT NULL DEFAULT NOW(3) ON UPDATE NOW(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_llm_job_key (job_key),
    KEY idx_ai_llm_job_claim (state, provider, created_at),
    KEY idx_ai_llm_job_plan (plan_id),
    CONSTRAINT chk_ai_llm_job_state CHECK (state IN ('REQUESTED', 'LEASED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_ai_llm_job_provider CHECK (provider IN ('CLAUDE', 'CODEX')),
    CONSTRAINT fk_ai_llm_job_outbox FOREIGN KEY (requested_by_event_id) REFERENCES ai_user_outbox(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 기존 CLI/API/OFF 설정은 legacy 실행기 호환을 위해 보존한다.
-- PLAN 모드에서는 아래 provider 3개만 사용하며 API 자동 폴백은 허용하지 않는다.
ALTER TABLE ai_user_generation_config
    ADD COLUMN IF NOT EXISTS scheduler_mode VARCHAR(12) NOT NULL DEFAULT 'LEGACY' AFTER daily_token_budget,
    ADD COLUMN IF NOT EXISTS provider_ai_post_bundle VARCHAR(16) NOT NULL DEFAULT 'OFF' AFTER scheduler_mode,
    ADD COLUMN IF NOT EXISTS provider_human_post_plan VARCHAR(16) NOT NULL DEFAULT 'OFF' AFTER provider_ai_post_bundle,
    ADD COLUMN IF NOT EXISTS provider_human_interaction VARCHAR(16) NOT NULL DEFAULT 'OFF' AFTER provider_human_post_plan,
    ADD COLUMN IF NOT EXISTS schedule_execution_paused BOOLEAN NOT NULL DEFAULT FALSE AFTER provider_human_interaction,
    ADD COLUMN IF NOT EXISTS ai_user_kill_switch BOOLEAN NOT NULL DEFAULT FALSE AFTER schedule_execution_paused,
    ADD COLUMN IF NOT EXISTS candidate_pool_size SMALLINT UNSIGNED NOT NULL DEFAULT 24 AFTER ai_user_kill_switch,
    ADD COLUMN IF NOT EXISTS human_batch_max_posts SMALLINT UNSIGNED NOT NULL DEFAULT 10 AFTER candidate_pool_size,
    ADD COLUMN IF NOT EXISTS human_batch_max_interactions SMALLINT UNSIGNED NOT NULL DEFAULT 50 AFTER human_batch_max_posts;
