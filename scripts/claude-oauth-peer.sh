#!/usr/bin/env bash
#
# AS ↔ WSL Claude Code oauth 동기화.
# 토큰은 SSH 파이프에만 두고 화면에 찍지 않는다.
#
#   pull <user@host>         피어 → 로컬 병합
#   push <user@host>         로컬 → 피어 병합
#   reconcile <user@host>    expiresAt 이 더 큰 쪽을 양쪽에 맞춤 (같으면 noop)
#   install-wsl [user@host]  헬퍼·래퍼를 WSL ~/.local 에 설치
#
# 원격 python3 -c 멀티라인 금지: SSH가 -c 인자를 버려 push 가 깨진다.
# 원격 merge 는 헬퍼 파일을 먼저 설치한 뒤 `python3 claude_oauth_creds.py merge`.
# pull 의 extract 는 heredoc|pipe 를 쓰지 않는다 (stdin 이 merge 쪽으로 새어 빈 JSON).
#
set -euo pipefail

ACTION="${1:-}"
PEER="${2:-}"

die() { echo "ERROR: $*" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
resolve_helper() {
  if [ -f "${SCRIPT_DIR}/claude_oauth_creds.py" ]; then
    echo "${SCRIPT_DIR}/claude_oauth_creds.py"
  elif [ -f "${HOME}/.local/lib/claude_oauth_creds.py" ]; then
    echo "${HOME}/.local/lib/claude_oauth_creds.py"
  else
    die "claude_oauth_creds.py 없음 (scripts/ 또는 ~/.local/lib/)"
  fi
}

SSH_OPTS=(-o BatchMode=yes -o ConnectTimeout=10)

[ -n "$ACTION" ] || die "usage: $0 pull|push|reconcile|install-wsl [user@host]"

if [ "$ACTION" != "install-wsl" ]; then
  [ -n "$PEER" ] || die "usage: $0 pull|push|reconcile user@host"
fi

HELPER="$(resolve_helper)"

expires_local() {
  python3 "$HELPER" expires
}

expires_peer() {
  local remote_helper
  remote_helper="$(ensure_remote_helper)"
  ssh "${SSH_OPTS[@]}" "$PEER" python3 "$remote_helper" expires
}

ensure_remote_helper() {
  ssh "${SSH_OPTS[@]}" "$PEER" 'mkdir -p "$HOME/.local/lib"' >/dev/null
  local local_sum remote_sum
  local_sum="$(md5sum "$HELPER" | awk '{print $1}')"
  remote_sum="$(ssh "${SSH_OPTS[@]}" "$PEER" 'md5sum "$HOME/.local/lib/claude_oauth_creds.py" 2>/dev/null | awk "{print \$1}"' || true)"
  if [ "$local_sum" != "$remote_sum" ]; then
    scp -q "${SSH_OPTS[@]}" "$HELPER" "${PEER}:.local/lib/claude_oauth_creds.py"
  fi
  remote_helper_path
}

# 원격 셸이 $HOME 을 확장한 실제 경로. stdout 은 경로만.
remote_helper_path() {
  ssh "${SSH_OPTS[@]}" "$PEER" 'echo "$HOME/.local/lib/claude_oauth_creds.py"'
}

do_pull() {
  ssh "${SSH_OPTS[@]}" "$PEER" true 2>/dev/null || die "SSH 실패: $PEER"
  mkdir -p "${HOME}/.claude"
  ensure_remote_helper >/dev/null
  local rpy
  rpy="$(remote_helper_path)"
  # heredoc 을 ssh stdin 에 넣지 않는다 — 파이프와 겹치면 merge stdin 이 빈다.
  ssh "${SSH_OPTS[@]}" "$PEER" python3 "$rpy" extract | python3 "$HELPER" merge
  echo "claude-oauth-peer pull ok from $PEER"
}

do_push() {
  ssh "${SSH_OPTS[@]}" "$PEER" true 2>/dev/null || die "SSH 실패: $PEER"
  ensure_remote_helper >/dev/null
  local rpy
  rpy="$(remote_helper_path)"
  python3 "$HELPER" extract | ssh "${SSH_OPTS[@]}" "$PEER" python3 "$rpy" merge
  echo "claude-oauth-peer push ok to $PEER"
}

do_reconcile() {
  local local_exp peer_exp
  local_exp=$(expires_local)
  peer_exp=$(expires_peer) || die "peer expiresAt 조회 실패: $PEER"
  if [ "$peer_exp" -gt "$local_exp" ]; then
    echo "reconcile: peer newer ($peer_exp > $local_exp) — pull"
    do_pull
  elif [ "$local_exp" -gt "$peer_exp" ]; then
    echo "reconcile: local newer ($local_exp > $peer_exp) — push"
    do_push
  else
    echo "reconcile: noop (expiresAt=$local_exp)"
  fi
}

do_install_wsl() {
  PEER="${PEER:-justant@100.115.252.61}"
  ssh "${SSH_OPTS[@]}" "$PEER" true 2>/dev/null || die "SSH 실패: $PEER"
  ssh "${SSH_OPTS[@]}" "$PEER" "mkdir -p ~/.local/bin ~/.local/lib ~/.config/systemd/user"
  scp -q "${SSH_OPTS[@]}" "$HELPER" "${PEER}:.local/lib/claude_oauth_creds.py"
  scp -q "${SSH_OPTS[@]}" "${SCRIPT_DIR}/claude-oauth-peer.sh" "${PEER}:.local/bin/claude-oauth-peer.sh"
  ssh "${SSH_OPTS[@]}" "$PEER" "chmod 755 ~/.local/bin/claude-oauth-peer.sh ~/.local/lib/claude_oauth_creds.py"
  local watchdog="${SCRIPT_DIR}/../env/scripts/wsl-ops-watchdog-script.sh"
  local watchdog_svc="${SCRIPT_DIR}/../env/scripts/wsl-ops-watchdog.service"
  if [ -f "$watchdog" ]; then
    scp -q "${SSH_OPTS[@]}" "$watchdog" "${PEER}:.config/systemd/user/wsl-ops-watchdog-script.sh"
    ssh "${SSH_OPTS[@]}" "$PEER" "chmod 755 ~/.config/systemd/user/wsl-ops-watchdog-script.sh"
  fi
  if [ -f "$watchdog_svc" ]; then
    scp -q "${SSH_OPTS[@]}" "$watchdog_svc" "${PEER}:.config/systemd/user/wsl-ops-watchdog.service"
    ssh "${SSH_OPTS[@]}" "$PEER" "systemctl --user daemon-reload" || true
  fi
  echo "installed oauth peer + watchdog script on $PEER"
}

case "$ACTION" in
  pull) do_pull ;;
  push) do_push ;;
  reconcile) do_reconcile ;;
  install-wsl) do_install_wsl ;;
  *) die "unknown action: $ACTION" ;;
esac
