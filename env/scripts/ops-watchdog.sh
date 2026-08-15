#!/bin/bash
set -euo pipefail

# ============================================================================
# Again Spring Watchdog — 감시 + 자동 복구 + 텔레그램 알림
# ============================================================================
# 감시 대상:
#   1. Claude 세션 생존 (1시간마다 canary ping)
#   2. .credentials.json 소유권
#   3. 컨테이너 헬스 상태 (unhealthy)
#
# 자동 복구 (최대 3회, 상태 파일로 추적):
#   - chown for .credentials.json
#   - docker restart for unhealthy containers
#
# 텔레그램 알림: 발생 → 조치중 → 결과 (3단계)
# ============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$(dirname "$SCRIPT_DIR")")"
WATCHDOG_STATE_DIR="${PROJECT_ROOT}/watchdog-state"
TELEGRAM_ENV="${HOME}/.config/again-spring-watchdog/telegram.env"
CANARY_TIMESTAMP_FILE="${WATCHDOG_STATE_DIR}/claude-canary.timestamp"
RETRY_STATE_FILE="${WATCHDOG_STATE_DIR}/retry-state.json"
LOG_FILE="${WATCHDOG_STATE_DIR}/watchdog.log"

# 상태 디렉토리 확인
mkdir -p "$WATCHDOG_STATE_DIR"

# 텔레그램 credentials 로드
if [[ ! -f "$TELEGRAM_ENV" ]]; then
    echo "[ERROR] Telegram credentials not found at $TELEGRAM_ENV" | tee -a "$LOG_FILE"
    exit 1
fi

source "$TELEGRAM_ENV"

if [[ -z "${TELEGRAM_BOT_TOKEN:-}" ]] || [[ -z "${TELEGRAM_CHAT_ID:-}" ]]; then
    echo "[ERROR] TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID not set" | tee -a "$LOG_FILE"
    exit 1
fi

# ============================================================================
# 유틸리티 함수
# ============================================================================

log() {
    local level="$1"
    shift
    local msg="$*"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [$level] $msg" | tee -a "$LOG_FILE"
}

send_telegram() {
    local text="$1"

    # 같은 메시지는 1시간 내 재전송하지 않음 (스팸 방지)
    local stamp="${WATCHDOG_STATE_DIR}/msg-$(printf '%s' "$text" | md5sum | cut -d' ' -f1)"
    local now_ts=$(date +%s)
    local last_ts=$(stat -c %Y "$stamp" 2>/dev/null || echo 0)
    if [[ $((now_ts - last_ts)) -lt 3600 ]]; then
        return 0
    fi

    # URL 인코딩 (공백, 개행 등). parse_mode 미지정 — 백틱 불균형으로 인한 400 방지.
    local encoded_msg=$(python3 -c "import urllib.parse, sys; print(urllib.parse.quote(sys.argv[1]))" "$text")

    if curl -s "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
        -d "chat_id=${TELEGRAM_CHAT_ID}" \
        -d "text=${encoded_msg}" \
        > /dev/null 2>&1; then
        touch "$stamp"
    else
        log "WARN" "Failed to send telegram message"
    fi
}

# JSON 형식의 retry state 파일 다루기
# 형식: { "claude_session": 0, "credentials_chown": 0, "container_restart": 0, "last_alert_id": {...} }
init_retry_state() {
    if [[ ! -f "$RETRY_STATE_FILE" ]]; then
        echo '{"claude_session": 0, "credentials_chown": 0, "container_restart": {}, "last_alert_id": {}}' > "$RETRY_STATE_FILE"
    fi
}

get_retry_count() {
    local key="$1"
    python3 << EOF
import json
try:
    with open("$RETRY_STATE_FILE", "r") as f:
        state = json.load(f)
    print(state.get("$key", 0))
except:
    print(0)
EOF
}

increment_retry_count() {
    local key="$1"
    python3 << EOF
import json
try:
    with open("$RETRY_STATE_FILE", "r") as f:
        state = json.load(f)
except:
    state = {"claude_session": 0, "credentials_chown": 0, "container_restart": {}, "last_alert_id": {}}

if isinstance(state.get("$key"), dict):
    pass  # Skip dict types for now
else:
    state["$key"] = state.get("$key", 0) + 1

with open("$RETRY_STATE_FILE", "w") as f:
    json.dump(state, f)
EOF
}

reset_retry_count() {
    local key="$1"
    python3 << EOF
import json
try:
    with open("$RETRY_STATE_FILE", "r") as f:
        state = json.load(f)
except:
    state = {"claude_session": 0, "credentials_chown": 0, "container_restart": {}, "last_alert_id": {}}

if isinstance(state.get("$key"), dict):
    state["$key"] = {}
else:
    state["$key"] = 0

with open("$RETRY_STATE_FILE", "w") as f:
    json.dump(state, f)
EOF
}

# ============================================================================
# 감시 함수
# ============================================================================

check_claude_session() {
    local now=$(date +%s)
    local last_check=0

    if [[ -f "$CANARY_TIMESTAMP_FILE" ]]; then
        last_check=$(cat "$CANARY_TIMESTAMP_FILE")
    fi

    local elapsed=$((now - last_check))
    local canary_interval=$((600))  # 10분

    if [[ $elapsed -lt $canary_interval ]]; then
        # Canary 실행 시간이 아직 안 됨
        return 0
    fi

    log "INFO" "Claude canary check starting..."

    # Claude 세션 테스트 (최소 토큰)
    if timeout 30 claude -p 'ping' > /dev/null 2>&1; then
        log "INFO" "Claude session OK"
        echo "$now" > "$CANARY_TIMESTAMP_FILE"
        reset_retry_count "claude_session"
        return 0
    else
        log "WARN" "Claude session failed"

        local retry_count=$(get_retry_count "claude_session")
        log "INFO" "Claude session retry count: $retry_count / 3"

        # 재로그인은 자동화할 수 없음 (headless OAuth 불가) — 감지 즉시 수동 조치 안내.
        # 3회(각 1시간 간격)까지만 반복 알림, 그 후엔 send_telegram의 1시간 dedup에 맡김.
        if [[ $retry_count -lt 3 ]]; then
            send_telegram "⚠️ [Again-Spring] Claude 세션 만료 감지 ($(($retry_count + 1))/3). 수동 조치: 로컬 터미널에서 'claude' 실행 후 브라우저 로그인"
            increment_retry_count "claude_session"
        else
            send_telegram "❌ [Again-Spring] Claude 세션 만료 지속 중 (3회 초과). 수동 조치 필요: 로컬 터미널에서 'claude' 실행 후 브라우저 로그인"
        fi

        return 1
    fi
}

check_credentials_ownership() {
    local cred_file="${HOME}/.claude/.credentials.json"

    if [[ ! -f "$cred_file" ]]; then
        log "INFO" "Credentials file not found (OK during fresh session)"
        return 0
    fi

    local owner=$(stat -c '%U' "$cred_file" 2>/dev/null || echo "")

    if [[ "$owner" != "justant" ]]; then
        log "WARN" "Credentials file owner is '$owner' (expected 'justant')"

        send_telegram "⚠️ [Again-Spring] 자격증명 소유권 오염 감지: owner=$owner"

        local retry_count=$(get_retry_count "credentials_chown")

        if [[ $retry_count -lt 3 ]]; then
            send_telegram "🔧 chown 복구 시도 중... ($(($retry_count + 1))/3)"

            if sudo chown justant:justant "$cred_file" 2>&1; then
                log "INFO" "Credentials ownership restored"
                send_telegram "✅ 자격증명 소유권 복구 완료"
                reset_retry_count "credentials_chown"
                return 0
            else
                log "ERROR" "Failed to chown credentials file"
                increment_retry_count "credentials_chown"
            fi
        else
            log "ERROR" "Credentials chown failed 3 times, manual action required"
            send_telegram "❌ 자격증명 소유권 복구 실패 (3회 초과). 수동 조치: sudo chown justant:justant ~/.claude/.credentials.json"
        fi

        return 1
    fi

    return 0
}

check_container_health() {
    # Unhealthy 컨테이너 찾기
    local unhealthy_containers=$(docker ps --filter "health=unhealthy" --format "{{.Names}}" 2>/dev/null || echo "")

    if [[ -z "$unhealthy_containers" ]]; then
        return 0
    fi

    log "WARN" "Found unhealthy containers: $unhealthy_containers"

    while IFS= read -r container; do
        [[ -z "$container" ]] && continue

        # againspring 관련 컨테이너만 처리
        if [[ ! "$container" =~ againspring ]]; then
            continue
        fi

        log "WARN" "Unhealthy container: $container"

        # ai-learning: 크롤/임베딩 중 health 지연은 정상 — 재시작하면 크롤이 유실됨
        # (2026-08-11 02:15: 크롤 중 restart → crawl_log SUCCESS 없음 → admin stale 배지)
        if should_skip_ai_learning_restart "$container"; then
            log "WARN" "Skip restart for $container (crawl in progress or KST 02–03 crawl window)"
            send_telegram "⚠️ [Again-Spring] Unhealthy \`$container\` — 크롤 구간이라 재시작 생략 (알림만)"
            continue
        fi

        send_telegram "⚠️ [Again-Spring] Unhealthy container detected: \`$container\`"

        local retry_count=$(get_retry_count "container_restart")
        local container_retry=$(python3 -c "import json; s = json.load(open('$RETRY_STATE_FILE')); print(s.get('container_restart', {}).get('$container', 0))")

        if [[ $container_retry -lt 3 ]]; then
            send_telegram "🔧 컨테이너 재시작 시도 중... ($(($container_retry + 1))/3)"

            if docker restart "$container" > /dev/null 2>&1; then
                log "INFO" "Container $container restarted successfully"
                send_telegram "✅ 컨테이너 \`$container\` 재시작 완료"

                # Reset retry count for this container
                python3 << EOF
import json
try:
    with open("$RETRY_STATE_FILE", "r") as f:
        state = json.load(f)
except:
    state = {"claude_session": 0, "credentials_chown": 0, "container_restart": {}, "last_alert_id": {}}

state["container_restart"]["$container"] = 0
with open("$RETRY_STATE_FILE", "w") as f:
    json.dump(state, f)
EOF
            else
                log "ERROR" "Failed to restart container $container"

                python3 << EOF
import json
try:
    with open("$RETRY_STATE_FILE", "r") as f:
        state = json.load(f)
except:
    state = {"claude_session": 0, "credentials_chown": 0, "container_restart": {}, "last_alert_id": {}}

state["container_restart"]["$container"] = state["container_restart"].get("$container", 0) + 1
with open("$RETRY_STATE_FILE", "w") as f:
    json.dump(state, f)
EOF
            fi
        else
            log "ERROR" "Container $container restart failed 3 times, manual action required"
            send_telegram "❌ 컨테이너 $container 재시작 실패 (3회 초과). 수동 조치: docker restart $container"
        fi
    done <<< "$unhealthy_containers"
}

# ai-learning 크롤 보호: 마커 파일 또는 일일 크롤 시간대(KST 02–03시)
should_skip_ai_learning_restart() {
    local container="$1"
    if [[ "$container" != "againspring-ai-learning" ]]; then
        return 1
    fi

    # 수동/스케줄 크롤이 남긴 진행 중 마커
    if docker exec "$container" test -f /tmp/ai_learning_crawl_in_progress 2>/dev/null; then
        return 0
    fi

    # 스케줄 윈도우 (cron 02:00 → strengthen까지 보통 ~1–2h). 마커가 없어도 보호.
    local kst_hour
    kst_hour=$(TZ=Asia/Seoul date +%H)
    if [[ "$kst_hour" == "02" || "$kst_hour" == "03" ]]; then
        return 0
    fi

    return 1
}

# ============================================================================
# 메인 루프
# ============================================================================

main() {
    log "INFO" "=== Again-Spring Watchdog started ==="

    init_retry_state

    check_claude_session
    check_credentials_ownership
    check_container_health

    log "INFO" "=== Watchdog cycle complete ==="
}

main
