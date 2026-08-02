-- Marketing capture: 1-based last front-half newline block (null = no split / ≤12 blocks)
ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS capture_split_after_line INT DEFAULT NULL
        COMMENT 'X/IG 캡쳐 전반부 끝 개행블록(1-based); NULL=미분할';
