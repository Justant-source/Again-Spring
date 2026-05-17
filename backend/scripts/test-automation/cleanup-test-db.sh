#!/usr/bin/env bash
# cleanup-test-db.sh
# test%@again.com 페르소나의 세션/메시지/turn/리포트를 삭제한다. users 행은 보존한다.
# (SeedDataLoader idempotent guard: test1@again.com 존재하는 한 재시드 없음)
#
# 권위본: backend/docs/test-automation.md §4
#
# 사용:
#   bash cleanup-test-db.sh
#   E2E_ENV_FILE=/path/.env.dev bash cleanup-test-db.sh
#
# 환경변수 우선순위: 명시 env > E2E_ENV_FILE > 기본값(env/.env.dev)

set -euo pipefail

ENV_FILE="${E2E_ENV_FILE:-$(dirname "$0")/../../../env/.env.dev}"

# env 파일에서 MARIADB_PASSWORD 읽기 (git에 평문 비밀 커밋 금지)
if [[ -f "$ENV_FILE" ]]; then
  DB_PASS="${MARIADB_PASSWORD:-$(grep -E '^MARIADB_PASSWORD=' "$ENV_FILE" | cut -d= -f2- | tr -d '"' | tr -d "'")}"
else
  DB_PASS="${MARIADB_PASSWORD:-}"
fi

if [[ -z "$DB_PASS" ]]; then
  echo "ERROR: MARIADB_PASSWORD를 찾을 수 없습니다. env/.env.dev 파일을 확인하세요." >&2
  exit 1
fi

DB_CONTAINER="${DB_CONTAINER:-againspring-mariadb-dev}"
DB_NAME="${MARIADB_DATABASE:-againspring_dev}"
DB_USER="${MARIADB_USER:-againspring}"

# prod-like 컨테이너 이름 안전 가드
if [[ "$DB_CONTAINER" == *prod* ]]; then
  echo "ERROR: prod 컨테이너 감지 ($DB_CONTAINER). 클린업 거부." >&2
  exit 1
fi

echo "[cleanup] 대상: $DB_CONTAINER / $DB_NAME / 패턴: test%@again.com"

docker exec -i "$DB_CONTAINER" mariadb -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" <<'SQL'
-- test%@again.com 페르소나 세션 클린업 (users 행 보존)
SET SESSION foreign_key_checks = 0;

DELETE FROM messages
WHERE session_id IN (
  SELECT id FROM sessions
  WHERE created_by_user_id IN (SELECT id FROM users WHERE email LIKE 'test%@again.com')
     OR invitee_user_id    IN (SELECT id FROM users WHERE email LIKE 'test%@again.com')
);

DELETE FROM turns
WHERE session_id IN (
  SELECT id FROM sessions
  WHERE created_by_user_id IN (SELECT id FROM users WHERE email LIKE 'test%@again.com')
     OR invitee_user_id    IN (SELECT id FROM users WHERE email LIKE 'test%@again.com')
);

DELETE FROM reports
WHERE session_id IN (
  SELECT id FROM sessions
  WHERE created_by_user_id IN (SELECT id FROM users WHERE email LIKE 'test%@again.com')
     OR invitee_user_id    IN (SELECT id FROM users WHERE email LIKE 'test%@again.com')
);

DELETE FROM sessions
WHERE created_by_user_id IN (SELECT id FROM users WHERE email LIKE 'test%@again.com')
   OR invitee_user_id    IN (SELECT id FROM users WHERE email LIKE 'test%@again.com');

SET SESSION foreign_key_checks = 1;
SQL

echo "[cleanup] 완료"
