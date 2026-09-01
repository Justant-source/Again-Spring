-- Telegram persona drill removed. Keep TIMELINE gold; DELETED_AUTO is a new
-- source written by the dawn job (VARCHAR, no schema change).
DELETE FROM x_persona_example WHERE source = 'DRILL';
