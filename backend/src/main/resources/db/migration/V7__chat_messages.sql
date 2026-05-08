-- V7: 카톡식 메시지 테이블 + 세션 메타데이터 (V1.5 단일 흐름)

-- 메시지 테이블 (카톡 채팅 히스토리)
CREATE TABLE IF NOT EXISTS messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id VARCHAR(36) NOT NULL,
    sender VARCHAR(32) NOT NULL COMMENT 'USER_A | USER_B | MEDIATOR_TO_A | MEDIATOR_TO_B',
    content TEXT NOT NULL,
    char_count INT NOT NULL DEFAULT 0,
    is_finalize_suggestion BOOLEAN NOT NULL DEFAULT FALSE,
    is_partner_join_notice BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'SOLO→DUO 전이 시 시스템이 삽입한 안내',
    crisis_level TINYINT DEFAULT NULL COMMENT '1=immediate, 2=warning, NULL=none',
    llm_model VARCHAR(64) DEFAULT NULL COMMENT 'haiku-4-5 / sonnet-4 / null=user message',
    tokens_used INT DEFAULT 0,
    llm_latency_ms BIGINT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_session_created (session_id, created_at),
    INDEX idx_session_sender (session_id, sender),
    CONSTRAINT fk_messages_session FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 세션 메타데이터 추가 (V1.5 카톡식 채팅)
ALTER TABLE sessions
    ADD COLUMN IF NOT EXISTS user_a_message_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS user_b_message_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS partner_joined_at TIMESTAMP NULL DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS finalize_suggested_at TIMESTAMP NULL DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS finalize_agreed_by_a BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS finalize_agreed_by_b BOOLEAN NOT NULL DEFAULT FALSE;

-- status 컬럼: ENUM → VARCHAR로 변환 (신규 값 수용)
-- 먼저 기존 ENUM 값을 문자로 매핑
ALTER TABLE sessions MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'chatting_solo';

-- 기존 6턴 시퀀스 데이터 마이그레이션 (운영 호환)
-- 기존 상태를 신규 상태로 매핑
UPDATE sessions SET status = 'chatting_solo' WHERE status IN ('waiting_b', 'b_joined', 'in_mediation', 'solo_mode');
-- 'completed', 'terminated'은 그대로 유지

-- description 컬럼은 V1 초기 스키마에 없음 — 이 ALTER 제거됨
