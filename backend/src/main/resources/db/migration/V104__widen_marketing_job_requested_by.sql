-- Force commit stores "admin:force:" + JWT subject (often UUID CHAR(36) = 48 chars).
-- VARCHAR(32) overflow caused HTTP 500 on POST /api/admin/marketing/completed/{postId}/force.
ALTER TABLE marketing_job
  MODIFY COLUMN requested_by VARCHAR(128) NULL;
