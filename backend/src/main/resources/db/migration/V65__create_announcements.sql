CREATE TABLE IF NOT EXISTS announcements (
    id VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT,
    level VARCHAR(20) NOT NULL DEFAULT 'INFO' COMMENT 'INFO or WARN',
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    starts_at TIMESTAMP(3) NULL,
    ends_at TIMESTAMP(3) NULL,
    created_by VARCHAR(32),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_ann_active (is_active, starts_at, ends_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
