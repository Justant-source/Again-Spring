-- V38: 해시태그 라이브러리 테이블 (V15.9 PR3 사전 스키마)
-- 플랫폼별 해시태그 풀 관리, 사용 빈도 추적

CREATE TABLE IF NOT EXISTS marketing_hashtag_library (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    platform     ENUM('x','instagram','naver_blog','threads','facebook') NOT NULL COMMENT '적용 플랫폼',
    tag          VARCHAR(100) NOT NULL COMMENT '해시태그 텍스트 (# 제외)',
    category     VARCHAR(50)  NULL     COMMENT '분류 (관계유형, 감정, 브랜드 등)',
    usage_count  INT          NOT NULL DEFAULT 0 COMMENT '콘텐츠 생성 시 사용 횟수',
    last_used_at TIMESTAMP(3) NULL     COMMENT '마지막 사용 시각',
    created_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_hashtag_platform_tag (platform, tag),
    INDEX idx_hashtag_platform_usage (platform, usage_count DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='마케팅 해시태그 라이브러리 (플랫폼별, 사용 빈도 추적)';
