-- V16: 의견 보내기 테이블 추가
CREATE TABLE feedbacks (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     VARCHAR(32)  NULL COMMENT 'NULL = 게스트',
    session_id  VARCHAR(32)  NULL,
    category    VARCHAR(50)  NOT NULL COMMENT 'ui_bug|feature|content|crisis|praise|other',
    content     TEXT         NOT NULL,
    contact_consent BOOLEAN  NOT NULL DEFAULT FALSE,
    contact_email   VARCHAR(255) NULL,
    page_url    VARCHAR(500) NULL,
    user_agent  VARCHAR(500) NULL,
    metadata    JSON         NULL COMMENT '추가 자동 수집 정보',
    status      VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending|reviewed|resolved',
    admin_note  TEXT         NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_feedbacks_user_id (user_id),
    INDEX idx_feedbacks_category (category),
    INDEX idx_feedbacks_status (status),
    INDEX idx_feedbacks_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='사용자 의견 수집';
