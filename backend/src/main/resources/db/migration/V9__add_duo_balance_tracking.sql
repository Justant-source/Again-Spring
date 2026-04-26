-- V9: Per-user accumulated emotion intensity (Phase C)
-- Combined with existing user_{a,b}_message_count for Duo balance heuristics.
-- Intensity is a 0.0–1.0 running average computed from per-turn 4 Horsemen totals.

ALTER TABLE sessions
    ADD COLUMN user_a_emotion_intensity DECIMAL(3,2) DEFAULT NULL COMMENT 'Cumulative emotion intensity 0.0-1.0 for USER_A',
    ADD COLUMN user_b_emotion_intensity DECIMAL(3,2) DEFAULT NULL COMMENT 'Cumulative emotion intensity 0.0-1.0 for USER_B';
