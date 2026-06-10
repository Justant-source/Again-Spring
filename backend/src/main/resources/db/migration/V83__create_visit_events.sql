CREATE TABLE visit_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  path VARCHAR(500) NOT NULL,
  utm_source VARCHAR(100),
  utm_medium VARCHAR(100),
  utm_campaign VARCHAR(100),
  utm_content VARCHAR(100),
  referrer VARCHAR(500),
  session_key VARCHAR(64),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ve_occurred_at (occurred_at),
  INDEX idx_ve_campaign (utm_campaign, occurred_at),
  INDEX idx_ve_path (path(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
