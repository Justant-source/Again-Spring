-- WP2: story ↔ candidate/selected persona match audit trail.
-- source_example_id is loose (no hard FK to example_bank).

CREATE TABLE IF NOT EXISTS persona_match_audits (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    correlation_id      VARCHAR(80)     NOT NULL,
    source_example_id   BIGINT          NOT NULL COMMENT 'example_bank.id (loose, no hard FK)',
    purpose             VARCHAR(24)     NOT NULL,
    persona_id          VARCHAR(32)     NULL,
    hard_filter_passed  BOOLEAN         NOT NULL,
    semantic_score      DECIMAL(6,5)    NULL,
    final_score         DECIMAL(6,5)    NULL,
    selected            BOOLEAN         NOT NULL,
    reasons             JSON            NOT NULL,
    random_seed         BIGINT          NULL,
    created_at          DATETIME(3)     NOT NULL DEFAULT NOW(3),
    PRIMARY KEY (id),
    KEY idx_match_audit_correlation (correlation_id),
    KEY idx_match_audit_source (source_example_id),
    KEY idx_match_audit_persona_time (persona_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
