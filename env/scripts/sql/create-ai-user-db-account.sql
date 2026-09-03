-- orchestrator 전용 DB 계정. 소유 테이블만 쓰기, 나머지는 읽기. root로 1회 실행:
--   docker exec -i againspring-mariadb-prod sh -c 'mariadb -uroot -p"$MARIADB_ROOT_PASSWORD"' < env/scripts/sql/create-ai-user-db-account.sql
-- 실행 전 :DBNAME: 과 :PASSWORD: 를 치환한다 (sed). dev는 컨테이너·DB명만 바꿔 같은 파일.
CREATE USER IF NOT EXISTS 'ai_user_orch'@'%' IDENTIFIED BY ':PASSWORD:';
GRANT SELECT ON `:DBNAME:`.* TO 'ai_user_orch'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`flyway_schema_history_aiuser` TO 'ai_user_orch'@'%';
-- orchestrator Flyway 소유 테이블 (docs/backend/40-data.md 소유 표와 동일 목록)
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`personas` TO 'ai_user_orch'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`persona_relationships` TO 'ai_user_orch'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`persona_seen_posts` TO 'ai_user_orch'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`persona_action_log` TO 'ai_user_orch'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`persona_daily_quota` TO 'ai_user_orch'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`persona_history_entries` TO 'ai_user_orch'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`persona_life_state` TO 'ai_user_orch'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`persona_fact_assertions` TO 'ai_user_orch'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`persona_semantic_capsules` TO 'ai_user_orch'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`persona_match_audits` TO 'ai_user_orch'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`post_analysis` TO 'ai_user_orch'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`ai_user_runtime` TO 'ai_user_orch'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`ai_thread_plans` TO 'ai_user_orch'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`ai_thread_plan_items` TO 'ai_user_orch'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`ai_human_interaction_inbox` TO 'ai_user_orch'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`ai_scheduled_posts` TO 'ai_user_orch'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`ai_scheduled_partner_answers` TO 'ai_user_orch'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`ai_post_interested_personas` TO 'ai_user_orch'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`llm_generation_gate` TO 'ai_user_orch'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES ON `:DBNAME:`.`daily_planner_retry_log` TO 'ai_user_orch'@'%';
-- 명시 예외: outbox 상태 갱신
GRANT UPDATE ON `:DBNAME:`.`ai_user_outbox` TO 'ai_user_orch'@'%';
FLUSH PRIVILEGES;
