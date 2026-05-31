-- V43: Social credentials table for encrypted platform credentials (X, Instagram)
CREATE TABLE IF NOT EXISTS social_credentials (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform VARCHAR(20) NOT NULL,
    username_enc TEXT NOT NULL,
    password_enc TEXT NOT NULL,
    totp_secret_enc TEXT,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_social_credentials_platform (platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
