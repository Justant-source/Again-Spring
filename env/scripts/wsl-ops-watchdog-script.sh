#!/bin/bash

# WSL Ops Watchdog — Claude 세션, 자격증명, 컨테이너, ASM 콜백 감시
# SSOT: Again-Spring env/scripts/wsl-ops-watchdog-script.sh
# 배포: ~/.config/systemd/user/wsl-ops-watchdog-script.sh
# Claude canary 실패 시 AS(100.81.189.92) 세션을 pull 한 뒤 ping 재시도.

LOG_DIR="${HOME}/.wsl-watchdog"
LOG_FILE="${LOG_DIR}/watchdog.log"
mkdir -p "${LOG_DIR}"

log() {
  printf "[%s] %s\n" "$(date +'%Y-%m-%d %H:%M:%S')" "$*" >> "${LOG_FILE}" 2>&1
}

log "=== Watchdog START ==="

set -e
export PATH="${HOME}/.local/bin:${HOME}/.nvm/versions/node/v22.14.0/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

STATE_DIR="${LOG_DIR}"
CANARY_FILE="${STATE_DIR}/canary.ts"
RETRY_FILE="${STATE_DIR}/retry.txt"
BOOT_TIME_FILE="${STATE_DIR}/boot.ts"

CONTAINERS=("llm-worker" "again-spring-marketing-asm-1" "again-spring-marketing-llm-bridge-1" "again-spring-marketing-social-poster-1" "env-ai_worker-1" "comfyui")

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Utilities
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

get_retry() {
  local key="$1"
  local file="${RETRY_FILE}"
  local val
  if [ ! -f "${file}" ]; then
    echo 0
    return
  fi
  # 2026-08-16: `grep | head | cut` exits 0 (cut's status) even when grep matches nothing,
  # so the old `|| echo 0` fallback never fired for a key with no prior record — $val came
  # back empty, and every caller's `[ "$cnt" -lt 3 ]` / `[ "$cnt" -ge 3 ]` integer comparison
  # silently errored instead of taking either branch. Net effect: a never-before-seen crashed
  # container (comfyui) was detected every 5 minutes but never actually restarted, and never
  # got the "manual action needed" alert either — it just sat dead for 5 days.
  val=$(grep "^${key}=" "${file}" 2>/dev/null | head -1 | cut -d= -f2)
  echo "${val:-0}"
}

set_retry() {
  local key="$1"
  local val="$2"
  local file="${RETRY_FILE}"
  touch "${file}"
  sed -i "/^${key}=/d" "${file}" 2>/dev/null || true
  echo "${key}=${val}" >> "${file}"
}

load_tg() {
  local tg_file="${STATE_DIR}/tg.conf"
  if [ -f "${tg_file}" ]; then
    source "${tg_file}"
  else
    if [ -f "${HOME}/Data/WaggleBot/env/.env" ]; then
      TG_BOT=$(grep '^TELEGRAM_BOT_TOKEN=' "${HOME}/Data/WaggleBot/env/.env" | cut -d= -f2)
      TG_CHAT=$(grep '^ALLOWED_USER_IDS=' "${HOME}/Data/WaggleBot/env/.env" | cut -d= -f2)
      [ -n "${TG_BOT}" ] && echo "TG_BOT='${TG_BOT}'" > "${tg_file}"
      [ -n "${TG_CHAT}" ] && echo "TG_CHAT='${TG_CHAT}'" >> "${tg_file}"
      chmod 600 "${tg_file}" 2>/dev/null || true
    fi
  fi
}

send_msg() {
  local text="$1"
  local type="${2:-info}"
  # dedupe_key: 지정 시 이 값으로 중복방지 stamp 파일을 고정한다 (메시지 본문이
  # 매번 달라져도 같은 사건은 한 번만 발송). 미지정 시 기존처럼 본문 해시 사용.
  local dedupe_key="${3:-}"
  load_tg
  [ -z "${TG_BOT}" ] || [ -z "${TG_CHAT}" ] && return 0

  case "$type" in
    detect) text="🚨 [감지] $text" ;;
    attempt) text="⚙️ [조치중] $text" ;;
    ok) text="✅ [성공] $text" ;;
    err) text="❌ [실패] $text" ;;
  esac

  local hash_source="${dedupe_key:-$text}"
  local stamp="${STATE_DIR}/msg-$(echo "$hash_source" | md5sum | cut -d' ' -f1)"
  local now=$(date +%s)
  local last=$(stat -c %Y "${stamp}" 2>/dev/null || echo 0)
  [ $((now - last)) -lt 3600 ] && return 0

  local payload
  payload=$(jq -n --arg cid "$TG_CHAT" --arg txt "$text" '{chat_id: ($cid|tonumber), text: $txt}')
  curl -s -X POST "https://api.telegram.org/bot${TG_BOT}/sendMessage" \
    -H "Content-Type: application/json" \
    -d "$payload" >/dev/null 2>&1 && touch "${stamp}"
}

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Checks
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

claude_ping() {
  # Prefer native ~/.local/bin/claude. nvm v22 has no claude; nvm.sh + 30s was
  # enough to miss a healthy session (canary 3/3 with oauth still valid).
  timeout 90 env -u ANTHROPIC_API_KEY bash -c '
    export PATH="${HOME}/.local/bin:${PATH}"
    if [ -x "${HOME}/.local/bin/claude" ]; then
      exec "${HOME}/.local/bin/claude" -p ping
    fi
    if [ -s "${HOME}/.nvm/nvm.sh" ]; then
      # shellcheck disable=SC1090
      . "${HOME}/.nvm/nvm.sh"
    fi
    exec claude -p ping
  ' >/dev/null 2>&1
}

# 피어(AS)와 같은 oauth를 유지. canary 실패 시 pull, 성공 시 expiresAt 기준 reconcile.
claude_peer_bin() {
  if [ -x "${HOME}/.local/bin/claude-oauth-peer.sh" ]; then
    echo "${HOME}/.local/bin/claude-oauth-peer.sh"
  else
    echo ""
  fi
}

AS_SSH="${AS_SSH:-justant@100.81.189.92}"

pull_as_claude_session() {
  local bin
  bin=$(claude_peer_bin)
  if [ -z "$bin" ]; then
    log "claude-oauth-peer.sh missing"
    return 1
  fi
  log "pulling Claude session from $AS_SSH"
  "$bin" pull "$AS_SSH"
}

reconcile_as_claude_session() {
  local bin
  bin=$(claude_peer_bin)
  [ -z "$bin" ] && return 0
  log "reconcile Claude oauth with AS"
  "$bin" reconcile "$AS_SSH" || log "reconcile failed"
}

check_claude() {
  local now=$(date +%s)
  local last=$(cat "${CANARY_FILE}" 2>/dev/null || echo 0)
  [ $((now - last)) -lt 600 ] && return 0

  if claude_ping; then
    echo $now > "${CANARY_FILE}"
    set_retry claude_pull 0
    reconcile_as_claude_session >> "${LOG_FILE}" 2>&1 || true
    return 0
  fi

  log "Claude canary failed — pull AS session and retry"
  local cnt
  cnt=$(get_retry claude_pull)

  if [ "$cnt" -ge 3 ]; then
    send_msg "Claude 세션 복구 한계. AS/WSL 양쪽 로그인 확인 필요." err
    return 1
  fi

  send_msg "Claude 세션 이상. AS(100.81.189.92) 세션을 가져와 재시도 ($((cnt + 1))/3)" attempt
  if pull_as_claude_session >> "${LOG_FILE}" 2>&1 && claude_ping; then
    echo $now > "${CANARY_FILE}"
    set_retry claude_pull 0
    reconcile_as_claude_session >> "${LOG_FILE}" 2>&1 || true
    send_msg "Claude 세션 복구 (AS 100.81.189.92 복사)" ok
    return 0
  fi

  set_retry claude_pull $((cnt + 1))
  send_msg "Claude 세션 만료. AS 복사 후에도 실패. 한쪽에서 \`claude\` 로그인 필요. ($((cnt + 1))/3)" detect
}

check_creds() {
  local cred="${HOME}/.claude/.credentials.json"
  [ ! -f "${cred}" ] && return 0
  
  local owner=$(stat -c '%U' "${cred}")
  [ "${owner}" = "justant" ] && return 0
  
  send_msg "자격증명 오염: ${owner}" detect
  
  local cnt=$(get_retry cred_fix)
  if [ "$cnt" -lt 3 ]; then
    send_msg "복구 중 ($((cnt + 1))/3)" attempt
    if sudo chown justant:justant "${cred}"; then
      send_msg "자격증명 복구 성공" ok
      set_retry cred_fix 0
    else
      set_retry cred_fix $((cnt + 1))
      send_msg "복구 실패 ($((cnt + 1))/3)" err
    fi
  else
    send_msg "복구 한계 초과. 수동: \`sudo chown justant:justant ~/.claude/.credentials.json\`" err
  fi
}

check_containers() {
  for c in "${CONTAINERS[@]}"; do
    local s=$(docker inspect --format='{{.State.Status}}' "$c" 2>/dev/null || echo "missing")
    local h=$(docker inspect --format='{{.State.Health.Status}}' "$c" 2>/dev/null || echo "")
    
    # Health status가 있고 unhealthy면 재시작 대상
    if [ -n "$h" ] && [ "$h" != "healthy" ] && [ "$h" != "" ]; then
      s="unhealthy"
    fi
    
    [ "$s" = "running" ] && continue
    
    send_msg "컨테이너 이상: $c (상태: $s)" detect
    
    local cnt=$(get_retry "c_$c")
    if [ "$cnt" -lt 3 ] && [ "$s" != "missing" ]; then
      send_msg "재시작 중: $c ($((cnt + 1))/3)" attempt
      if docker restart "$c" >/dev/null 2>&1; then
        send_msg "재시작 성공: $c" ok
        set_retry "c_$c" 0
      else
        set_retry "c_$c" $((cnt + 1))
        send_msg "재시작 실패: $c ($((cnt + 1))/3)" err
      fi
    elif [ "$cnt" -ge 3 ]; then
      send_msg "컨테이너 한계: $c. 수동: \`docker restart $c\`" err
    fi
  done
}

check_wsl_reboot() {
  # boot_id는 부팅마다 커널이 새로 생성하는 고정값 — uptime -s를 date -d로 재파싱할 때
  # 생기는 초 단위 반올림 흔들림이 없어 같은 부팅 중 오탐(중복 재부팅 감지)이 없다.
  local current_boot_id
  current_boot_id=$(cat /proc/sys/kernel/random/boot_id 2>/dev/null | tr -d '[:space:]')
  [ -z "$current_boot_id" ] && return 0
  local current_boot_epoch
  current_boot_epoch=$(date -d "$(uptime -s)" +%s 2>/dev/null || echo 0)

  if [ ! -f "${BOOT_TIME_FILE}" ]; then
    # 첫 실행 — 알림 없이 기록만
    printf '{"boot_id":"%s","boot_epoch":%s}\n' "$current_boot_id" "$current_boot_epoch" > "${BOOT_TIME_FILE}"
    return 0
  fi

  local stored_boot_id="" stored_boot_epoch=""
  if grep -q '"boot_id"' "${BOOT_TIME_FILE}" 2>/dev/null; then
    stored_boot_id=$(jq -r '.boot_id // empty' "${BOOT_TIME_FILE}" 2>/dev/null)
    stored_boot_epoch=$(jq -r '.boot_epoch // empty' "${BOOT_TIME_FILE}" 2>/dev/null)
  else
    # 구 형식(초 단위 epoch만 저장) — boot_id가 없으므로 이번 회차는 판단하지 않고
    # 신 형식으로 마이그레이션만 한다 (알림 없음).
    stored_boot_epoch=$(cat "${BOOT_TIME_FILE}" 2>/dev/null | tr -d '[:space:]')
  fi

  if [ -z "$stored_boot_id" ]; then
    printf '{"boot_id":"%s","boot_epoch":%s}\n' "$current_boot_id" "$current_boot_epoch" > "${BOOT_TIME_FILE}"
    return 0
  fi

  if [ "$current_boot_id" != "$stored_boot_id" ]; then
    # 재부팅 감지
    local last_boot_readable current_boot_readable boot_interval_min
    last_boot_readable=$(date -d "@${stored_boot_epoch:-0}" '+%Y-%m-%d %H:%M:%S' 2>/dev/null || echo "?")
    current_boot_readable=$(date -d "@${current_boot_epoch}" '+%Y-%m-%d %H:%M:%S' 2>/dev/null || echo "?")
    boot_interval_min="?"
    if [ -n "$stored_boot_epoch" ] && [ "$stored_boot_epoch" -eq "$stored_boot_epoch" ] 2>/dev/null; then
      boot_interval_min=$(( (current_boot_epoch - stored_boot_epoch) / 60 ))
    fi

    # dedupe_key를 boot_id에 고정 — 메시지 본문의 시각 문자열이 달라져도 같은 부팅은 1회만 발송.
    send_msg "WSL 재부팅 감지: $current_boot_readable (이전 $last_boot_readable, 간격 ${boot_interval_min}분)" \
      detect "wsl-reboot:${current_boot_id}"
    printf '{"boot_id":"%s","boot_epoch":%s}\n' "$current_boot_id" "$current_boot_epoch" > "${BOOT_TIME_FILE}"
  fi
}

check_asm_401() {
  docker logs --since 5m again-spring-marketing-asm-1 2>/dev/null | grep -qi "emit_callback.*HTTP 401" || return 0
  send_msg "ASM 콜백 401 감지. 확인: \`docker logs again-spring-marketing-asm-1\`" detect
}

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Main
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

check_claude || true
check_creds || true
check_containers || true
check_wsl_reboot || true
check_asm_401 || true

log "=== Watchdog END ==="
