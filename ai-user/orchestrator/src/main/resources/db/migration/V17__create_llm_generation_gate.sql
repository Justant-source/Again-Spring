-- V17: LLM Generation Gate — circuit breaker for generation failures
-- 단일행 런타임 제어: GENERATION(생성)만 홀딩, PUBLISHING(발행)은 계속됨

CREATE TABLE IF NOT EXISTS llm_generation_gate (
    id              INT             NOT NULL DEFAULT 1 COMMENT 'singleton row',
    state           VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|HELD',
    last_held_at    DATETIME(3)     NULL COMMENT 'last time gate was HELD',
    reason          TEXT            NULL COMMENT 'hold reason (nullable)',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT chk_gate_singleton CHECK (id = 1),
    CONSTRAINT chk_gate_state CHECK (state IN ('ACTIVE', 'HELD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Initialize singleton row (ACTIVE by default)
INSERT INTO llm_generation_gate (id, state, updated_at) VALUES (1, 'ACTIVE', NOW(3))
ON DUPLICATE KEY UPDATE updated_at = updated_at;
