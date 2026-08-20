#!/usr/bin/env bash
# Wait for WSL (ASM/WaggleBot), sync layout-fix worker code, restart ai_worker, regenerate #703-706.
set -euo pipefail

WSL_HOST="${WSL_HOST:-justant@100.115.252.61}"
WAGGLE_SRC="${WAGGLE_SRC:-/home/justant/Data/WaggleBot}"
MAX_WAIT_SEC="${MAX_WAIT_SEC:-1800}"
POLL_SEC="${POLL_SEC:-30}"
DEADLINE=$((SECONDS + MAX_WAIT_SEC))

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S %Z')] $*"; }

wait_wsl() {
  while (( SECONDS < DEADLINE )); do
    if ssh -o ConnectTimeout=10 -o BatchMode=yes "$WSL_HOST" 'echo ok' >/dev/null 2>&1; then
      log "WSL SSH up"
      return 0
    fi
    log "WSL offline — retry in ${POLL_SEC}s"
    sleep "$POLL_SEC"
  done
  log "ERROR: WSL still offline after ${MAX_WAIT_SEC}s"
  return 1
}

sync_waggle() {
  log "rsync WaggleBot worker (layout/outro fix files)"
  rsync -avz "$WAGGLE_SRC/worker/ai_worker/scene/again_spring_text.py" \
    "$WAGGLE_SRC/worker/ai_worker/scene/director.py" \
    "$WSL_HOST:~/Data/WaggleBot/worker/ai_worker/scene/"
  rsync -avz "$WAGGLE_SRC/worker/ai_worker/core/processor.py" \
    "$WSL_HOST:~/Data/WaggleBot/worker/ai_worker/core/"
  rsync -avz "$WAGGLE_SRC/worker/ai_worker/renderer/layout.py" \
    "$WAGGLE_SRC/worker/ai_worker/renderer/_frames.py" \
    "$WSL_HOST:~/Data/WaggleBot/worker/ai_worker/renderer/"
  rsync -avz "$WAGGLE_SRC/worker/test/test_again_spring_text.py" \
    "$WSL_HOST:~/Data/WaggleBot/worker/test/"
}

restart_worker() {
  log "restart WaggleBot ai_worker on WSL"
  ssh -o BatchMode=yes "$WSL_HOST" 'cd ~/Data/WaggleBot/env && docker compose restart ai_worker'
  log "probe ASM health"
  ssh -o BatchMode=yes "$WSL_HOST" 'curl -sf http://localhost:8200/api/health || curl -sf http://localhost:8200/health'
}

wait_asm_reachable() {
  while (( SECONDS < DEADLINE )); do
    if curl -sf --max-time 10 "http://100.115.252.61:8200/api/health" >/dev/null 2>&1; then
      log "ASM reachable from server2"
      return 0
    fi
    log "ASM not reachable — retry in 10s"
    sleep 10
  done
  return 1
}

main() {
  wait_wsl
  sync_waggle
  restart_worker
  wait_asm_reachable
  log "run regenerate script for jobs 703-706"
  cd /home/justant/Data/Again-Spring
  python3 scripts/rerender-marketing-videos-layout-fix.py
  log "all done"
}

main "$@"
