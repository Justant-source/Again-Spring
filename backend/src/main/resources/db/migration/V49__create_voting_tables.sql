CREATE TABLE IF NOT EXISTS vote_options (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id VARCHAR(32) NOT NULL,
    label VARCHAR(100) NOT NULL,
    order_idx INT NOT NULL DEFAULT 0,
    INDEX idx_vote_options_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS votes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id VARCHAR(32) NOT NULL,
    option_id BIGINT NOT NULL,
    voter_user_id VARCHAR(32),
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_votes_post_voter (post_id, voter_user_id),
    INDEX idx_votes_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS jurors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id VARCHAR(32) NOT NULL,
    persona JSON,
    chosen_option_id BIGINT,
    empathy_comment TEXT,
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_jurors_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
