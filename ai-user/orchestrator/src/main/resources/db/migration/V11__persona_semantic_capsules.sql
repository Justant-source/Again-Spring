-- WP2: semantic capsules for LLM-free persona search (KURE VECTOR(1024)).
-- Max 3 capsules per persona (INTEREST|EXPERIENCE|VALUE) enforced in app, not DB.
-- VECTOR INDEX syntax matches learning example_bank / MariaDB 11.8.

CREATE TABLE IF NOT EXISTS persona_semantic_capsules (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    persona_id      VARCHAR(32)     NOT NULL,
    capsule_type    VARCHAR(24)     NOT NULL COMMENT 'INTEREST|EXPERIENCE|VALUE (+ future)',
    topic_key       VARCHAR(80)     NOT NULL,
    text_value      TEXT            NOT NULL,
    embedding       VECTOR(1024)    NOT NULL,
    weight          DECIMAL(4,3)    NOT NULL DEFAULT 1.000,
    origin          VARCHAR(24)     NOT NULL COMMENT 'EXPLICIT|INFERRED|SYNTHETIC_FILL|HISTORY',
    confidence      DECIMAL(4,3)    NOT NULL,
    evidence_ref    VARCHAR(255)    NULL,
    content_hash    CHAR(64)        NOT NULL,
    schema_version  SMALLINT        NOT NULL DEFAULT 1,
    active          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      DATETIME(3)     NOT NULL DEFAULT NOW(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT NOW(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_capsule_persona FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE,
    UNIQUE KEY uk_persona_capsule (persona_id, capsule_type, topic_key),
    KEY idx_persona_type (persona_id, capsule_type),
    VECTOR INDEX idx_persona_capsule_embedding (embedding)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
