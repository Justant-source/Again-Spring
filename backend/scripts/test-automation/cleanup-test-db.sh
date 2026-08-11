#!/usr/bin/env bash
# cleanup-test-db.sh
# e2e-realbe가 남긴 테스트 산출물을 삭제한다.
# (SeedDataLoader idempotent guard: test1@again.com 존재하는 한 재시드 없음)
#
# 권위본: docs/frontend/testing.md (e2e-realbe)
#
# 삭제 대상:
#   - test%@again.com 페르소나가 생성한 모든 커뮤니티 데이터
#   - is_guest=1 게스트 유저 행 + 그 산출물 (e2e가 수백 명 누적)
#   - e2e-signup%@example.com 일회용 가입 유저 + 산출물
#   - 제목 패턴 E2E / e2e / REPRO / [e2e] 포스트 안전망
#   - marketing_job / notifications / community_reports / password_reset_tokens
#   - ai_thread_plans/_items · ai_human_interaction_inbox · ai_post_interested_personas ·
#     ai_user_outbox 중 위 e2e post를 가리키는 행 (§2b) — posts를 raw SQL로 지우면
#     POST_DELETED outbox가 안 나가 orchestrator가 모르고 고아 REQUESTED 플랜을 만들고,
#     나중에 provider가 켜지면 존재하지 않는 글에 실제 LLM 토큰을 써서 생성을 시도한다.
#     e2e는 절대 LLM 토큰을 소비해서는 안 되므로 posts와 함께 반드시 지운다.
# 보존:
#   - mock_001 포스트 (global-setup이 INSERT IGNORE로 재시드)
#   - test%@again.com users 행 (페르소나 재시드 guard)
#
# 사용 (기본: dev:8090):
#   DB_CONTAINER=againspring-mariadb-dev E2E_ENV_FILE=env/.env.dev bash cleanup-test-db.sh
#   bash cleanup-test-db.sh   # 기본 = mariadb-dev + .env.dev
#
# 환경변수: E2E_ENV_FILE의 MARIADB_*가 권위본.
# ambient MARIADB_*는 BE와 다른 스택을 가리킬 수 있어 env 파일 값을 우선한다.
set -euo pipefail

ENV_FILE="${E2E_ENV_FILE:-$(dirname "$0")/../../../env/.env.dev}"

read_env() {
  local key="$1"
  if [[ -f "$ENV_FILE" ]]; then
    grep -E "^${key}=" "$ENV_FILE" | head -1 | cut -d= -f2- | tr -d '"' | tr -d "'"
  fi
}

if [[ -f "$ENV_FILE" ]]; then
  DB_PASS="$(read_env MARIADB_PASSWORD)"
  DB_NAME="$(read_env MARIADB_DATABASE)"
  DB_USER="$(read_env MARIADB_USER)"
else
  DB_PASS="${MARIADB_PASSWORD:-}"
  DB_NAME="${MARIADB_DATABASE:-}"
  DB_USER="${MARIADB_USER:-}"
fi

if [[ -z "$DB_PASS" ]]; then
  echo "ERROR: MARIADB_PASSWORD를 찾을 수 없습니다. $ENV_FILE 확인." >&2
  exit 1
fi

DB_CONTAINER="${DB_CONTAINER:-againspring-mariadb-dev}"
DB_NAME="${DB_NAME:-againspring}"
DB_USER="${DB_USER:-againspring}"

# E3: e2e cleanup은 dev만. prod 컨테이너 하드 거부.
if [[ "$DB_CONTAINER" == *prod* ]]; then
  echo "ERROR: prod DB cleanup 거부 ($DB_CONTAINER). e2e는 againspring-mariadb-dev만." >&2
  exit 1
fi
ALLOWED_CONTAINERS="againspring-mariadb-dev|againspring-mariadb"
if [[ ! "$DB_CONTAINER" =~ ^($ALLOWED_CONTAINERS)$ ]]; then
  echo "ERROR: 허용되지 않은 DB 컨테이너 ($DB_CONTAINER). 허용: $ALLOWED_CONTAINERS" >&2
  exit 1
fi

echo "[cleanup] 대상: $DB_CONTAINER / $DB_NAME / 패턴: test%@again.com + guest + e2e-signup% + E2E제목"

run_sql() {
  if command -v docker >/dev/null 2>&1; then
    if docker exec -i "$DB_CONTAINER" mariadb -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" >/dev/null 2>&1 <<'SQL_PROBE'
SELECT 1;
SQL_PROBE
    then
      # abort-source-on-error: 중간 DELETE 실패 시 조용히 "완료"로 끝나지 않게
      docker exec -i "$DB_CONTAINER" mariadb --abort-source-on-error -u "$DB_USER" -p"$DB_PASS" "$DB_NAME"
      return
    fi
  fi

  python3 "$(dirname "$0")/dev_db_sql.py" --env-file "$ENV_FILE"
}

run_sql <<'SQL'
-- ═══════════════════════════════════════════════════════════════════
-- §1: legacy 중재 테이블 정리 (V56에서 DROP됨 — 남아 있을 때만)
-- ═══════════════════════════════════════════════════════════════════
SET SESSION foreign_key_checks = 0;

SET @has_messages = (SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'messages');
SET @sql = IF(@has_messages > 0,
  'DELETE FROM messages WHERE session_id IN (
     SELECT id FROM sessions
     WHERE created_by_user_id IN (SELECT id FROM users WHERE email LIKE ''test%@again.com'')
        OR invitee_user_id    IN (SELECT id FROM users WHERE email LIKE ''test%@again.com'')
   )', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_turns = (SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'turns');
SET @sql = IF(@has_turns > 0,
  'DELETE FROM turns WHERE session_id IN (
     SELECT id FROM sessions
     WHERE created_by_user_id IN (SELECT id FROM users WHERE email LIKE ''test%@again.com'')
        OR invitee_user_id    IN (SELECT id FROM users WHERE email LIKE ''test%@again.com'')
   )', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_reports = (SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reports');
SET @sql = IF(@has_reports > 0,
  'DELETE FROM reports WHERE session_id IN (
     SELECT id FROM sessions
     WHERE created_by_user_id IN (SELECT id FROM users WHERE email LIKE ''test%@again.com'')
        OR invitee_user_id    IN (SELECT id FROM users WHERE email LIKE ''test%@again.com'')
   )', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_sessions = (SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sessions');
SET @sql = IF(@has_sessions > 0,
  'DELETE FROM sessions
   WHERE created_by_user_id IN (SELECT id FROM users WHERE email LIKE ''test%@again.com'')
      OR invitee_user_id    IN (SELECT id FROM users WHERE email LIKE ''test%@again.com'')',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ═══════════════════════════════════════════════════════════════════
-- §2: 커뮤니티 산출물 정리
--   collation: BINARY 비교로 utf8mb4_unicode_ci vs uca1400 충돌 회피
-- ═══════════════════════════════════════════════════════════════════

DROP TEMPORARY TABLE IF EXISTS _e2e_posts;
CREATE TEMPORARY TABLE _e2e_posts (
  id VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PRIMARY KEY
);
INSERT IGNORE INTO _e2e_posts (id)
SELECT id FROM posts
WHERE id <> 'mock_001'
  AND (
    CAST(author_id AS BINARY) IN (
      SELECT CAST(id AS BINARY) FROM users
      WHERE email LIKE 'test%@again.com'
         OR email LIKE 'e2e-signup%@example.com'
         OR is_guest = 1
    )
    OR title LIKE '%E2E%'
    OR title LIKE '%e2e%'
    OR user_title LIKE '%E2E%'
    OR user_title LIKE '%e2e%'
    OR title LIKE 'REPRO%'
    OR title LIKE '[e2e]%'
  );

DELETE FROM marketing_job
WHERE CAST(post_id AS BINARY) IN (SELECT CAST(id AS BINARY) FROM _e2e_posts)
   OR remote_job_id LIKE 'e2e-%'
   OR idempotency_key LIKE 'e2e-%';

DELETE FROM notifications
WHERE CAST(user_id AS BINARY) IN (
  SELECT CAST(id AS BINARY) FROM users
  WHERE email LIKE 'test%@again.com'
     OR email LIKE 'e2e-signup%@example.com'
     OR is_guest = 1
)
OR id LIKE 'noti_e2e%'
OR (ref_post_id IS NOT NULL AND CAST(ref_post_id AS BINARY) IN (SELECT CAST(id AS BINARY) FROM _e2e_posts));

DELETE FROM community_reports
WHERE CAST(reporter_user_id AS BINARY) IN (
  SELECT CAST(id AS BINARY) FROM users
  WHERE email LIKE 'test%@again.com'
     OR email LIKE 'e2e-signup%@example.com'
     OR is_guest = 1
)
OR (target_type = 'POST' AND CAST(target_id AS BINARY) IN (SELECT CAST(id AS BINARY) FROM _e2e_posts))
OR (target_type = 'COMMENT' AND target_id IN (
  SELECT CAST(id AS CHAR) FROM post_comments
  WHERE CAST(post_id AS BINARY) IN (SELECT CAST(id AS BINARY) FROM _e2e_posts)
     OR CAST(author_id AS BINARY) IN (
       SELECT CAST(id AS BINARY) FROM users
       WHERE email LIKE 'test%@again.com'
          OR email LIKE 'e2e-signup%@example.com'
          OR is_guest = 1
     )
));

DELETE FROM password_reset_tokens
WHERE email LIKE 'test%@again.com'
   OR email LIKE 'e2e-signup%@example.com'
   OR email LIKE 'e2e-reset%@example.com';

DELETE FROM email_verifications
WHERE email LIKE 'test%@again.com'
   OR email LIKE 'e2e-signup%@example.com';

DELETE FROM votes
WHERE CAST(voter_user_id AS BINARY) IN (
  SELECT CAST(id AS BINARY) FROM users
  WHERE email LIKE 'test%@again.com'
     OR email LIKE 'e2e-signup%@example.com'
     OR is_guest = 1
)
OR CAST(post_id AS BINARY) IN (SELECT CAST(id AS BINARY) FROM _e2e_posts);

DELETE FROM post_likes
WHERE CAST(user_id AS BINARY) IN (
  SELECT CAST(id AS BINARY) FROM users
  WHERE email LIKE 'test%@again.com'
     OR email LIKE 'e2e-signup%@example.com'
     OR is_guest = 1
)
OR comment_id IN (
  SELECT id FROM post_comments
  WHERE CAST(post_id AS BINARY) IN (SELECT CAST(id AS BINARY) FROM _e2e_posts)
     OR CAST(author_id AS BINARY) IN (
       SELECT CAST(id AS BINARY) FROM users
       WHERE email LIKE 'test%@again.com'
          OR email LIKE 'e2e-signup%@example.com'
          OR is_guest = 1
     )
);

DELETE FROM post_views
WHERE CAST(post_id AS BINARY) IN (SELECT CAST(id AS BINARY) FROM _e2e_posts);

DELETE FROM post_comments
WHERE CAST(author_id AS BINARY) IN (
  SELECT CAST(id AS BINARY) FROM users
  WHERE email LIKE 'test%@again.com'
     OR email LIKE 'e2e-signup%@example.com'
     OR is_guest = 1
)
OR CAST(post_id AS BINARY) IN (SELECT CAST(id AS BINARY) FROM _e2e_posts);

DELETE FROM vote_options
WHERE CAST(post_id AS BINARY) IN (SELECT CAST(id AS BINARY) FROM _e2e_posts);

SET @has_post_analysis = (SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'post_analysis');
SET @sql = IF(@has_post_analysis > 0,
  'DELETE FROM post_analysis WHERE CAST(post_id AS BINARY) IN (SELECT CAST(id AS BINARY) FROM _e2e_posts)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ═══════════════════════════════════════════════════════════════════
-- §2b: AI-user 파생 데이터 정리 (2026-08-01)
--   posts 삭제는 raw SQL이라 POST_DELETED outbox가 발행되지 않는다 → 백엔드가
--   정상적으로 처리했다면 호출됐을 planService.cancelPlanAndUnpublishedItemsForPost가
--   건너뛰어져서 ai_thread_plans/inbox/interested/outbox에 죽은 post_id를 가리키는
--   고아 행이 그대로 남는다. e2e-realbe를 돌릴 때마다(prod 배포 전 필수 게이트) 쌓여서,
--   나중에 provider가 켜지는 순간(새벽 배치 등) 존재하지 않는 글에 대한 LLM 생성을
--   실제로 시도해 토큰을 낭비하고 100% 실패한다(2026-08-01 인시던트: 172건).
--   e2e는 절대 LLM 토큰을 써서는 안 된다 — posts를 지우는 이 시점에 함께 지운다.
-- ═══════════════════════════════════════════════════════════════════

SET @has_plan_items = (SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_thread_plan_items');
SET @has_plans = (SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_thread_plans');
SET @sql = IF(@has_plan_items > 0 AND @has_plans > 0,
  'DELETE FROM ai_thread_plan_items WHERE plan_id IN (
     SELECT id FROM ai_thread_plans WHERE CAST(post_id AS BINARY) IN (SELECT CAST(id AS BINARY) FROM _e2e_posts)
   )', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(@has_plans > 0,
  'DELETE FROM ai_thread_plans WHERE CAST(post_id AS BINARY) IN (SELECT CAST(id AS BINARY) FROM _e2e_posts)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_inbox = (SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_human_interaction_inbox');
SET @sql = IF(@has_inbox > 0,
  'DELETE FROM ai_human_interaction_inbox WHERE CAST(post_id AS BINARY) IN (SELECT CAST(id AS BINARY) FROM _e2e_posts)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_interested = (SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_post_interested_personas');
SET @sql = IF(@has_interested > 0,
  'DELETE FROM ai_post_interested_personas WHERE CAST(post_id AS BINARY) IN (SELECT CAST(id AS BINARY) FROM _e2e_posts)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_outbox = (SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_user_outbox');
SET @sql = IF(@has_outbox > 0,
  'DELETE FROM ai_user_outbox WHERE aggregate_type = ''POST''
     AND CAST(aggregate_id AS BINARY) IN (SELECT CAST(id AS BINARY) FROM _e2e_posts)',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

DELETE FROM posts WHERE id IN (SELECT id FROM _e2e_posts);

DROP TEMPORARY TABLE IF EXISTS _e2e_posts;

-- ═══════════════════════════════════════════════════════════════════
-- §3: e2e-signup + 게스트 유저 행 정리 (test%@again.com 보존)
-- ═══════════════════════════════════════════════════════════════════

DELETE FROM email_verifications
WHERE email LIKE 'e2e-signup%@example.com';

DELETE FROM users
WHERE email LIKE 'e2e-signup%@example.com';

SET @has_guest_sessions = (SELECT COUNT(*) FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'guest_sessions');
SET @sql = IF(@has_guest_sessions > 0, 'DELETE FROM guest_sessions', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

DELETE FROM users WHERE is_guest = 1;

SET SESSION foreign_key_checks = 1;
SQL

echo "[cleanup] 완료"
