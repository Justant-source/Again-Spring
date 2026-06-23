-- V5: persona file history -> DB

CREATE TABLE IF NOT EXISTS persona_history_entries (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    persona_id      VARCHAR(32)     NOT NULL,
    entry_type      VARCHAR(16)     NOT NULL COMMENT 'POST|COMMENT',
    target_post_id  VARCHAR(32)     NOT NULL DEFAULT '',
    category        VARCHAR(32)     NOT NULL DEFAULT '',
    content_hash    CHAR(64)        NOT NULL,
    content         LONGTEXT        NOT NULL,
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_history_persona FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE,
    UNIQUE KEY uk_history_dedupe (persona_id, entry_type, created_at, target_post_id, content_hash),
    KEY idx_history_persona_type_time (persona_id, entry_type, created_at),
    KEY idx_history_persona_time (persona_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS persona_life_state (
    persona_id          VARCHAR(32)     NOT NULL,
    casual_streak       INT             NOT NULL DEFAULT 0,
    ongoing_situation   VARCHAR(255)    NOT NULL DEFAULT '',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (persona_id),
    CONSTRAINT fk_life_state_persona FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
