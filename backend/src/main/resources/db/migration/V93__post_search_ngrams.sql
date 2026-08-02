-- 광장 검색 슬라이스 ②: MariaDB는 MySQL ngram FULLTEXT 파서를 미지원(MDEV-10267).
-- 동일 효과 = title/body 문자 바이그램을 BTREE 테이블에 적재 후 AND 매칭.
CREATE TABLE IF NOT EXISTS post_search_ngrams (
    post_id VARCHAR(32)  NOT NULL,
    gram    VARCHAR(8)   NOT NULL,
    PRIMARY KEY (post_id, gram),
    KEY idx_post_search_ngrams_gram (gram)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
  COMMENT '게시글 검색용 문자 바이그램 (MariaDB ngram FULLTEXT 대체)';
