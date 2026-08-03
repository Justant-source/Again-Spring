-- Pending partner answers for AI paired posts.
-- Author goes PUBLIC first (held in ai_scheduled_posts); partner submits later at T0+Δ.
-- Owned by the ai-user orchestrator (ai-user Flyway history table).

CREATE TABLE IF NOT EXISTS ai_scheduled_partner_answers (
    id                    VARCHAR(36)  NOT NULL,
    post_id               VARCHAR(32)  NOT NULL,
    invite_token          VARCHAR(64)  NOT NULL,
    author_persona_id     VARCHAR(32)  NOT NULL,
    partner_persona_id    VARCHAR(32)  NOT NULL,
    category              VARCHAR(50)  NULL,
    author_title          VARCHAR(200) NULL,
    author_body           LONGTEXT     NULL,
    correlation_id        VARCHAR(32)  NULL,
    scheduled_post_id     VARCHAR(36)  NULL COMMENT 'ai_scheduled_posts.id that produced the author PUBLIC',
    scheduled_partner_at  DATETIME(3)  NOT NULL,
    status                VARCHAR(16)  NOT NULL COMMENT 'SCHEDULED|PUBLISHING|COMPLETED|FAILED|CANCELLED',
    lease_owner           VARCHAR(64)  NULL,
    lease_until           DATETIME(3)  NULL,
    attempt_count         INT          NOT NULL DEFAULT 0,
    failure_code          VARCHAR(64)  NULL,
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_partner_answer_post (post_id),
    KEY idx_partner_answer_due (status, scheduled_partner_at),
    KEY idx_partner_answer_lease (status, lease_until),
    KEY idx_partner_answer_partner (partner_persona_id, scheduled_partner_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
