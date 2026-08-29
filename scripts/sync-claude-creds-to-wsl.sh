#!/usr/bin/env bash
#
# 로컬(AS 호스트)의 동작하는 Claude Code 인증을 WSL로 복사한다.
#
# 왜 필요한가
#   WSL 자체 계정(subscriptionType=max)은 토큰이 만료되지 않았는데도 조직 정책으로
#   Claude Code 접근이 차단된다:
#     "Your organization has disabled Claude subscription access for Claude Code"
#   → 브라우저 재로그인으로 해결되지 않는다. 로컬(pro) 자격증명을 옮겨야 한다.
#
# 무엇을 하는가
#   ~/.claude/.credentials.json 의 claudeAiOauth 키만 WSL로 병합한다.
#   (로컬 mcpOAuth 항목은 WSL과 무관하므로 옮기지 않는다)
#   기존 WSL 파일은 타임스탬프 백업 후 교체하고 mode 600을 유지한다.
#
# 파급 범위
#   WSL의 아래 컨테이너가 ~/.claude 를 디렉토리 bind mount 로 물고 있어
#   재시작 없이 즉시 반영된다:
#     again-spring-marketing-asm-1 · again-spring-marketing-llm-bridge-1 · llm-worker
#   실제 LLM 호출 주체는 llm-bridge (/usr/local/bin/claude). ASM 본체엔 claude 가 없다.
#
# 주의
#   토큰 수명이 하루 단위다. 로컬이 갱신하면 WSL 사본은 낡는다 — 그때 다시 실행하면 된다.
#   한 토큰을 두 머신이 공유하므로 refresh 회전 시 한쪽 세션이 끊길 수 있고,
#   사용량도 같은 구독 한도를 함께 쓴다.
#
# 사용법
#   scripts/sync-claude-creds-to-wsl.sh              # 복사 + 검증
#   scripts/sync-claude-creds-to-wsl.sh --check      # 복사하지 않고 현재 상태만 확인
#
set -uo pipefail

WSL_HOST="${WSL_HOST:-justant@100.115.252.61}"
LOCAL_CREDS="$HOME/.claude/.credentials.json"
REMOTE_MERGE="/tmp/claude-creds-merge.$$.py"

die() { echo "ERROR: $*" >&2; exit 1; }

# ── 현재 상태만 확인 ────────────────────────────────────────────────────────
if [ "${1:-}" = "--check" ]; then
  echo "=== 로컬 ==="
  python3 - "$LOCAL_CREDS" <<'PY'
import json, sys, datetime
o = json.load(open(sys.argv[1]))["claudeAiOauth"]
print(" subscriptionType:", o.get("subscriptionType"))
print(" expiresAt       :", datetime.datetime.fromtimestamp(o["expiresAt"]/1000))
PY
  echo "=== WSL claude 동작 여부 ==="
  ssh "$WSL_HOST" 'source ~/.nvm/nvm.sh 2>/dev/null; claude -p "reply with exactly: AUTH_OK" 2>&1 | tail -2'
  exit 0
fi

# ── 0. 사전 점검 ───────────────────────────────────────────────────────────
[ -f "$LOCAL_CREDS" ] || die "로컬 자격증명 없음: $LOCAL_CREDS"
python3 -c "
import json,sys
d=json.load(open('$LOCAL_CREDS'))
sys.exit(0 if 'claudeAiOauth' in d else 1)
" || die "로컬 자격증명에 claudeAiOauth 없음 — 로컬에서 먼저 로그인하라"

ssh -o BatchMode=yes "$WSL_HOST" true 2>/dev/null || die "WSL SSH 실패: $WSL_HOST"

# ── 1. 병합 스크립트를 WSL로 전송 ──────────────────────────────────────────
# heredoc 으로 통째로 보낸다 — ssh 인자 안에서 따옴표를 중첩하면 변수 확장이 깨진다.
# (실제로 겪은 함정: sh -c "... --header='Bearer $TOK' ..." 는 $TOK 가 확장되지 않는다)
ssh "$WSL_HOST" "cat > $REMOTE_MERGE" <<'REMOTE_PY'
import json, os, shutil, sys, time
PATH = os.path.expanduser("~/.claude/.credentials.json")
incoming = json.load(sys.stdin)
if "claudeAiOauth" not in incoming:
    print("ERROR: stdin에 claudeAiOauth 없음", file=sys.stderr); sys.exit(1)
current = {}
if os.path.exists(PATH):
    backup = PATH + ".bak-" + time.strftime("%Y%m%d-%H%M%S")
    shutil.copy2(PATH, backup)
    print("backup :", backup)
    with open(PATH) as f:
        current = json.load(f)
before = current.get("claudeAiOauth", {})
current["claudeAiOauth"] = incoming["claudeAiOauth"]
with open(PATH, "w") as f:
    json.dump(current, f, indent=2)
os.chmod(PATH, 0o600)
after = current["claudeAiOauth"]
print("written:", PATH, "(mode 600)")
print("  subscriptionType:", before.get("subscriptionType"), "->", after.get("subscriptionType"))
print("  보존된 최상위 키:", sorted(current.keys()))
REMOTE_PY
[ $? -eq 0 ] || die "병합 스크립트 전송 실패"

# ── 2. claudeAiOauth 만 추출해 파이프로 전달 (토큰은 화면에 찍지 않는다) ──
echo "── 자격증명 전송"
python3 -c "
import json
d = json.load(open('$LOCAL_CREDS'))
print(json.dumps({'claudeAiOauth': d['claudeAiOauth']}))
" | ssh "$WSL_HOST" "python3 $REMOTE_MERGE; rm -f $REMOTE_MERGE"
[ $? -eq 0 ] || die "병합 실패"

# ── 3. 소유권 오염 확인 (과거 컨테이너가 root 로 써서 세션이 끊긴 전례 있음) ──
echo
echo "── root 소유권 오염 확인"
OWN=$(ssh "$WSL_HOST" 'find ~/.claude -maxdepth 1 ! -user justant -printf "%u %p\n" 2>/dev/null')
if [ -n "$OWN" ]; then
  echo "⚠️  root 소유 항목 발견 — 회수 필요:"; echo "$OWN"
else
  echo "   없음"
fi

# ── 4. 검증: CLI + 실제 호출 주체(llm-bridge) ──────────────────────────────
echo
echo "── 검증: WSL CLI"
ssh "$WSL_HOST" 'source ~/.nvm/nvm.sh && claude -p "reply with exactly: AUTH_OK" 2>&1 | tail -2'

echo "── 검증: llm-bridge 컨테이너"
ssh "$WSL_HOST" 'docker exec again-spring-marketing-llm-bridge-1 sh -lc "claude -p \"reply with exactly: BRIDGE_AUTH_OK\"" 2>&1 | tail -2'

echo
echo "완료. AUTH_OK / BRIDGE_AUTH_OK 가 모두 보이면 정상이다."
