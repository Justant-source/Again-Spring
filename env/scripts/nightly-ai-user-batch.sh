#!/usr/bin/env bash
# 다시봄 AI-user 새벽 배치 — 예약글 파이프라인 (2026-07-31~).
#
# 이력:
#   2026-07-30: PLAN 모드가 postId(VARCHAR) Long 파싱 버그로 깨져 있어 LEGACY tick을
#   새벽에 몰아 압축 실행하는 임시방편을 썼다. 생성=발행이 분리되지 않아 새 글이
#   전부 같은 시각에 몰리고 댓글이 하나도 안 붙는 문제가 났다.
#
#   2026-07-31 오전: postId 버그를 고치고(커밋 1e9475cd) PLAN 모드로 전환했지만,
#   AiPostBundleService.generateAndPublish()는 생성 즉시 글을 발행한다 — 여전히
#   "새벽에 생성 = 새벽에 발행"이었다. 이 스크립트가 처음 그 방식으로 3개 글을
#   만들었고, 전부 03:0x KST에 몰려 올라왔다(사용자 리포트로 발견).
#
#   2026-07-31 오후: 진짜 생성/발행 분리 파이프라인을 만들었다 —
#   AiPostBundleService.generateAndHold()가 발행하지 않고 ai_scheduled_posts에
#   저장하고, ScheduledPostPublisher가 슬롯 도래 시 실제로 발행한다. 이 스크립트는
#   이제 그 파이프라인만 쓴다.
#
# 절차:
#   1) provider(ai_post_bundle/human_post_plan/human_interaction) = CLAUDE
#   2) /admin/trigger/generate-scheduled-posts — N개 글을 "생성만" 함(발행 안 함).
#      각 글의 발행 슬롯은 ActivityCurve로 오늘 남은 활동 시간대(기본 08~22시 KST)에
#      가중 샘플링됨 — 20~40대 커뮤니티 체류 패턴 근사치(실측 데이터 아님, 하드코딩값).
#   3) 낮 동안 실사람이 새로 올린 글에 대한 REQUESTED 스레드플랜 백로그도 이 새벽
#      창에서 같이 소진한다(provider가 어차피 켜져 있으므로).
#   4) provider를 다시 OFF로 복귀. schedule_execution_paused는 항상 false로 둬서
#      ScheduledPostPublisher/ThreadPlanPublisher의 게시 자체는 하루 종일 계속된다.
#
# ai_user_runtime.enabled(LEGACY tick 킬스위치)는 건드리지 않는다 — 이 파이프라인과
# 무관하다.
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
# 아래 값은 /admin/ai-user → ai_user_generation_config 가 SSOT.
# env NIGHTLY_BATCH_* 는 DB 조회 실패 시에만 fallback.
SCHEDULED_POST_COUNT_FALLBACK=${NIGHTLY_BATCH_POST_COUNT:-5}
PAIRED_SHARE_FALLBACK=${NIGHTLY_BATCH_PAIRED_SHARE:-0.20}
SLOT_FROM_HOUR_FALLBACK=${NIGHTLY_BATCH_SLOT_FROM_HOUR:-8}
SLOT_TO_HOUR_FALLBACK=${NIGHTLY_BATCH_SLOT_TO_HOUR:-22}
SLOT_MIN_SPACING_MINUTES_FALLBACK=${NIGHTLY_BATCH_SLOT_MIN_SPACING_MINUTES:-45}

# ceil(N * share); N>=1이면 최소 1 (비율>0일 때)
paired_count_for() {
  local n=$1
  local share=$2
  if [ "$n" -le 0 ]; then echo 0; return; fi
  # awk ceil
  local p
  p=$(awk -v n="$n" -v s="$share" 'BEGIN { x=n*s; if (x<=0) print 0; else print int(x)==x ? x : int(x)+1 }')
  if [ "$p" -lt 1 ] && awk -v s="$share" 'BEGIN { exit !(s>0) }'; then p=1; fi
  if [ "$p" -gt "$n" ]; then p=$n; fi
  echo "$p"
}

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

# /admin/ai-user 저장값 로드 (target_posts = 새벽 생성 개수)
load_generation_config() {
  local row
  row=$(db "SELECT target_posts, COALESCE(nightly_paired_share,0.20), COALESCE(nightly_slot_from_hour,8), COALESCE(nightly_slot_to_hour,22), COALESCE(nightly_slot_min_spacing_minutes,45) FROM ai_user_generation_config WHERE id=1" || true)
  if [ -z "${row:-}" ]; then
    SCHEDULED_POST_COUNT=$SCHEDULED_POST_COUNT_FALLBACK
    PAIRED_SHARE=$PAIRED_SHARE_FALLBACK
    SLOT_FROM_HOUR=$SLOT_FROM_HOUR_FALLBACK
    SLOT_TO_HOUR=$SLOT_TO_HOUR_FALLBACK
    SLOT_MIN_SPACING_MINUTES=$SLOT_MIN_SPACING_MINUTES_FALLBACK
    log "WARN: ai_user_generation_config 조회 실패 — env fallback count=${SCHEDULED_POST_COUNT}"
    return
  fi
  SCHEDULED_POST_COUNT=$(printf '%s' "$row" | awk '{print $1}')
  PAIRED_SHARE=$(printf '%s' "$row" | awk '{print $2}')
  SLOT_FROM_HOUR=$(printf '%s' "$row" | awk '{print $3}')
  SLOT_TO_HOUR=$(printf '%s' "$row" | awk '{print $4}')
  SLOT_MIN_SPACING_MINUTES=$(printf '%s' "$row" | awk '{print $5}')
  # sanitize
  if ! [ "${SCHEDULED_POST_COUNT}" -ge 0 ] 2>/dev/null; then SCHEDULED_POST_COUNT=$SCHEDULED_POST_COUNT_FALLBACK; fi
  if [ "${SCHEDULED_POST_COUNT}" -gt 100 ]; then SCHEDULED_POST_COUNT=100; fi
}

trap 'log "trap: restoring provider=OFF before exit"; db "UPDATE ai_user_generation_config SET provider_ai_post_bundle=\"OFF\", provider_human_post_plan=\"OFF\", provider_human_interaction=\"OFF\", updated_by=\"nightly-batch-trap\", updated_at=UTC_TIMESTAMP() WHERE id=1;" || true' EXIT

log "=== nightly-ai-user-batch (scheduled-post pipeline) start (max ${MAX_MINUTES}m) ==="

if ! db "UPDATE ai_user_generation_config SET provider_ai_post_bundle='CLAUDE', provider_human_post_plan='CLAUDE', provider_human_interaction='CLAUDE', updated_by='nightly-batch', updated_at=UTC_TIMESTAMP() WHERE id=1;"; then
  log "ERROR: failed to enable PLAN providers — aborting without further action"
  exit 1
fi
log "provider_ai_post_bundle/human_post_plan/human_interaction=CLAUDE"
load_generation_config
PAIRED_COUNT=$(paired_count_for "$SCHEDULED_POST_COUNT" "$PAIRED_SHARE")
SOLO_COUNT=$(( SCHEDULED_POST_COUNT - PAIRED_COUNT ))
log "nightly allocation: total=${SCHEDULED_POST_COUNT} solo=${SOLO_COUNT} paired=${PAIRED_COUNT} (share=${PAIRED_SHARE} slots=${SLOT_FROM_HOUR}-${SLOT_TO_HOUR} spacing=${SLOT_MIN_SPACING_MINUTES}m) [from ai_user_generation_config]"

if [ "$SOLO_COUNT" -gt 0 ]; then
  GEN_RESULT=$(docker exec "$ORCH_CONTAINER" wget -qO- -T 1800 --post-data='' \
    "http://localhost:8096/admin/trigger/generate-scheduled-posts?count=${SOLO_COUNT}&fromHour=${SLOT_FROM_HOUR}&toHour=${SLOT_TO_HOUR}&minSpacingMinutes=${SLOT_MIN_SPACING_MINUTES}" \
    2>>"$LOG_FILE")
  log "generate-scheduled-posts result: ${GEN_RESULT:-<empty>}"
else
  log "solo count=0 — skip generate-scheduled-posts"
fi

if [ "$PAIRED_COUNT" -gt 0 ]; then
  PAIRED_RESULT=$(docker exec "$ORCH_CONTAINER" wget -qO- -T 1800 --post-data='' \
    "http://localhost:8096/admin/trigger/paired-posts?count=${PAIRED_COUNT}" \
    2>>"$LOG_FILE")
  log "paired-posts result: ${PAIRED_RESULT:-<empty>}"
else
  log "paired count=0 — skip paired-posts"
fi

# 낮 동안 밀린 REQUESTED 스레드플랜(실사람 글에 대한 AI 반응 등)도 이 창에서 소진한다.
START_TS=$(date +%s)
END_TS=$((START_TS + MAX_MINUTES * 60))
while [ "$(date +%s)" -lt "$END_TS" ]; do
  REQUESTED=$(db "SELECT COUNT(*) FROM ai_thread_plans WHERE status='REQUESTED';")
  if [ "${REQUESTED:-0}" -eq 0 ]; then
    log "ai_thread_plans REQUESTED backlog drained"
    break
  fi
  log "REQUESTED backlog: ${REQUESTED} — waiting"
  sleep "$POLL_INTERVAL_SECONDS"
done

SCHEDULED_COUNT=$(db "SELECT COUNT(*) FROM ai_scheduled_posts WHERE status='SCHEDULED';")
log "ai_scheduled_posts pending publish: ${SCHEDULED_COUNT:-0}"
log "=== nightly-ai-user-batch done ==="
# trap EXIT handles provider=OFF
