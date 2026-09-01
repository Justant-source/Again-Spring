#!/usr/bin/env bash
#
# scripts/verify-deploy.sh — 배포 실물 검증 (읽기 전용)
#
# 배경: docs/_active/deploy-verification.md
# 이 프로젝트의 반복 사고는 "테스트·헬스체크는 통과하는데 기능만 죽는" 유형이었다
# (UTM/세션 계측 필드명 snake/camel 불일치로 전량 유실, NEXT_PUBLIC_* 빌드 인자 누락 등).
# 이 스크립트는 배포 직후 실물 데이터로 그걸 잡는다.
#
# 사용법:
#   scripts/verify-deploy.sh dev
#   scripts/verify-deploy.sh prod
#   VERIFY_WINDOW_MIN=30 scripts/verify-deploy.sh dev   # 계측 조회 윈도우(분) 조정, 기본 10분
#
# 🚨 읽기 전용 검증만 한다 — DB에 쓰기 없음, 배포/재기동 없음.
# 🚨 prod 대상이어도 SELECT 조회만 한다.
# 🔑 비밀번호는 이 스크립트에 하드코딩하지 않는다 — env/.env.<target> 파일에서 읽고,
#    DB 조회는 컨테이너 안에서 실행하며 비밀번호는 CLI 인자(=docker ps/inspect에 노출)가
#    아니라 stdin 파이프로만 컨테이너에 전달한다.

set -euo pipefail

# ────────────────────────────────────────────────────────────
# 0. 인자 파싱 + 대상별 설정
# ────────────────────────────────────────────────────────────

usage() {
    echo "사용법: $0 <dev|prod>" >&2
    exit 2
}

TARGET="${1:-}"
case "$TARGET" in
    dev)
        PORT=8090
        ENV_FILE_NAME=".env.dev"
        MARIADB_CONTAINER="againspring-mariadb-dev"
        BACKEND_CONTAINER="againspring-backend-dev"
        FRONTEND_CONTAINER="againspring-frontend-dev"
        DEFAULT_APP_URL="https://dev.againspring.net"
        FORBIDDEN_APP_URL="https://againspring.net"
        ;;
    prod)
        PORT=8091
        ENV_FILE_NAME=".env.prod"
        MARIADB_CONTAINER="againspring-mariadb-prod"
        BACKEND_CONTAINER="againspring-backend-prod"
        FRONTEND_CONTAINER="againspring-frontend-prod"
        DEFAULT_APP_URL="https://againspring.net"
        FORBIDDEN_APP_URL="https://dev.againspring.net"
        ;;
    *)
        usage
        ;;
esac

WINDOW_MIN="${VERIFY_WINDOW_MIN:-10}"
if ! [[ "$WINDOW_MIN" =~ ^[0-9]+$ ]]; then
    echo "VERIFY_WINDOW_MIN은 숫자여야 한다: $WINDOW_MIN" >&2
    exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_DIR="$REPO_ROOT/env"
ENV_FILE="$ENV_DIR/$ENV_FILE_NAME"
BASE_URL="http://localhost:${PORT}"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

# ────────────────────────────────────────────────────────────
# 1. 출력 헬퍼 + 판정 집계
# ────────────────────────────────────────────────────────────

PASS_COUNT=0
WARN_COUNT=0
SKIP_COUNT=0
FAIL_COUNT=0

pass() { echo "[PASS] $1"; PASS_COUNT=$((PASS_COUNT + 1)); }
warn() { echo "[WARN] $1"; WARN_COUNT=$((WARN_COUNT + 1)); }
skip() { echo "[SKIP] $1"; SKIP_COUNT=$((SKIP_COUNT + 1)); }
fail() { echo "[FAIL] $1"; FAIL_COUNT=$((FAIL_COUNT + 1)); }

echo "=== 배포 실물 검증: $TARGET (포트 $PORT, 조회 윈도우 ${WINDOW_MIN}분) ==="
echo

# ────────────────────────────────────────────────────────────
# 2. 사전 조건 확인 — 환경 파일 · 컨테이너 기동 여부
# ────────────────────────────────────────────────────────────

if [[ ! -f "$ENV_FILE" ]]; then
    fail "환경 파일 없음: $ENV_FILE — DB 조회 항목을 건너뛴다"
    DB_READY=0
else
    DB_READY=1
fi

container_running() {
    docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null | grep -q '^true$'
}

if [[ "$DB_READY" -eq 1 ]] && ! container_running "$MARIADB_CONTAINER"; then
    fail "DB 컨테이너 미기동: $MARIADB_CONTAINER — 계측 항목(2·3) 조회 불가"
    DB_READY=0
fi

if ! container_running "$BACKEND_CONTAINER"; then
    warn "백엔드 컨테이너 미기동: $BACKEND_CONTAINER (헬스 확인은 계속 시도)"
fi

# ────────────────────────────────────────────────────────────
# 3. HTTP GET 헬퍼 — curl 우선, 없으면 wget 폴백
#    fetch <url> <출력파일> → stdout으로 HTTP 상태코드만 반환
# ────────────────────────────────────────────────────────────

fetch() {
    local url="$1" outfile="$2"
    if command -v curl >/dev/null 2>&1; then
        curl -sS -o "$outfile" -w '%{http_code}' --max-time 10 "$url" 2>/dev/null || echo "000"
    elif command -v wget >/dev/null 2>&1; then
        local headers="$outfile.headers"
        wget -q -S -O "$outfile" "$url" 2>"$headers" || true
        grep -oE 'HTTP/[0-9.]+ [0-9]+' "$headers" 2>/dev/null | tail -1 | awk '{print $2}' || echo "000"
    else
        echo "000"
    fi
}

# ────────────────────────────────────────────────────────────
# 4. 항목 1 — deep 헬스 (/api/health/deep)
#    계약: DB 정상 → HTTP 200 + {"status":"UP","db":"ok",...}
#          DB 장애 → HTTP 503
# ────────────────────────────────────────────────────────────

HEALTH_OUT="$TMP_DIR/health_deep.json"
HEALTH_STATUS="$(fetch "$BASE_URL/api/health/deep" "$HEALTH_OUT")"
HEALTH_BODY="$(cat "$HEALTH_OUT" 2>/dev/null || true)"

if [[ "$HEALTH_STATUS" == "200" ]]; then
    if grep -qE '"status"[[:space:]]*:[[:space:]]*"UP"' "$HEALTH_OUT" 2>/dev/null \
       && grep -qE '"db"[[:space:]]*:[[:space:]]*"ok"' "$HEALTH_OUT" 2>/dev/null; then
        pass "deep 헬스: HTTP 200, status=UP, db=ok"
    else
        fail "deep 헬스: HTTP 200이지만 계약 불일치 (status=UP/db=ok 필드 없음). body: ${HEALTH_BODY:0:200}"
    fi
elif [[ "$HEALTH_STATUS" == "503" ]]; then
    fail "deep 헬스: HTTP 503 — DB 또는 컴포넌트 장애. body: ${HEALTH_BODY:0:200}"
else
    fail "deep 헬스: 예상치 못한 HTTP $HEALTH_STATUS (200 UP 또는 503 DOWN 계약 위반 — /api/health/deep 미구현이거나 배포 전일 수 있음). body: ${HEALTH_BODY:0:200}"
fi

# ────────────────────────────────────────────────────────────
# 5. DB 조회 헬퍼 — 비밀번호는 stdin으로만 컨테이너에 전달
#    (CLI 인자로 넘기면 docker ps/inspect에 평문 노출되므로 금지)
# ────────────────────────────────────────────────────────────

get_env_var() {
    local key="$1"
    grep -E "^${key}=" "$ENV_FILE" 2>/dev/null | tail -1 | cut -d'=' -f2-
}

run_sql() {
    local sql="$1"
    { printf '%s\n' "$MARIADB_PASSWORD"; printf '%s\n' "$sql"; } | \
        docker exec -i \
            -e SQL_USER="$MARIADB_USER" \
            -e SQL_DB="$MARIADB_DATABASE" \
            "$MARIADB_CONTAINER" \
            sh -c 'read -r MYSQL_PWD; export MYSQL_PWD; exec mariadb -N -B -u"$SQL_USER" "$SQL_DB"'
}

# ────────────────────────────────────────────────────────────
# 6. 항목 2·3 — 계측 적재 + 세션 키 채움
# ────────────────────────────────────────────────────────────

if [[ "$DB_READY" -eq 1 ]]; then
    MARIADB_DATABASE="$(get_env_var MARIADB_DATABASE)"
    MARIADB_USER="$(get_env_var MARIADB_USER)"
    MARIADB_PASSWORD="$(get_env_var MARIADB_PASSWORD)"

    if [[ -z "$MARIADB_USER" || -z "$MARIADB_PASSWORD" || -z "$MARIADB_DATABASE" ]]; then
        fail "환경 파일에 MARIADB_USER/PASSWORD/DATABASE 중 누락 — 계측 항목(2·3) 조회 불가"
    else
        SQL="SELECT COUNT(*) AS total, \
SUM(session_key IS NULL) AS session_null, \
SUM(visitor_key IS NULL) AS visitor_null, \
SUM(session_key IS NULL AND visitor_key IS NULL) AS both_null \
FROM visit_events \
WHERE occurred_at >= (NOW() - INTERVAL ${WINDOW_MIN} MINUTE);"

        if ! VISIT_ROW="$(run_sql "$SQL" 2>"$TMP_DIR/sql_err.log")"; then
            fail "계측 DB 조회 실패 ($MARIADB_CONTAINER) — $(tail -1 "$TMP_DIR/sql_err.log" 2>/dev/null)"
        else
            TOTAL="$(echo "$VISIT_ROW" | awk -F'\t' '{print $1}')"
            SESSION_NULL="$(echo "$VISIT_ROW" | awk -F'\t' '{print $2}')"
            VISITOR_NULL="$(echo "$VISIT_ROW" | awk -F'\t' '{print $3}')"
            BOTH_NULL="$(echo "$VISIT_ROW" | awk -F'\t' '{print $4}')"

            if [[ -z "$TOTAL" ]]; then
                fail "계측 DB 조회 결과를 파싱할 수 없음 (raw: $VISIT_ROW)"
            elif [[ "$TOTAL" -eq 0 ]]; then
                skip "계측 적재: 최근 ${WINDOW_MIN}분간 visit_events 0건 — 판정 불가(방금 배포한 dev라면 정상, 실 방문 유도 후 재확인)"
                skip "세션 키 채움: 최근 ${WINDOW_MIN}분간 방문 없음 — 판정 불가"
            else
                pass "계측 적재: 최근 ${WINDOW_MIN}분간 visit_events ${TOTAL}건 적재됨"

                if [[ "$BOTH_NULL" -eq "$TOTAL" ]]; then
                    fail "세션 키 채움: ${TOTAL}건 전량 session_key·visitor_key NULL — 2026-08-29 snake/camel 사고 재발 의심 (프론트 페이로드 필드명 ↔ 백엔드 DTO 대조 필요)"
                else
                    PCT_BOTH_NULL=$(( BOTH_NULL * 100 / TOTAL ))
                    if [[ "$BOTH_NULL" -gt 0 ]]; then
                        warn "세션 키 채움: ${TOTAL}건 중 ${BOTH_NULL}건(${PCT_BOTH_NULL}%) 두 키 모두 NULL — 전량은 아니나 일부 유실, 원인 확인 권장 (session_null=${SESSION_NULL}, visitor_null=${VISITOR_NULL})"
                    else
                        pass "세션 키 채움: ${TOTAL}건 중 두 키 모두 NULL인 행 0건 (session_null=${SESSION_NULL}, visitor_null=${VISITOR_NULL})"
                    fi
                fi
            fi
        fi
    fi
else
    skip "계측 적재: DB 조회 사전조건 불충족으로 건너뜀"
    skip "세션 키 채움: DB 조회 사전조건 불충족으로 건너뜀"
fi

# ────────────────────────────────────────────────────────────
# 7. 항목 4 — 빌드 주입값 (NEXT_PUBLIC_APP_URL)
#    dev 이미지에 prod 도메인이, 혹은 그 반대가 박혀 있으면 빌드 인자 오주입.
# ────────────────────────────────────────────────────────────

APP_URL_FROM_ENV="$( [[ "$DB_READY" -eq 1 ]] && get_env_var APP_URL || true )"
EXPECTED_APP_URL="${APP_URL_FROM_ENV:-$DEFAULT_APP_URL}"

HOME_OUT="$TMP_DIR/home.html"
HOME_STATUS="$(fetch "$BASE_URL/" "$HOME_OUT")"

if [[ "$HOME_STATUS" != "200" ]]; then
    fail "빌드 주입값: 홈페이지 응답 실패 (HTTP $HOME_STATUS) — 확인 불가"
else
    HAS_EXPECTED=0
    HAS_FORBIDDEN=0
    grep -qF "$EXPECTED_APP_URL" "$HOME_OUT" 2>/dev/null && HAS_EXPECTED=1
    grep -qF "$FORBIDDEN_APP_URL" "$HOME_OUT" 2>/dev/null && HAS_FORBIDDEN=1

    if [[ "$HAS_FORBIDDEN" -eq 1 ]]; then
        fail "빌드 주입값: 다른 환경 도메인($FORBIDDEN_APP_URL)이 ${TARGET} 페이지에서 발견됨 — NEXT_PUBLIC_APP_URL 빌드 인자 오주입"
    elif [[ "$HAS_EXPECTED" -eq 1 ]]; then
        pass "빌드 주입값: NEXT_PUBLIC_APP_URL=$EXPECTED_APP_URL 정상 주입 확인"
    else
        warn "빌드 주입값: 기대값($EXPECTED_APP_URL)을 홈페이지 HTML에서 찾지 못함 — 페이지 구조 변경일 수 있음, 수동 확인 필요"
    fi
fi

# ────────────────────────────────────────────────────────────
# 8. 요약 + 종료 코드
# ────────────────────────────────────────────────────────────

echo
echo "=== 요약: PASS=$PASS_COUNT WARN=$WARN_COUNT SKIP=$SKIP_COUNT FAIL=$FAIL_COUNT ==="

if [[ "$FAIL_COUNT" -gt 0 ]]; then
    echo "결과: FAIL"
    exit 1
fi

echo "결과: PASS"
exit 0
