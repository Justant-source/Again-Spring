-- V98: multi-part marketing capture splits (JSON arrays of 1-based block cuts)
ALTER TABLE posts
    ADD COLUMN capture_split_after_lines JSON NULL COMMENT 'X/IG capture cuts: 1-based last block of each part except final' AFTER capture_split_after_line,
    ADD COLUMN partner_capture_split_after_lines JSON NULL COMMENT 'Partner body capture cuts (same semantics)' AFTER partner_body_published;

-- Backfill: promote legacy single INT into a one-element JSON array
UPDATE posts
SET capture_split_after_lines = JSON_ARRAY(capture_split_after_line)
WHERE capture_split_after_line IS NOT NULL
  AND capture_split_after_lines IS NULL;
