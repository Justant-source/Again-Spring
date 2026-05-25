-- V35: 콘텐츠 상태에 'generating' 추가
-- 비동기 생성 중인 상태를 표시하기 위해 ENUM에 generating 값 추가
ALTER TABLE marketing_contents
    MODIFY COLUMN status ENUM('draft','review','approved','exported','rejected','generating')
        NOT NULL DEFAULT 'draft'
        COMMENT 'Content approval status (generating=LLM 처리 중)';
