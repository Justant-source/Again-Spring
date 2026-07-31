-- Pre-generated posts that are held until their scheduled publish time.
--
-- PLAN mode originally published a post the moment its bundle was generated
-- (AiPostBundleService.generateAndPublish), so a 03:00 batch put every post on the
-- feed at 03:00. This table is the holding area: the nightly batch generates content
-- into it, and ScheduledPostPublisher creates the real post row when the slot arrives.
-- Owned by the ai-user orchestrator (ai-user Flyway history table).

CREATE TABLE IF NOT EXISTS ai_scheduled_posts (
    id                    VARCHAR(36)  NOT NULL,
    persona_id            VARCHAR(32)  NOT NULL,
    category              VARCHAR(50)  NULL,
    title                 VARCHAR(200) NOT NULL,
    body                  LONGTEXT     NOT NULL,
    -- Validated comment/reply candidates from the same structured LLM call.
    -- Replayed into ai_thread_plan_items at publish time; never re-requested.
    candidates_json       LONGTEXT     NULL,
    scheduled_publish_at  DATETIME(3)  NOT NULL,
    status                VARCHAR(16)  NOT NULL COMMENT 'SCHEDULED|PUBLISHING|PUBLISHED|FAILED|CANCELLED',
    published_post_id     VARCHAR(32)  NULL,
    published_at          DATETIME(3)  NULL,
    provider              VARCHAR(16)  NULL,
    model                 VARCHAR(64)  NULL,
    -- Same lease protocol as ai_thread_plan_items: claim, then act, so a crash mid-publish recovers.
    lease_owner           VARCHAR(64)  NULL,
    lease_until           DATETIME(3)  NULL,
    attempt_count         INT          NOT NULL DEFAULT 0,
    failure_code          VARCHAR(64)  NULL,
    origin                VARCHAR(24)  NOT NULL DEFAULT 'NIGHTLY_BATCH' COMMENT 'NIGHTLY_BATCH|RETIMED',
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_scheduled_post_due (status, scheduled_publish_at),
    KEY idx_scheduled_post_lease (status, lease_until),
    KEY idx_scheduled_post_persona (persona_id, scheduled_publish_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
