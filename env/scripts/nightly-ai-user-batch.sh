#!/usr/bin/env bash
# 다시봄 AI-user 새벽 압축배치.
#
# PLAN 모드(스레드플랜 사전생성 + 낮 게시전용, docs/ai-user/operations.md 참조)는
# posts.id(VARCHAR) 를 Long으로 파싱하려는 구조적 버그가 있어 2026-07-30에 다시
# scheduler_mode=LEGACY 로 되돌렸다 — 코드 수정 전까지 비활성 상태.
#
# 이 스크립트는 그 대신 기존 LEGACY tick 엔진을 새벽 창에 몰아서 압축 실행한다:
#   1) ai_user_runtime.enabled=1
#   2) generate-posts로 오늘 글 확보
#   3) /admin/trigger/tick 을 daily_global_cap 도달(또는 시간제한)까지 반복 호출
#      — AI_USER_FORCE_ACTIVE=true 라서 새벽 시간대 circadian 저하 없이 정상 예산 적용
#   4) 잔여 jitter 실행 대기 후 ai_user_runtime.enabled=0 (낮 동안 토큰 소모 차단)
#
# LLM을 수동으로 호출해 콘텐츠를 만들어 DB에 넣지 않는다 — 전부 기존 orchestrator
# admin trigger 엔드포인트(내부 도커 네트워크 전용, 외부 미노출)를 통해서만 동작한다.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$ENV_DIR/.env.ai-user"
LOG_FILE="$ENV_DIR/logs/nightly-ai-user-batch.log"
mkdir -p "$(dirname "$LOG_FILE")"

ORCH_CONTAINER=againspring-ai-user-orchestrator
DB_CONTAINER=againspring-mariadb-prod

MAX_MINUTES=${NIGHTLY_BATCH_MAX_MINUTES:-45}
TICK_INTERVAL_SECONDS=${NIGHTLY_BATCH_TICK_INTERVAL:-20}
GENERATE_POSTS_COUNT=${NIGHTLY_BATCH_POST_COUNT:-3}
MAX_CONSECUTIVE_FAILURES=5

log() { printf '[%s] %s\n' "$(date '+%F %T %Z')" "$*" | tee -a "$LOG_FILE"; }

if [ ! -f "$ENV_FILE" ]; then
  log "ERROR: $ENV_FILE not found — aborting"
  exit 1
fi
DB_USER=$(grep -oP '^MARIADB_USER=\K.*' "$ENV_FILE")
DB_PASS=$(grep -oP '^MARIADB_PASSWORD=\K.*' "$ENV_FILE")
DB_NAME=$(grep -oP '^MARIADB_DATABASE=\K.*' "$ENV_FILE")

db() {
  docker exec "$DB_CONTAINER" mariadb -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -e "$1" 2>>"$LOG_FILE"
}

trap 'log "trap: restoring ai_user_runtime.enabled=0 before exit"; db "UPDATE ai_user_runtime SET enabled=0, updated_at=UTC_TIMESTAMP() WHERE id=1;" || true' EXIT

log "=== nightly-ai-user-batch start (max ${MAX_MINUTES}m, tick every ${TICK_INTERVAL_SECONDS}s) ==="

if ! db "UPDATE ai_user_runtime SET enabled=1, updated_at=UTC_TIMESTAMP() WHERE id=1;"; then
  log "ERROR: failed to enable ai_user_runtime — aborting without further action"
  exit 1
fi
log "ai_user_runtime.enabled=1"

GEN_RESULT=$(docker exec "$ORCH_CONTAINER" wget -qO- --post-data='' \
  "http://localhost:8096/admin/trigger/generate-posts?count=${GENERATE_POSTS_COUNT}" 2>>"$LOG_FILE")
log "generate-posts result: ${GEN_RESULT:-<empty>}"

START_TS=$(date +%s)
END_TS=$((START_TS + MAX_MINUTES * 60))
TICKS=0
FAILURES=0

while [ "$(date +%s)" -lt "$END_TS" ]; do
  CAP_ROW=$(db "SELECT actions_today, daily_global_cap FROM ai_user_runtime WHERE id=1;")
  ACTIONS_TODAY=$(echo "$CAP_ROW" | awk '{print $1}')
  CAP=$(echo "$CAP_ROW" | awk '{print $2}')
  if [ -n "${ACTIONS_TODAY:-}" ] && [ -n "${CAP:-}" ] && [ "$ACTIONS_TODAY" -ge "$CAP" ]; then
    log "daily_global_cap reached (${ACTIONS_TODAY}/${CAP}) — stopping tick loop"
    break
  fi

  if docker exec "$ORCH_CONTAINER" wget -qO- --post-data='' "http://localhost:8096/admin/trigger/tick" >/dev/null 2>>"$LOG_FILE"; then
    FAILURES=0
  else
    FAILURES=$((FAILURES + 1))
    log "tick call failed (${FAILURES}/${MAX_CONSECUTIVE_FAILURES} consecutive)"
    if [ "$FAILURES" -ge "$MAX_CONSECUTIVE_FAILURES" ]; then
      log "ERROR: too many consecutive tick failures — stopping loop early"
      break
    fi
  fi
  TICKS=$((TICKS + 1))
  sleep "$TICK_INTERVAL_SECONDS"
done
log "tick loop done: ${TICKS} tick(s) issued"

log "waiting 180s for in-flight jittered actions to settle"
sleep 180

FINAL_ROW=$(db "SELECT actions_today, daily_global_cap FROM ai_user_runtime WHERE id=1;")
log "final actions_today/cap: ${FINAL_ROW:-<unknown>}"
log "=== nightly-ai-user-batch done ==="
# trap EXIT handles ai_user_runtime.enabled=0
