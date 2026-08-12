-- Free-form LLM stance labels (e.g. concerned_supportive, counter-perspective)
-- exceeded the original VARCHAR(16) and aborted plan-item persist (STRICT mode),
-- leaving published posts with GENERATING plans and zero comments (2026-08-12).

ALTER TABLE ai_thread_plan_items
    MODIFY COLUMN stance VARCHAR(64) NULL
        COMMENT 'Free-form perspective label for stance-cap (LLM); was VARCHAR(16)';
