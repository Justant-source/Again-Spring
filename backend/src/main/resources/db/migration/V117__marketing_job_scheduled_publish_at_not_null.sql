-- V117: Enforce NOT NULL on marketing_job.scheduled_publish_at
-- Date: 2026-08-15
-- Purpose: Decision #10 - Prevent orphaned jobs that never receive a scheduled publish time.
--          This migration must run AFTER the stale job cleanup (Task A).

-- Step 1: Fill any remaining NULL rows with created_at (defensive; Task A should have handled these)
UPDATE marketing_job
SET scheduled_publish_at = created_at
WHERE scheduled_publish_at IS NULL;

-- Step 2: Promote to NOT NULL
ALTER TABLE marketing_job
  MODIFY COLUMN scheduled_publish_at DATETIME(6) NOT NULL;

-- Step 3: Add index for efficient polling (findDueAutoPublishJobs uses this column heavily)
ALTER TABLE marketing_job
  ADD INDEX idx_scheduled_publish_at_status (scheduled_publish_at, status);
