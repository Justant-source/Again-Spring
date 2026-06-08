-- V76: 나머지 마케팅 ENUM 컬럼 소문자 → 대문자 (V75 platform에 이어 status 등 전수 수정)
-- 근본 원인: Flyway가 ENUM을 소문자로 정의 → MariaDB가 canonical 소문자로 저장
--   → JPA @Enumerated(EnumType.STRING)이 읽을 때 Enum.valueOf("draft"/"generating"/"x") 실패.
-- 대상: marketing_contents.status, marketing_hashtag_library.platform,
--       marketing_content_templates.platform.
-- (social_sessions/social_publish_results는 VARCHAR이라 영향 없음)

-- 1. marketing_contents.status
UPDATE marketing_contents SET status = UPPER(status);

ALTER TABLE marketing_contents
    MODIFY COLUMN status ENUM(
        'GENERATING','DRAFT','REVIEW','APPROVED','EXPORTED','REJECTED',
        'PUBLISHING','PARTIAL','PUBLISHED','FAILED'
    ) NOT NULL DEFAULT 'DRAFT' COMMENT 'Content status (uppercase matches Java enum name)';

-- 2. marketing_hashtag_library.platform
UPDATE marketing_hashtag_library SET platform = UPPER(platform);

ALTER TABLE marketing_hashtag_library
    MODIFY COLUMN platform ENUM('X','INSTAGRAM','NAVER_BLOG','THREADS','FACEBOOK')
        NOT NULL COMMENT '적용 플랫폼 (uppercase)';

-- 3. marketing_content_templates.platform
UPDATE marketing_content_templates SET platform = UPPER(platform);

ALTER TABLE marketing_content_templates
    MODIFY COLUMN platform ENUM('X','INSTAGRAM','NAVER_BLOG','THREADS','FACEBOOK')
        NOT NULL COMMENT '적용 플랫폼 (uppercase)';
