-- V45: Social publish results table for tracking per-platform publish outcomes
CREATE TABLE IF NOT EXISTS social_publish_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    content_id BIGINT NOT NULL,
    platform VARCHAR(20) NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    published_url VARCHAR(500),
    error_reason TEXT,
    attempted_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_social_results_content_platform (content_id, platform),
    INDEX idx_social_results_content_id (content_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
