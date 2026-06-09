-- Phase R: drop all V15 legacy marketing tables extracted to Again-Spring-Marketing (ASM)
-- These tables were managed by the old marketing system (V28-V78 range)
-- Marketing operations now performed by ASM service via HTTP API

DROP TABLE IF EXISTS social_publish_results;
DROP TABLE IF EXISTS social_sessions;
DROP TABLE IF EXISTS social_credentials;
DROP TABLE IF EXISTS marketing_content_templates;
DROP TABLE IF EXISTS marketing_hashtag_library;
DROP TABLE IF EXISTS marketing_audit_logs;
DROP TABLE IF EXISTS marketing_usage_logs;
DROP TABLE IF EXISTS marketing_contents;
DROP TABLE IF EXISTS marketing_simulations;
DROP TABLE IF EXISTS marketing_source_stories;

-- Remove marketing_system seed user if present
DELETE FROM users WHERE username = 'marketing_system' LIMIT 1;
