-- V30: 마케팅 콘텐츠 생성 테이블 추가
-- 시뮬레이션 결과 → 플랫폼별 콘텐츠 자동 생성 → 수동 검수 → 내보내기
CREATE TABLE IF NOT EXISTS marketing_contents (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Auto-increment ID',
    simulation_id     BIGINT NOT NULL COMMENT 'Reference to marketing_simulations',
    platform          ENUM('x', 'instagram', 'naver_blog') NOT NULL COMMENT 'Target social platform',
    title             VARCHAR(255) COMMENT 'Post title or headline',
    body_text         MEDIUMTEXT COMMENT 'Plain text content',
    html_template     MEDIUMTEXT COMMENT 'HTML template with styling',
    image_paths       JSON COMMENT 'List of image file paths (List<String>)',
    hashtags          JSON COMMENT 'Platform-specific hashtags (List<String>)',
    status            ENUM('draft', 'review', 'approved', 'exported', 'rejected') NOT NULL DEFAULT 'draft' COMMENT 'Content approval status',
    safety_check_json JSON COMMENT 'Safety validation results (forbidden_words, crisis_keywords, etc)',
    edited_by         VARCHAR(32) COMMENT 'User ID who last edited',
    approved_by       VARCHAR(32) COMMENT 'User ID who approved',
    created_at        TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT 'Creation timestamp',
    updated_at        TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT 'Last update timestamp',
    CONSTRAINT fk_mc_simulation FOREIGN KEY (simulation_id) REFERENCES marketing_simulations(id) ON DELETE CASCADE,
    CONSTRAINT fk_mc_edited_by FOREIGN KEY (edited_by) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_mc_approved_by FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE SET NULL,
    UNIQUE KEY uk_mc_sim_platform (simulation_id, platform),
    INDEX idx_mc_status_platform (status, platform),
    INDEX idx_mc_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='시뮬레이션 결과 기반 마케팅 콘텐츠 생성 및 검수';
