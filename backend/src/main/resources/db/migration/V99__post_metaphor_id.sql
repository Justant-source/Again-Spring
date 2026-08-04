-- V99: metaphor illustration id matched at story creation (AI PLAN)
ALTER TABLE posts
    ADD COLUMN metaphor_id VARCHAR(64) NULL COMMENT 'Metaphor illustration id (empty-chair, …) matched at create' AFTER promo_title;
