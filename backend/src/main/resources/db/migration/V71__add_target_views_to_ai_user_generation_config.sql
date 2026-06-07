-- 조회수 생성 정책 추가 (AI 봇이 글을 조회하여 조회수 보정)
-- 투표·댓글 수에 비례하여 조회수를 자동으로 증가

ALTER TABLE ai_user_generation_config
ADD COLUMN target_views INT NOT NULL DEFAULT 10000 AFTER target_likes;

-- 제약조건 추가 (0~10000)
ALTER TABLE ai_user_generation_config
ADD CONSTRAINT chk_gen_target_views CHECK (target_views BETWEEN 0 AND 10000);
