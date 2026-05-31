-- V44: Social sessions table for encrypted browser storage states
CREATE TABLE IF NOT EXISTS social_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform VARCHAR(20) NOT NULL,
    storage_state_enc MEDIUMTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SEEDED',
    seeded_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    last_used_at TIMESTAMP(6),
    UNIQUE KEY uk_social_sessions_platform (platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
