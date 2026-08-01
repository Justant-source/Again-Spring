-- W6-A: interested persona pool per post (human-reply responder candidates).
-- Loose refs only — no hard FK to posts/users/personas.

CREATE TABLE IF NOT EXISTS ai_post_interested_personas (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    post_id         VARCHAR(32)     NOT NULL,
    persona_id      VARCHAR(32)     NOT NULL,
    score           DECIMAL(6,5)    NULL,
    source          VARCHAR(24)     NOT NULL DEFAULT 'PLAN_CAST' COMMENT 'PLAN_CAST|MATCHER|MANUAL',
    created_at      DATETIME(3)     NOT NULL DEFAULT NOW(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_interested_persona (post_id, persona_id),
    KEY idx_interested_post (post_id),
    KEY idx_interested_persona (persona_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
