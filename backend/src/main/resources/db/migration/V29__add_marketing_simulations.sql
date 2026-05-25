-- V29: 마케팅 시뮬레이션 세션 테이블 추가
-- 승인된 스토리 → 페르소나 생성 → 자동 세션 시뮬레이션 실행
CREATE TABLE IF NOT EXISTS marketing_simulations (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Auto-increment ID',
    source_story_id   BIGINT COMMENT 'Reference to marketing_source_stories',
    session_id        VARCHAR(32) COMMENT 'Associated session ID (one-to-one)',
    persona_a         JSON COMMENT 'Persona A snapshot (PersonaProfile)',
    persona_b         JSON COMMENT 'Persona B snapshot (PersonaProfile)',
    turn_count        INT NOT NULL DEFAULT 8 COMMENT 'Target turn count for simulation',
    actual_turn_count INT COMMENT 'Actual completed turns',
    status            ENUM('queued', 'running', 'completed', 'failed', 'canceled') NOT NULL DEFAULT 'queued' COMMENT 'Simulation status',
    error_message     TEXT COMMENT 'Error details if status=failed',
    llm_cost_usd      DECIMAL(8, 4) COMMENT 'Estimated LLM API cost',
    started_at        TIMESTAMP(3) COMMENT 'Simulation start timestamp',
    finished_at       TIMESTAMP(3) COMMENT 'Simulation completion timestamp',
    created_at        TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Creation timestamp',
    CONSTRAINT fk_ms_story FOREIGN KEY (source_story_id) REFERENCES marketing_source_stories(id) ON DELETE SET NULL,
    CONSTRAINT fk_ms_session FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE,
    UNIQUE KEY uk_ms_session (session_id),
    INDEX idx_ms_status (status, created_at DESC),
    INDEX idx_ms_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='마케팅 시뮬레이션 세션 실행 기록';
