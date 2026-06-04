-- V1: AI 유저 시뮬레이션 — 페르소나 테이블 생성
-- 오케스트레이터 전용 Flyway (flyway_schema_history_aiuser — backend 히스토리와 분리)
-- 백엔드 소유 테이블 (users, posts, post_comments)로의 하드 FK 없음 (loose coupling)

-- 1) personas: 봇 1명 = 1행. id = users.id (관례적 공유, 하드 FK 없음)
CREATE TABLE IF NOT EXISTS personas (
    id              VARCHAR(32)     NOT NULL COMMENT '= users.id (관례적 공유)',
    archetype       VARCHAR(64)     NOT NULL COMMENT 'archetypes.yml 키 (갈등 장르)',
    tier            VARCHAR(16)     NOT NULL COMMENT 'HEAVY|REGULAR|LIGHT|DORMANT',
    voice_profile   JSON            NOT NULL COMMENT 'Opus 생성 말투 기술자',
    interests       JSON            NOT NULL COMMENT '카테고리 affinity 가중치 Map<String,Double>',
    bias_profile    JSON            NOT NULL COMMENT '투표 편향 Map<String,Double>',
    circadian       JSON            NOT NULL COMMENT '24버킷 접속 가중치(KST 0-23) List<Double>',
    slang_level     DECIMAL(3,2)    NOT NULL DEFAULT 0.50 COMMENT '0=깔끔 1=채팅용어 다수',
    daily_target    INT             NOT NULL DEFAULT 6 COMMENT '일 목표 행동 수',
    active          BIT(1)          NOT NULL DEFAULT 1,
    created_at      DATETIME(3)     NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2) persona_relationships: 관계 인접 테이블 (Neo4j 대체)
CREATE TABLE IF NOT EXISTS persona_relationships (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    persona_id      VARCHAR(32)     NOT NULL,
    other_id        VARCHAR(32)     NOT NULL,
    relation_type   VARCHAR(20)     NOT NULL COMMENT 'COUPLE|MARRIAGE|FRIEND|FAMILY|PARENT_CHILD|WORK|KOREAN_SPECIFIC',
    closeness       DECIMAL(3,2)    NOT NULL DEFAULT 0.50 COMMENT '0=소원 1=친밀',
    status          VARCHAR(12)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|DORMANT',
    PRIMARY KEY (id),
    CONSTRAINT fk_rel_persona   FOREIGN KEY (persona_id) REFERENCES personas(id),
    CONSTRAINT fk_rel_other     FOREIGN KEY (other_id)   REFERENCES personas(id),
    UNIQUE KEY uk_pair (persona_id, other_id, relation_type),
    KEY idx_rel_a (persona_id),
    KEY idx_rel_b (other_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3) persona_seen_posts: 열람 dedup (이미 행동한 글 재행동 방지)
CREATE TABLE IF NOT EXISTS persona_seen_posts (
    persona_id      VARCHAR(32)     NOT NULL,
    post_id         VARCHAR(32)     NOT NULL COMMENT 'posts.id 동형 (loose, 하드 FK 없음)',
    seen_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    acted           BIT(1)          NOT NULL DEFAULT 0 COMMENT '0=열람만, 1=행동함',
    PRIMARY KEY (persona_id, post_id),
    CONSTRAINT fk_seen_persona FOREIGN KEY (persona_id) REFERENCES personas(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4) persona_action_log: 행동 이력 + 쿨다운 + 감사
CREATE TABLE IF NOT EXISTS persona_action_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    persona_id      VARCHAR(32)     NOT NULL,
    action_type     VARCHAR(16)     NOT NULL COMMENT 'LIKE|VOTE|COMMENT|REPLY|POST|INVITE_ANSWER',
    target_type     VARCHAR(16)     NULL COMMENT 'POST|COMMENT',
    target_id       VARCHAR(64)     NULL COMMENT 'posts.id(VARCHAR32) 또는 comment id(BIGINT 문자열) 수용',
    used_llm        BIT(1)          NOT NULL DEFAULT 0,
    status          VARCHAR(16)     NOT NULL DEFAULT 'POSTED' COMMENT 'PLANNED|GENERATING|POSTED|FAILED|BLOCKED',
    correlation_id  VARCHAR(64)     NULL,
    detail          JSON            NULL COMMENT '{httpStatus, error, generatedLen,...}',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_log_persona FOREIGN KEY (persona_id) REFERENCES personas(id),
    KEY idx_action_persona_time (persona_id, created_at),
    KEY idx_action_type_time (action_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5) ai_user_runtime: 단일행 런타임 상태 / kill-switch
CREATE TABLE IF NOT EXISTS ai_user_runtime (
    id              INT             NOT NULL DEFAULT 1,
    enabled         BIT(1)          NOT NULL DEFAULT 0 COMMENT '마스터 kill-switch (dev 기본 OFF)',
    daily_global_cap INT            NOT NULL DEFAULT 200 COMMENT '일일 전체 행동 상한',
    actions_today   INT             NOT NULL DEFAULT 0 COMMENT '오늘 이미 실행한 행동 수',
    day_bucket      DATE            NULL COMMENT 'actions_today 기준일 (날짜 바뀌면 리셋)',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT chk_runtime_singleton CHECK (id = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- kill-switch 기본 행 삽입 (OFF 상태로 시작)
INSERT INTO ai_user_runtime (id, enabled, updated_at) VALUES (1, 0, NOW(3))
ON DUPLICATE KEY UPDATE updated_at = updated_at;
