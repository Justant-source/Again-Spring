-- V60: daily_stats에 오늘 투표수 컬럼 추가
ALTER TABLE daily_stats ADD COLUMN vote_count INT NOT NULL DEFAULT 0;
