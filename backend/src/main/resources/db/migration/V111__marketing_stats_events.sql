-- Phase 3 (V111): append-only marketing stats activity events for admin timeline.
-- event_type: COLLECT_STARTED | COLLECT_COMPLETED | COLLECT_FAILED | PROPOSE | APPLY | SHADOW_TOGGLE

CREATE TABLE marketing_stats_event (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_type    VARCHAR(32) NOT NULL,
  platform      VARCHAR(32) NULL,
  payload_json  TEXT NULL,
  created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX idx_mse_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
