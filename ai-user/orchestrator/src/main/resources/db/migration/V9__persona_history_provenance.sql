-- WP2: provenance on persona history (replaces persona_content_relations).
-- Loose refs only — no hard FK to example_bank or posts.

ALTER TABLE persona_history_entries
    ADD COLUMN source_example_id BIGINT NULL
        COMMENT 'example_bank.id provenance (loose, no hard FK)' AFTER content,
    ADD COLUMN plan_id VARCHAR(36) NULL
        COMMENT 'ai_thread_plans.id provenance (loose, no hard FK)' AFTER source_example_id,
    ADD KEY idx_history_source_example (source_example_id),
    ADD KEY idx_history_plan (plan_id);
