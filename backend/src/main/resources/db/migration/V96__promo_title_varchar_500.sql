-- IG 훅 제목: 개행 포함 원제 복제 (한 줄 ≤10자). VARCHAR(40) → 500
ALTER TABLE posts
    MODIFY COLUMN promo_title VARCHAR(500) DEFAULT NULL COMMENT 'IG 훅 제목(원제 복제+의미줄바꿈)';
