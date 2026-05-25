-- V28: 마케팅 소스 스토리 테이블 추가
-- 사용자 제출 이야기 → 익명화 → 승인 workflow
CREATE TABLE IF NOT EXISTS marketing_source_stories (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Auto-increment ID',
    source_platform VARCHAR(50)  NOT NULL COMMENT 'Story source (twitter, reddit, kakao_talk, blog, email, etc)',
    source_url      TEXT COMMENT 'Original source URL if available',
    raw_text        LONGTEXT NOT NULL COMMENT 'Original story text (before anonymization)',
    anonymized_text LONGTEXT NOT NULL COMMENT 'Anonymized version',
    rewrite_ratio   DECIMAL(5, 2) COMMENT 'Rewrite percentage (0-100%)',
    category        VARCHAR(64) COMMENT 'Relationship category (marriage, family, dating, work, friend, etc)',
    relation_type   VARCHAR(64) NOT NULL COMMENT 'RelationType enum (spouse, parent, child, colleague, etc)',
    status          ENUM('pending', 'approved', 'rejected', 'used') NOT NULL DEFAULT 'pending' COMMENT 'Approval status',
    blocked_reason  VARCHAR(255) COMMENT 'Reason for rejection (forbidden_words, insufficient_context, safety_risk, etc)',
    created_by      VARCHAR(32) NOT NULL COMMENT 'User ID who submitted the story',
    created_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Creation timestamp',
    CONSTRAINT fk_mss_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    INDEX idx_mss_status (status),
    INDEX idx_mss_created_at (created_at DESC),
    INDEX idx_mss_source_platform (source_platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='마케팅용 소재 스토리 수집 및 승인 워크플로우';
