-- 원본 비교 기능: AI 재구성 사연과 크롤 원본 1:1 추적
-- 재구성 모드로 생성된 사연에만 채워짐 (기존 사연은 모두 NULL)
ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS source_example_id   BIGINT          DEFAULT NULL COMMENT 'example_bank.id — 재구성 원본 크롤 행',
    ADD COLUMN IF NOT EXISTS source_community    VARCHAR(64)     DEFAULT NULL COMMENT '크롤 커뮤니티 식별자 (natepan/dcinside 등)',
    ADD COLUMN IF NOT EXISTS source_url          VARCHAR(1024)   DEFAULT NULL COMMENT '크롤 원본 URL',
    ADD COLUMN IF NOT EXISTS source_original_title VARCHAR(512)  DEFAULT NULL COMMENT '크롤 원본 제목 스냅샷',
    ADD COLUMN IF NOT EXISTS source_original_body LONGTEXT       DEFAULT NULL COMMENT '크롤 원본 본문 스냅샷';
