-- V77: daily_stats에 post_count 컬럼 추가
-- DailyStats 엔티티에 postCount 필드(int)가 있으나 V19/V60 마이그레이션에 누락되어
-- "Unknown column 'post_count'" SQLGrammarException 발생 (관리자 통계 조회 실패).
-- vote_count(V60)와 짝을 이루는 일별 게시글 수 집계 컬럼.

ALTER TABLE daily_stats ADD COLUMN post_count INT NOT NULL DEFAULT 0 COMMENT '일별 게시글 수';
