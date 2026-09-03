-- ai_user_runtime.enabled는 읽는 코드가 없는 dead kill-switch였다(2026-09-03 확인). 실제 스위치는 ai_user_generation_config.ai_user_kill_switch.
ALTER TABLE ai_user_runtime DROP COLUMN enabled;
