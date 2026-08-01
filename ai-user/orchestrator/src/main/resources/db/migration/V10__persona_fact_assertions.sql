-- WP2: slim persona fact assertions (no validity/temporal columns).
-- origin: EXPLICIT|INFERRED|SYNTHETIC_FILL|LEGACY_IMPORTED

CREATE TABLE IF NOT EXISTS persona_fact_assertions (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    persona_id      VARCHAR(32)     NOT NULL,
    fact_key        VARCHAR(80)     NOT NULL,
    fact_value      TEXT            NOT NULL,
    origin          VARCHAR(24)     NOT NULL COMMENT 'EXPLICIT|INFERRED|SYNTHETIC_FILL|LEGACY_IMPORTED',
    confidence      DECIMAL(4,3)    NOT NULL DEFAULT 1.000,
    evidence_ref    VARCHAR(255)    NULL,
    schema_version  SMALLINT        NOT NULL DEFAULT 1,
    created_at      DATETIME(3)     NOT NULL DEFAULT NOW(3),
    PRIMARY KEY (id),
    CONSTRAINT fk_fact_persona FOREIGN KEY (persona_id) REFERENCES personas(id) ON DELETE CASCADE,
    UNIQUE KEY uk_fact_persona_key (persona_id, fact_key),
    KEY idx_fact_persona (persona_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
