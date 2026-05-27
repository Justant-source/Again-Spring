-- V41: Extend body_text column from MEDIUMTEXT to LONGTEXT
-- Needed to store structured JSON (card-news slides, image slot markers) alongside longer blog posts.
-- No table/index changes — zero-downtime column type upgrade.
ALTER TABLE marketing_contents
    MODIFY COLUMN body_text LONGTEXT;
