-- Phase 2.6 (V110): platform engagement snapshots linked to marketing_job.
-- Collectors run on ASM (credentials + remote_id); AS stores the canonical copy for
-- weekly reports and marketing.score.auto_adjust (Phase 2.7).

CREATE TABLE marketing_publication_stats (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  job_id          BIGINT NOT NULL,
  post_id         VARCHAR(32) NOT NULL,
  platform        VARCHAR(40) NOT NULL,
  remote_job_id   VARCHAR(64) NULL,
  remote_id       VARCHAR(120) NULL,
  url             VARCHAR(500) NULL,
  collected_at    TIMESTAMP(3) NOT NULL,
  metrics_json    JSON NOT NULL,
  partial         BOOLEAN NOT NULL DEFAULT TRUE,
  error_message   VARCHAR(500) NULL,
  created_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT fk_mps_job FOREIGN KEY (job_id) REFERENCES marketing_job(id) ON DELETE CASCADE,
  INDEX idx_mps_job (job_id),
  INDEX idx_mps_post_platform (post_id, platform),
  INDEX idx_mps_collected (collected_at),
  INDEX idx_mps_job_platform_collected (job_id, platform, collected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Report-only by default; weekly job nudges score weights only when true.
INSERT INTO system_setting (setting_key, setting_value, updated_at, updated_by)
VALUES ('marketing.score.auto_adjust', 'false', CURRENT_TIMESTAMP, 'system')
ON DUPLICATE KEY UPDATE setting_key = setting_key;
