-- V39: 콘텐츠 템플릿 테이블 (V15.9 PR3 사전 스키마)
-- 재사용 가능한 플랫폼별 카피 템플릿 관리, 변수 치환 지원

CREATE TABLE IF NOT EXISTS marketing_content_templates (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform        ENUM('x','instagram','naver_blog','threads','facebook') NOT NULL COMMENT '적용 플랫폼',
    name            VARCHAR(120) NOT NULL COMMENT '템플릿 이름',
    body_template   MEDIUMTEXT   NOT NULL COMMENT '카피 템플릿 ($${variable} 형식 변수 치환 지원)',
    variables_json  JSON         NULL     COMMENT '변수 목록 및 설명 [{name, description, required}]',
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '활성 상태 (false면 목록 노출 안 함)',
    created_by      BIGINT       NULL     COMMENT 'admin 사용자 ID',
    created_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_template_platform_active (platform, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='마케팅 콘텐츠 템플릿 (변수 치환 기반 재사용 카피)';
