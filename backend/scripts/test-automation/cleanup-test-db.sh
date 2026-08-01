#!/usr/bin/env bash
# cleanup-test-db.sh
# test%@again.com 페르소나의 세션/메시지/turn/리포트를 삭제한다. users 행은 보존한다.
# 커뮤니티 산출물(게시글·댓글·투표·좋아요·조회)도 삭제한다 (2026-06-07 추가).
# (SeedDataLoader idempotent guard: test1@again.com 존재하는 한 재시드 없음)
#
# 권위본: docs/frontend/testing.md (e2e-realbe)
#
# 삭제 대상:
#   - test%@again.com 페르소나가 생성한 모든 데이터
#   - is_guest=1 게스트가 생성한 커뮤니티 데이터
#   - e2e-signup%@example.com 이메일로 가입한 일회용 테스트 유저와 그 산출물
#   - mock_001 포스트는 보존 (global-setup이 INSERT IGNORE로 재시드함)
#   - users 행은 보존 (페르소나 재시드 guard 유지)
#
# 사용 (미공개: prod:8091 기본):
#   DB_CONTAINER=againspring-mariadb-prod E2E_ENV_FILE=env/.env.prod bash cleanup-test-db.sh
#   bash cleanup-test-db.sh   # 기본 = mariadb-prod + .env.prod
#
# 환경변수 우선순위: 명시 env > E2E_ENV_FILE > 기본값(env/.env.prod)

set -euo pipefail

ENV_FILE="${E2E_ENV_FILE:-$(dirname "$0")/../../../env/.env.prod}"

# env 파일에서 MARIADB_PASSWORD 읽기 (git에 평문 비밀 커밋 금지)
if [[ -f "$ENV_FILE" ]]; then
  DB_PASS="${MARIADB_PASSWORD:-$(grep -E '^MARIADB_PASSWORD=' "$ENV_FILE" | cut -d= -f2- | tr -d '"' | tr -d "'")}"
else
  DB_PASS="${MARIADB_PASSWORD:-}"
fi

if [[ -z "$DB_PASS" ]]; then
  echo "ERROR: MARIADB_PASSWORD를 찾을 수 없습니다. env/.env.prod 파일을 확인하세요." >&2
  exit 1
fi

DB_CONTAINER="${DB_CONTAINER:-againspring-mariadb-prod}"
DB_NAME="${MARIADB_DATABASE:-againspring}"
DB_USER="${MARIADB_USER:-againspring}"

# 허용: localhost 대상 로컬 docker 컨테이너만.
# 미공개(prelaunch): againspring-mariadb-prod 허용. 그 외 *prod* 이름은 거부.
# 정식 공개 후: prod cleanup 재검토·강화 필요.
ALLOWED_CONTAINERS="againspring-mariadb-prod|againspring-mariadb-dev|againspring-mariadb"
if [[ ! "$DB_CONTAINER" =~ ^($ALLOWED_CONTAINERS)$ ]]; then
  echo "ERROR: 허용되지 않은 DB 컨테이너 ($DB_CONTAINER). 허용: $ALLOWED_CONTAINERS" >&2
  exit 1
fi

echo "[cleanup] 대상: $DB_CONTAINER / $DB_NAME / 패턴: test%@again.com + guest + e2e-signup%"

run_sql() {
  if command -v docker >/dev/null 2>&1; then
    if docker exec -i "$DB_CONTAINER" mariadb -u "$DB_USER" -p"$DB_PASS" "$DB_NAME" >/dev/null 2>&1 <<'SQL_PROBE'
SELECT 1;
SQL_PROBE
    then
      docker exec -i "$DB_CONTAINER" mariadb -u "$DB_USER" -p"$DB_PASS" "$DB_NAME"
      return
    fi
  fi

  python3 "$(dirname "$0")/dev_db_sql.py" --env-file "$ENV_FILE"
}

run_sql <<'SQL'
-- ═══════════════════════════════════════════════════════════════════
-- §1: 기존 세션/메시지/turn/리포트 정리 (test%@again.com 페르소나)
-- ═══════════════════════════════════════════════════════════════════
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

-- ═══════════════════════════════════════════════════════════════════
-- §2: 커뮤니티 산출물 정리
--   대상: test%@again.com 페르소나 + is_guest=1 게스트 + e2e-signup% 유저
--   보존: mock_001 포스트, users 행 전체
-- ═══════════════════════════════════════════════════════════════════

-- 테스트 유저 ID 집합 (1회 계산, 재사용)
-- test 페르소나 + 게스트 + e2e-signup
-- 게스트는 dev DB에서 모두 폐기 가능한 데이터로 취급

-- 2-A. 투표 삭제 (FK 선행)
DELETE FROM votes
WHERE voter_user_id IN (
  SELECT id FROM users
  WHERE email LIKE 'test%@again.com'
     OR email LIKE 'e2e-signup%@example.com'
     OR is_guest = 1
);

-- 2-B. 댓글 좋아요 삭제
DELETE FROM post_likes
WHERE user_id IN (
  SELECT id FROM users
  WHERE email LIKE 'test%@again.com'
     OR email LIKE 'e2e-signup%@example.com'
     OR is_guest = 1
);

-- 2-C. 조회 기록 삭제 (테스트 유저가 작성한 포스트의 조회 포함)
-- post_views.post_id collation=utf8mb4_uca1400_ai_ci (V58), posts.id=utf8mb4_unicode_ci → CONVERT 필요
DELETE FROM post_views
WHERE CONVERT(post_id USING utf8mb4) COLLATE utf8mb4_unicode_ci IN (
  SELECT id FROM posts
  WHERE author_id IN (
    SELECT id FROM users
    WHERE email LIKE 'test%@again.com'
       OR email LIKE 'e2e-signup%@example.com'
       OR is_guest = 1
  )
  AND id <> 'mock_001'
);

-- 2-D. 댓글 삭제 (테스트 유저가 작성한 포스트의 모든 댓글 포함)
DELETE FROM post_comments
WHERE author_id IN (
  SELECT id FROM users
  WHERE email LIKE 'test%@again.com'
     OR email LIKE 'e2e-signup%@example.com'
     OR is_guest = 1
)
OR post_id IN (
  SELECT id FROM posts
  WHERE author_id IN (
    SELECT id FROM users
    WHERE email LIKE 'test%@again.com'
       OR email LIKE 'e2e-signup%@example.com'
       OR is_guest = 1
  )
  AND id <> 'mock_001'
);

-- 2-E. jurors 삭제 (포스트 소속)
DELETE FROM jurors
WHERE post_id IN (
  SELECT id FROM posts
  WHERE author_id IN (
    SELECT id FROM users
    WHERE email LIKE 'test%@again.com'
       OR email LIKE 'e2e-signup%@example.com'
       OR is_guest = 1
  )
  AND id <> 'mock_001'
);

-- 2-F. vote_options 삭제 (포스트 소속, mock_001 제외)
DELETE FROM vote_options
WHERE post_id IN (
  SELECT id FROM posts
  WHERE author_id IN (
    SELECT id FROM users
    WHERE email LIKE 'test%@again.com'
       OR email LIKE 'e2e-signup%@example.com'
       OR is_guest = 1
  )
  AND id <> 'mock_001'
);

-- 2-G. 포스트 삭제 (mock_001 보존, global-setup이 재시드)
DELETE FROM posts
WHERE author_id IN (
  SELECT id FROM users
  WHERE email LIKE 'test%@again.com'
     OR email LIKE 'e2e-signup%@example.com'
     OR is_guest = 1
)
AND id <> 'mock_001';

-- ═══════════════════════════════════════════════════════════════════
-- §3: e2e-signup 일회용 유저 정리
--   email_verifications 정리 후 users 행 삭제 (페르소나 행은 보존)
-- ═══════════════════════════════════════════════════════════════════

DELETE FROM email_verifications
WHERE email LIKE 'e2e-signup%@example.com';

-- e2e-signup 유저 행 삭제 (test%@again.com은 보존)
DELETE FROM users
WHERE email LIKE 'e2e-signup%@example.com';

SET SESSION foreign_key_checks = 1;
SQL

echo "[cleanup] 완료"
