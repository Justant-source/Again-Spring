-- Wave1-H: stance for thread-level 80% cap measurement; source_example_id for provenance.
-- Additive / nullable so old readers keep working. Owned by ai-user Flyway
-- (flyway_schema_history_aiuser). Do not create WP2 tables here.

ALTER TABLE ai_thread_plan_items
    ADD COLUMN stance VARCHAR(16) NULL
        COMMENT 'AUTHOR|COUNTERPART|NEUTRAL|CONTRARIAN' AFTER body,
    ADD COLUMN source_example_id BIGINT NULL
        COMMENT 'crawled/source example provenance' AFTER stance;
