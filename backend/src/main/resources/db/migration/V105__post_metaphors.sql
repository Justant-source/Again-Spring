-- V105: 메타포 일러스트 ID 목록 테이블 (순서 보존)
-- posts.metaphor_id는 대표값 유지, post_metaphors는 3-5개 순위 목록
CREATE TABLE post_metaphors (
    post_id VARCHAR(32) NOT NULL COMMENT 'Post ID (posts.id)',
    metaphor_id VARCHAR(64) NOT NULL COMMENT 'Metaphor illustration ID (e.g., empty-chair)',
    rank INT NOT NULL COMMENT 'Rank in the list (0=representative/first)',
    PRIMARY KEY (post_id, metaphor_id),
    CONSTRAINT fk_pm_post FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    INDEX idx_pm_rank (rank)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='메타포 순위 목록 (대표값은 posts.metaphor_id와 동기화)';
