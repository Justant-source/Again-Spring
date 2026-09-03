#!/usr/bin/env bash
# dev 전용 AI-user canary — orchestrator-dev를 일시 기동해 STUB provider로
# generate → hold → publish 1사이클을 backend-dev에 실제로 돌린다. LLM 호출 0, prod 무접촉.
# 사용: scripts/ai-user-canary.sh            (deploy.sh dev --ai-user-canary 가 호출)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_DIR="$ROOT/env"
COMPOSE=(docker compose -f "$ENV_DIR/docker-compose.ai-user.yml" --env-file "$ENV_DIR/.env.ai-user" --profile ai-user-dev)
ORCH=againspring-ai-user-orchestrator-dev
DB=againspring-mariadb-dev
FIX="$ROOT/.temp/llm-stub-fixtures"
BE="http://localhost:8090"

envv() { grep -E "^$1=" "$ENV_DIR/.env.ai-user" | tail -1 | cut -d= -f2-; }
DEV_PW="$(envv DEV_MARIADB_PASSWORD)"; DEV_DB="$(envv DEV_DB_NAME)"; DEV_DB="${DEV_DB:-againspring_dev}"
sql() { { printf '%s\n' "$DEV_PW"; printf '%s\n' "$1"; } | docker exec -i -e U="$(envv MARIADB_USER)" -e D="$DEV_DB" "$DB" \
        sh -c 'read -r MYSQL_PWD; export MYSQL_PWD; exec mariadb -N -B -u"${U:-againspring}" "$D"'; }
orch() { docker exec "$ORCH" wget -qO- -T 600 --post-data='' "http://localhost:8096/admin/trigger/$1"; }

SNAP=""; SCHED_ID=""; POST_ID=""
cleanup() {
  set +e
  [[ -n "$POST_ID" ]] && sql "UPDATE posts SET deleted_at=NOW(3) WHERE id='$POST_ID';"
  [[ -n "$SCHED_ID" ]] && sql "DELETE FROM ai_scheduled_posts WHERE id='$SCHED_ID';"
  if [[ -n "$SNAP" ]]; then
    IFS=$'\t' read -r p1 p2 p3 <<<"$SNAP"
    sql "UPDATE ai_user_generation_config SET provider_ai_post_bundle='$p1', provider_human_post_plan='$p2', provider_human_interaction='$p3', updated_by='ai-user-canary' WHERE id=1;"
  fi
  # ai-user-orchestrator-dev는 Task 4.7부터 상시 기동(unless-stopped) 서비스로 이미
  # 떠 있었다 — 이 canary가 만든 임시 컨테이너가 아니므로 여기서 내리지 않는다
  # (원 브리프의 `rm -sf ai-user-orchestrator-dev`는 매번 새로 띄우는 에페메럴
  # 컨테이너를 전제했으나, 실제로는 지속 서비스와 충돌한다).
}
trap cleanup EXIT

echo "▶ [canary] 픽스처 준비" >&2
mkdir -p "$FIX"
cp "$ROOT"/ai-user/llm/src/main/resources/stub/* "$FIX"/

echo "▶ [canary] dev DB provider=STUB (스냅샷 보관)" >&2
SNAP="$(sql "SELECT provider_ai_post_bundle, provider_human_post_plan, provider_human_interaction FROM ai_user_generation_config WHERE id=1;")"
[[ -n "$SNAP" ]] || { echo "🚨 dev ai_user_generation_config(id=1) 없음" >&2; exit 1; }
sql "UPDATE ai_user_generation_config SET provider_ai_post_bundle='STUB', provider_human_post_plan='STUB', provider_human_interaction='STUB', ai_user_kill_switch=0, schedule_execution_paused=0, updated_by='ai-user-canary' WHERE id=1;"
sql "UPDATE llm_generation_gate SET state='ACTIVE', reason=NULL WHERE id=1;" || true

echo "▶ [canary] orchestrator-dev 일시 기동" >&2
AI_USER_DEV_ENABLED=true AI_LEARNING_ENABLED=false "${COMPOSE[@]}" up -d --build ai-user-orchestrator-dev
for i in $(seq 1 40); do
  docker exec "$ORCH" wget -qO- http://localhost:8096/actuator/health 2>/dev/null | grep -q UP && break
  sleep 3; [[ $i -eq 40 ]] && { echo "🚨 orchestrator-dev 헬스 실패" >&2; docker logs --tail 50 "$ORCH" >&2; exit 1; }
done

echo "▶ [canary] generate-scheduled-posts (STUB)" >&2
GEN="$(orch 'generate-scheduled-posts?count=1&fromHour=0&toHour=23&minSpacingMinutes=1&skipSourceClaim=true')"
echo "$GEN" >&2
SCHED_ID="$(printf '%s' "$GEN" | grep -oE '"scheduledIds":\["[^"]+"' | sed -E 's/.*\["([^"]+)"/\1/')"
[[ -n "$SCHED_ID" ]] || { echo "🚨 예약글 저장 실패" >&2; exit 1; }

echo "▶ [canary] publish-scheduled-post force=true" >&2
PUB="$(orch "publish-scheduled-post?id=$SCHED_ID&force=true")"
echo "$PUB" >&2
POST_ID="$(printf '%s' "$PUB" | grep -oE '"postId":"[^"]+"' | sed -E 's/.*:"([^"]+)"/\1/')"
[[ -n "$POST_ID" ]] || { echo "🚨 게시 실패" >&2; exit 1; }

echo "▶ [canary] backend-dev 게시 확인" >&2
CODE="$(curl -s -o /tmp/canary-post.json -w '%{http_code}' "$BE/api/community/posts/$POST_ID")"
[[ "$CODE" == "200" ]] || { echo "🚨 GET post → HTTP $CODE" >&2; exit 1; }
grep -q '"synthetic":true\|"isSynthetic":true\|"authorSynthetic":true' /tmp/canary-post.json || echo "[WARN] 응답에 synthetic 플래그 없음 — DTO 필드명 확인" >&2

echo "✅ [canary] PASS scheduled=$SCHED_ID post=$POST_ID (정리 후 종료)" >&2
