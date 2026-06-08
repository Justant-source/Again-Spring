-- V75: marketing_contents.platform ENUM 소문자 → 대문자 변경
-- JPA @Enumerated(EnumType.STRING)은 대문자 enum 이름을 저장하지만
-- MariaDB ENUM이 소문자로 정의되어 있어 읽기 시 Platform.valueOf("x") 실패.
-- 기존 데이터 UPDATE 후 컬럼 재정의.

UPDATE marketing_contents SET platform = UPPER(platform);

ALTER TABLE marketing_contents
    MODIFY COLUMN platform ENUM('X','INSTAGRAM','NAVER_BLOG','THREADS','FACEBOOK')
        NOT NULL COMMENT 'Target social platform (uppercase matches Java enum name)';
