#!/usr/bin/env bash
#
# AS ↔ WSL Claude Code oauth 동기화.
# claudeAiOauth 만 옮긴다. 토큰은 SSH 파이프에만 두고 화면에 찍지 않는다.
#
#   pull <user@host>         피어 → 로컬 병합
#   push <user@host>         로컬 → 피어 병합
#   reconcile <user@host>    expiresAt 이 더 큰 쪽을 양쪽에 맞춤 (같으면 noop)
#
set -euo pipefail

ACTION="${1:-}"
PEER="${2:-}"

die() { echo "ERROR: $*" >&2; exit 1; }

[ -n "$ACTION" ] && [ -n "$PEER" ] || die "usage: $0 pull|push|reconcile user@host"

expires_local() {
  python3 - <<'PY'
import json, os
p = os.path.expanduser("~/.claude/.credentials.json")
if not os.path.isfile(p):
    print(0)
    raise SystemExit
d = json.load(open(p))
oauth = d.get("claudeAiOauth") or {}
print(int(oauth.get("expiresAt") or 0))
PY
}

expires_peer() {
  ssh -o BatchMode=yes -o ConnectTimeout=10 "$PEER" python3 - <<'PY'
import json, os
p = os.path.expanduser("~/.claude/.credentials.json")
if not os.path.isfile(p):
    print(0)
    raise SystemExit
d = json.load(open(p))
oauth = d.get("claudeAiOauth") or {}
print(int(oauth.get("expiresAt") or 0))
PY
}

extract_oauth() {
  python3 - <<'PY'
import json, os, sys
p = os.path.expanduser("~/.claude/.credentials.json")
if not os.path.isfile(p):
    sys.stderr.write("local credentials missing\n")
    raise SystemExit(2)
d = json.load(open(p))
if "claudeAiOauth" not in d:
    sys.stderr.write("local claudeAiOauth missing\n")
    raise SystemExit(3)
print(json.dumps({"claudeAiOauth": d["claudeAiOauth"]}))
PY
}

merge_local_from_stdin() {
  python3 -c "
import json, os, shutil, sys, time
incoming = json.load(sys.stdin)
if 'claudeAiOauth' not in incoming:
    print('ERROR: incoming oauth 없음', file=sys.stderr)
    raise SystemExit(1)
path = os.path.expanduser('~/.claude/.credentials.json')
current = {}
if os.path.exists(path):
    backup = path + '.bak-' + time.strftime('%Y%m%d-%H%M%S')
    shutil.copy2(path, backup)
    print('backup :', backup)
    with open(path) as f:
        current = json.load(f)
before = current.get('claudeAiOauth') or {}
current['claudeAiOauth'] = incoming['claudeAiOauth']
os.makedirs(os.path.dirname(path), exist_ok=True)
fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
with os.fdopen(fd, 'w') as f:
    json.dump(current, f, indent=2)
after = current['claudeAiOauth']
print('written:', path, '(mode 600)')
print('  subscriptionType:', before.get('subscriptionType'), '->', after.get('subscriptionType'))
"
}

# 파이프 JSON → 원격 python stdin. 원격 코드는 -c, heredoc은 ssh stdin을 뺏음.
merge_peer_from_stdin() {
  ssh -o BatchMode=yes -o ConnectTimeout=10 "$PEER" python3 -c "
import json, os, shutil, sys, time
incoming = json.load(sys.stdin)
if 'claudeAiOauth' not in incoming:
    print('ERROR: incoming oauth 없음', file=sys.stderr)
    raise SystemExit(1)
path = os.path.expanduser('~/.claude/.credentials.json')
current = {}
if os.path.exists(path):
    backup = path + '.bak-' + time.strftime('%Y%m%d-%H%M%S')
    shutil.copy2(path, backup)
    print('backup :', backup)
    with open(path) as f:
        current = json.load(f)
before = current.get('claudeAiOauth') or {}
current['claudeAiOauth'] = incoming['claudeAiOauth']
os.makedirs(os.path.dirname(path), exist_ok=True)
fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
with os.fdopen(fd, 'w') as f:
    json.dump(current, f, indent=2)
after = current['claudeAiOauth']
print('written:', path, '(mode 600)')
print('  subscriptionType:', before.get('subscriptionType'), '->', after.get('subscriptionType'))
"
}

do_pull() {
  ssh -o BatchMode=yes -o ConnectTimeout=10 "$PEER" true 2>/dev/null || die "SSH 실패: $PEER"
  mkdir -p "${HOME}/.claude"
  ssh -o BatchMode=yes "$PEER" python3 - <<'PY' | merge_local_from_stdin
import json, os, sys
p = os.path.expanduser("~/.claude/.credentials.json")
if not os.path.isfile(p):
    sys.stderr.write("peer credentials missing\n")
    raise SystemExit(2)
d = json.load(open(p))
if "claudeAiOauth" not in d:
    sys.stderr.write("peer claudeAiOauth missing\n")
    raise SystemExit(3)
print(json.dumps({"claudeAiOauth": d["claudeAiOauth"]}))
PY
  echo "claude-oauth-peer pull ok from $PEER"
}

do_push() {
  ssh -o BatchMode=yes -o ConnectTimeout=10 "$PEER" true 2>/dev/null || die "SSH 실패: $PEER"
  extract_oauth | merge_peer_from_stdin
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

case "$ACTION" in
  pull) do_pull ;;
  push) do_push ;;
  reconcile) do_reconcile ;;
  *) die "unknown action: $ACTION" ;;
esac
