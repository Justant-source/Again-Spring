-- V118: Add render_profile column to marketing_job
-- Date: 2026-08-22
-- Purpose: WS6.1 - Persist render profile in AS DB so test tab can distinguish
--          between marketing_fast (v1) and marketing_v2 renders.
--          Phase 0 implementation: profile must be DB-backed for admin UI filtering/comparison.

-- Step 1: Add render_profile column (nullable; existing rows = NULL → interpret as marketing_fast)
ALTER TABLE marketing_job
  ADD COLUMN render_profile VARCHAR(32) NULL
  COMMENT 'Render profile used (marketing_fast | marketing_v2). NULL = legacy/fast path.';

-- Step 2: Add index for efficient filtering in admin UI (list jobs by profile)
ALTER TABLE marketing_job
  ADD INDEX idx_render_profile (render_profile);
