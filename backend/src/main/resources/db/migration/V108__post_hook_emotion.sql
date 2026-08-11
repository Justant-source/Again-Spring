-- Master SNS hook emotion for posts (Phase 1 hook+emotion).
-- promo_title semantics: provocative master hook (not title character-copy).

ALTER TABLE posts
  ADD COLUMN hook_emotion VARCHAR(16) NULL
    COMMENT 'SNS hook emotion: shock|anger|tension|sad|hype'
    AFTER promo_title;
