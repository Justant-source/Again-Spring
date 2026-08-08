-- Daily Planner 실패 복구 로그
CREATE TABLE IF NOT EXISTS daily_planner_retry_log (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    day_bucket              DATE            NOT NULL,
    attempt_count           INT             NOT NULL DEFAULT 1,
    status                  VARCHAR(32)     NOT NULL,  -- PENDING, SUCCESS, FAILED
    error_message           TEXT,
    error_class             VARCHAR(255),
    stacktrace_excerpt      TEXT,           -- 처음 500자만 저장
    previous_attempt_at     TIMESTAMP NULL,
    retry_attempted_at      TIMESTAMP NULL,
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_day (day_bucket),
    KEY idx_status (status),
    KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
