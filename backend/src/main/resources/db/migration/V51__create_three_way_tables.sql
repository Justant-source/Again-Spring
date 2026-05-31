CREATE TABLE IF NOT EXISTS three_way_sessions (
    id VARCHAR(32) NOT NULL,
    party_a_user_id VARCHAR(32) NOT NULL,
    party_b_user_id VARCHAR(32),
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    invite_token VARCHAR(64),
    category VARCHAR(50),
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tws_invite_token (invite_token),
    INDEX idx_tws_party_a (party_a_user_id),
    INDEX idx_tws_party_b (party_b_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS three_way_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tws_id VARCHAR(32) NOT NULL,
    author_role VARCHAR(20) NOT NULL,
    content TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'complete',
    llm_model VARCHAR(80),
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_twm_session (tws_id),
    INDEX idx_twm_created_at (tws_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
