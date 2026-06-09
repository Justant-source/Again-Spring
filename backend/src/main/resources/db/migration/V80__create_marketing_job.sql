CREATE TABLE marketing_job (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  remote_job_id   VARCHAR(64) UNIQUE,
  post_id         BIGINT NOT NULL,
  status          VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
  phase           VARCHAR(20),
  progress        DOUBLE DEFAULT 0,
  targets         JSON,
  auto_publish    BOOLEAN DEFAULT FALSE,
  artifacts       JSON,
  publications    JSON,
  error_message   TEXT,
  requested_by    BIGINT,
  poll_fail_count INT DEFAULT 0,
  last_polled_at  TIMESTAMP NULL,
  created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_mj_post FOREIGN KEY (post_id) REFERENCES posts(id)
);
CREATE INDEX idx_mj_status ON marketing_job(status);
CREATE INDEX idx_mj_remote ON marketing_job(remote_job_id);
