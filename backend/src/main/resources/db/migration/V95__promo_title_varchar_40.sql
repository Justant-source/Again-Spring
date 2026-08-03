-- IG 훅 제목 상한 20 → 40자 (instagram-feed-strategy.md)
ALTER TABLE posts
    MODIFY COLUMN promo_title VARCHAR(40) DEFAULT NULL COMMENT '마케팅 훅 제목 ≤40자';
