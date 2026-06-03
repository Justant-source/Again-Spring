-- V58: 사연 조회수 추가
-- posts 테이블에 view_count 컬럼 추가
-- post_views 테이블로 디바이스별 중복 조회 방지

ALTER TABLE posts ADD COLUMN view_count INT NOT NULL DEFAULT 0;

CREATE TABLE post_views (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  post_id    VARCHAR(32) NOT NULL,
  device_id  VARCHAR(64) NOT NULL,
  viewed_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_post_device (post_id, device_id),
  INDEX idx_post_views_post_id (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
