-- IG/마케팅용 홍보 짧은 제목 (사연 생성 시 1회 LLM, 발행 시 추가 호출 없음)
ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS promo_title VARCHAR(20) DEFAULT NULL COMMENT '마케팅 훅 제목 ≤20자';
