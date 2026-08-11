-- Phase 2.1: per-platform marketing daily caps + score weight defaults (plan §3 / §5.3).
-- Legacy marketing.daily_text_cap / marketing.daily_video_cap remain as deprecated fallbacks
-- when a platform key is unset (see MarketingQuotaService).

INSERT INTO system_setting (setting_key, setting_value, updated_at, updated_by)
VALUES
  ('marketing.cap.x_thread', '3', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.cap.instagram_feed', '3', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.cap.instagram_reels', '3', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.cap.youtube_shorts', '3', CURRENT_TIMESTAMP(6), 'migration:V109')
ON DUPLICATE KEY UPDATE setting_key = setting_key;

-- Score weights: Reels
INSERT INTO system_setting (setting_key, setting_value, updated_at, updated_by)
VALUES
  ('marketing.score.weights.instagram_reels.hook', '2.0', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.score.weights.instagram_reels.vote_skew', '1.5', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.score.weights.instagram_reels.comments', '1.2', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.score.weights.instagram_reels.votes', '0.8', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.score.weights.instagram_reels.views', '0.3', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.score.weights.instagram_reels.has_partner', '1.0', CURRENT_TIMESTAMP(6), 'migration:V109')
ON DUPLICATE KEY UPDATE setting_key = setting_key;

-- Shorts
INSERT INTO system_setting (setting_key, setting_value, updated_at, updated_by)
VALUES
  ('marketing.score.weights.youtube_shorts.hook', '1.2', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.score.weights.youtube_shorts.vote_skew', '1.8', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.score.weights.youtube_shorts.comments', '1.0', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.score.weights.youtube_shorts.votes', '1.0', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.score.weights.youtube_shorts.views', '0.5', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.score.weights.youtube_shorts.has_partner', '0.8', CURRENT_TIMESTAMP(6), 'migration:V109')
ON DUPLICATE KEY UPDATE setting_key = setting_key;

-- X
INSERT INTO system_setting (setting_key, setting_value, updated_at, updated_by)
VALUES
  ('marketing.score.weights.x_thread.hook', '1.0', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.score.weights.x_thread.vote_skew', '1.0', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.score.weights.x_thread.comments', '2.0', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.score.weights.x_thread.votes', '0.8', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.score.weights.x_thread.views', '0.4', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.score.weights.x_thread.has_partner', '1.5', CURRENT_TIMESTAMP(6), 'migration:V109')
ON DUPLICATE KEY UPDATE setting_key = setting_key;

-- IG feed
INSERT INTO system_setting (setting_key, setting_value, updated_at, updated_by)
VALUES
  ('marketing.score.weights.instagram_feed.hook', '1.5', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.score.weights.instagram_feed.vote_skew', '1.2', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.score.weights.instagram_feed.comments', '1.0', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.score.weights.instagram_feed.votes', '0.8', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.score.weights.instagram_feed.views', '0.3', CURRENT_TIMESTAMP(6), 'migration:V109'),
  ('marketing.score.weights.instagram_feed.has_partner', '1.2', CURRENT_TIMESTAMP(6), 'migration:V109')
ON DUPLICATE KEY UPDATE setting_key = setting_key;
