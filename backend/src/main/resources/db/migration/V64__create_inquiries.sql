CREATE TABLE IF NOT EXISTS inquiries (
    id VARCHAR(32) NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    subject VARCHAR(200),
    category VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    assignee_user_id VARCHAR(32),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_inq_user (user_id),
    INDEX idx_inq_status (status),
    INDEX idx_inq_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS inquiry_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inquiry_id VARCHAR(32) NOT NULL,
    sender_role VARCHAR(10) NOT NULL COMMENT 'USER or ADMIN',
    sender_user_id VARCHAR(32),
    body TEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_im_inquiry (inquiry_id),
    INDEX idx_im_created_at (created_at DESC),
    CONSTRAINT fk_im_inquiry FOREIGN KEY (inquiry_id) REFERENCES inquiries(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
