#!/usr/bin/env bash
# 다시봄 AI-user 새벽 배치 — PLAN 모드용 (2026-07-31~).
#
# 2026-07-30에는 PLAN 모드가 postId(VARCHAR) Long 파싱 버그로 깨져 있어 LEGACY
# tick을 새벽에 몰아 압축 실행하는 임시방편을 썼다. 그 결과 새 글 7개가 한꺼번에
# 같은 시각에 올라오고 댓글이 하나도 안 붙는 문제가 났다 — LEGACY는 생성=발행이
# 분리되지 않아서 애초에 "새벽엔 준비만, 낮엔 하나씩" 요구사항을 만족할 수 없다.
#
# 2026-07-31에 postId 버그가 수정되고(커밋 1e9475cd) PLAN 모드가 정상 동작함을
# 확인했으므로, 이 스크립트는 이제 PLAN 모드를 새벽에만 켜는 것으로 바뀐다:
#   1) ai_user_generation_config.provider_ai_post_bundle/human_post_plan/
#      human_interaction = 'CLAUDE' (새벽에만 새 LLM job 생성 허용)
#   2) generate-posts로 오늘 AI 글 확보 — 각 글은 저장 즉시 outbox를 통해
#      스레드플랜(글+댓글/대댓글 후보)이 한 번의 구조화 LLM 요청으로 통째 생성됨
#   3) ai_thread_plans.status='REQUESTED' 큐가 빌 때까지(또는 시간제한까지) 대기
#      — 생성만 하고 게시는 하지 않는다. 게시는 scheduled_at에 따라 낮 동안
#        ThreadPlanPublisher가 알아서 분산 실행한다(LLM 호출 없음).
#   4) provider를 다시 'OFF'로 돌려 낮 동안 새 LLM job 생성을 막는다.
#      schedule_execution_paused는 항상 false로 둬서 게시 자체는 하루 종일 계속된다.
#
# ai_user_runtime.enabled(LEGACY tick 킬스위치)는 건드리지 않는다 — PLAN 모드는
# 이 플래그를 쓰지 않는다.
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
POLL_INTERVAL_SECONDS=${NIGHTLY_BATCH_POLL_INTERVAL:-30}
GENERATE_POSTS_COUNT=${NIGHTLY_BATCH_POST_COUNT:-3}

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

trap 'log "trap: restoring provider=OFF before exit"; db "UPDATE ai_user_generation_config SET provider_ai_post_bundle=\"OFF\", provider_human_post_plan=\"OFF\", provider_human_interaction=\"OFF\", updated_by=\"nightly-batch-trap\", updated_at=UTC_TIMESTAMP() WHERE id=1;" || true' EXIT

log "=== nightly-ai-user-batch (PLAN mode) start (max ${MAX_MINUTES}m) ==="

if ! db "UPDATE ai_user_generation_config SET provider_ai_post_bundle='CLAUDE', provider_human_post_plan='CLAUDE', provider_human_interaction='CLAUDE', updated_by='nightly-batch', updated_at=UTC_TIMESTAMP() WHERE id=1;"; then
  log "ERROR: failed to enable PLAN providers — aborting without further action"
  exit 1
fi
log "provider_ai_post_bundle/human_post_plan/human_interaction=CLAUDE"

GEN_RESULT=$(docker exec "$ORCH_CONTAINER" wget -qO- --post-data='' \
  "http://localhost:8096/admin/trigger/generate-posts?count=${GENERATE_POSTS_COUNT}" 2>>"$LOG_FILE")
log "generate-posts result: ${GEN_RESULT:-<empty>}"

START_TS=$(date +%s)
END_TS=$((START_TS + MAX_MINUTES * 60))

while [ "$(date +%s)" -lt "$END_TS" ]; do
  REQUESTED=$(db "SELECT COUNT(*) FROM ai_thread_plans WHERE status='REQUESTED';")
  if [ "${REQUESTED:-0}" -eq 0 ]; then
    log "ai_thread_plans REQUESTED queue drained"
    break
  fi
  log "REQUESTED queue: ${REQUESTED} — waiting"
  sleep "$POLL_INTERVAL_SECONDS"
done

FINAL_STATUS=$(db "SELECT status, COUNT(*) FROM ai_thread_plans GROUP BY status;")
log "final ai_thread_plans status: ${FINAL_STATUS:-<none>}"
log "=== nightly-ai-user-batch done ==="
# trap EXIT handles provider=OFF
