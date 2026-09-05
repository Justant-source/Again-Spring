#!/usr/bin/env bash
# persona-diversity-v4 WP1 — regenerate-persona-profiles 재개 자동화.
#
# 배경(docs/_active/persona-diversity-v4.md): 150명 페르소나 신원/문체 축을 Sonnet으로
# 재생성하는 배치가 Claude 세션 한도에 두 번 걸렸다(2026-09-05, dev 6am UTC 리셋 / prod
# 11am UTC 리셋). `PersonaProfileRegenerator`는 한도 시그니처(LlmErrorSignatures)를 감지하면
# 정확히 멈추지만(`haltedReason`), 리셋 후 재실행은 지금까지 사람이 수동으로 트리거를 다시
# 쳐야 했다. 이 스크립트는 그 재시도만 자동화한다 — LLM 호출·정책 판단은 전부
# `POST /admin/trigger/regenerate-persona-profiles`(ai-user-orchestrator, `PersonaProfileRegenerator`)
# 안에서 이미 끝난 것을 그대로 위임할 뿐이다.
#
# 재개 원리: only 없이(=force=false) 같은 seed로 재호출하면 `style_axes`/`voice_profile.
# profile_rev="v4"`가 이미 있는 페르소나는 건너뛴다(PersonaProfileRegenerator.isProfileCurrent) —
# 그래서 이 스크립트는 onlyIds를 넘기지 않고 단순히 같은 요청을 반복하기만 하면 된다.
#
# 응답 스키마(PersonaProfileRegenerator.regenerate 참고): processed/succeeded/skipped/
# remaining/haltedReason/failures[]. remaining==0 → 전원 완료. haltedReason이
# "LLM_ERROR_SIGNATURE: ..." 이면 세션 한도/인증/거절 — 오류 문구에서 리셋 시각을 파싱해
# 그때까지 대기 후 재시도한다. haltedReason이 "CONSECUTIVE_FAILURES(...)" 이면 한도가 아닌
# 실제 결함이므로 재시도하지 않고 실패 종료한다(사람이 봐야 함).
#
# LLM을 이 스크립트가 직접 호출하지 않는다 — 기존 orchestrator admin trigger 엔드포인트
# (내부 도커 네트워크 전용, 외부 미노출)만 호출한다. nightly-ai-user-batch.sh와 같은 패턴
# (docker exec <container> wget --post-data='' http://localhost:8096/admin/trigger/...).
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_FILE="$ENV_DIR/logs/resume-persona-profile-regen.log"
mkdir -p "$(dirname "$LOG_FILE")"

# shellcheck source=./lib/session-reset-time.sh
source "$SCRIPT_DIR/lib/session-reset-time.sh"

# ── 기본값 ────────────────────────────────────────────────────────────────
ENVIRONMENT=""
SEED=""
BATCH=10                     # orchestrator 기본값과 동일(진행률 로그 배치 크기)
MAX_CONSECUTIVE_FAILURES=5   # orchestrator 기본값과 동일
MAX_RETRIES=12               # 한도 리셋 대기 후 재시도 총 상한(무한 루프 금지)
FALLBACK_WAIT_MINUTES=30     # 리셋 시각 파싱 실패 시 폴백 대기
TRIGGER_TIMEOUT_SECONDS=${RESUME_PERSONA_REGEN_TRIGGER_TIMEOUT:-21600}  # wget -T, 6h
I_MEAN_IT=0
DRY_RUN=0

log() { printf '[%s] %s\n' "$(date '+%F %T %Z')" "$*" | tee -a "$LOG_FILE"; }

usage() {
  cat <<'EOF'
사용법: resume-persona-profile-regen.sh --env dev|prod --seed <long> [옵션]

persona-diversity-v4 WP1 페르소나 프로필 재생성(regenerate-persona-profiles)이 세션 한도에
걸려 중단될 때마다, 오류 문구의 리셋 시각까지 자동 대기했다가 재시도해 remaining=0까지 밀어붙인다.

필수:
  --env dev|prod                대상 환경 (컨테이너 이름 선택에 사용)
  --seed N                      QuotaPlanner seed — 전체 재시도 동안 반드시 동일해야 함

옵션:
  --batch N                     진행률 로그 배치 크기 (기본 10)
  --max-consecutive-failures N  트리거 1회 호출당 연속 실패 상한 (기본 5)
  --max-retries N                한도 리셋 대기 후 재시도 총 횟수 상한 (기본 12, 무한루프 금지)
  --fallback-wait-minutes N     리셋 시각 파싱 실패 시 폴백 대기 분 (기본 30)
  --i-mean-it                   --env prod 사용 시 필수 동반 플래그(실수 방지)
  --dry-run                     실제 트리거 호출 없이 내장 픽스처로 재시도 로직만 시연
  -h, --help                    이 도움말

예:
  ./resume-persona-profile-regen.sh --env dev --seed 20260905
  ./resume-persona-profile-regen.sh --env prod --seed 20260905 --i-mean-it
  ./resume-persona-profile-regen.sh --dry-run --env dev --seed 1
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env) ENVIRONMENT="${2:-}"; shift 2 ;;
    --seed) SEED="${2:-}"; shift 2 ;;
    --batch) BATCH="${2:-}"; shift 2 ;;
    --max-consecutive-failures) MAX_CONSECUTIVE_FAILURES="${2:-}"; shift 2 ;;
    --max-retries) MAX_RETRIES="${2:-}"; shift 2 ;;
    --fallback-wait-minutes) FALLBACK_WAIT_MINUTES="${2:-}"; shift 2 ;;
    --i-mean-it) I_MEAN_IT=1; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "알 수 없는 인자: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ -z "$ENVIRONMENT" || -z "$SEED" ]]; then
  echo "ERROR: --env와 --seed는 필수다." >&2
  usage >&2
  exit 2
fi
if ! [[ "$SEED" =~ ^-?[0-9]+$ ]]; then
  echo "ERROR: --seed는 정수여야 한다: $SEED" >&2
  exit 2
fi
for n in "$BATCH" "$MAX_CONSECUTIVE_FAILURES" "$MAX_RETRIES" "$FALLBACK_WAIT_MINUTES"; do
  if ! [[ "$n" =~ ^[0-9]+$ ]]; then
    echo "ERROR: 숫자 인자에 정수가 아닌 값이 들어왔다: $n" >&2
    exit 2
  fi
done

case "$ENVIRONMENT" in
  dev)
    CONTAINER=againspring-ai-user-orchestrator-dev
    ;;
  prod)
    # 🚨 prod 컨테이너 이름에는 -prod 접미사가 붙지 않는다(env/docker-compose.ai-user.yml
    # container_name: againspring-ai-user-orchestrator). dev만 -dev 접미사가 붙는다.
    CONTAINER=againspring-ai-user-orchestrator
    if [[ "$I_MEAN_IT" -ne 1 ]]; then
      echo "ERROR: --env prod는 --i-mean-it 없이 실행할 수 없다(AGENTS.md 절대 규칙 #4 취지 — 실수 방지)." >&2
      exit 2
    fi
    ;;
  *)
    echo "ERROR: --env는 dev 또는 prod여야 한다: $ENVIRONMENT" >&2
    exit 2
    ;;
esac

# ── 텔레그램 알림 — ops-watchdog.sh와 동일 자격 파일·함수를 그대로 재사용한다 ────────
# (새 자격 경로/함수를 만들지 않는다 — AGENTS.md 하위 임무 규칙)
TELEGRAM_ENV="${HOME}/.config/again-spring-watchdog/telegram.env"
TELEGRAM_READY=0
if [[ -f "$TELEGRAM_ENV" ]]; then
  # shellcheck source=/dev/null
  source "$TELEGRAM_ENV"
  if [[ -n "${TELEGRAM_BOT_TOKEN:-}" && -n "${TELEGRAM_CHAT_ID:-}" ]]; then
    TELEGRAM_READY=1
  else
    log "WARN: $TELEGRAM_ENV 는 있지만 TELEGRAM_BOT_TOKEN/TELEGRAM_CHAT_ID가 비어 있음 — 알림 생략"
  fi
else
  log "WARN: $TELEGRAM_ENV 없음 — 텔레그램 알림 생략(로그만 남김)"
fi

send_telegram() {
  local text="$1"
  if [[ "$DRY_RUN" -eq 1 ]]; then
    log "[dry-run] telegram 전송 생략: $text"
    return 0
  fi
  if [[ "$TELEGRAM_READY" -ne 1 ]]; then
    return 0
  fi
  local encoded_msg
  encoded_msg=$(python3 -c "import urllib.parse, sys; print(urllib.parse.quote(sys.argv[1]))" "$text")
  if ! curl -s "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
      -d "chat_id=${TELEGRAM_CHAT_ID}" \
      -d "text=${encoded_msg}" \
      > /dev/null 2>&1; then
    log "WARN: 텔레그램 전송 실패"
  fi
}

# ── JSON 응답 필드 추출 (python3 — 이 저장소 스크립트들의 기존 관례, jq 미설치 가정) ──
extract_field() {
  local json="$1" field="$2"
  python3 -c '
import json, sys
try:
    data = json.loads(sys.argv[1])
except Exception:
    print("")
    sys.exit(0)
val = data.get(sys.argv[2])
print("" if val is None else val)
' "$json" "$field" 2>/dev/null
}

# ── 트리거 호출 ───────────────────────────────────────────────────────────
call_trigger() {
  docker exec "$CONTAINER" wget -qO- -T "$TRIGGER_TIMEOUT_SECONDS" --post-data='' \
    "http://localhost:8096/admin/trigger/regenerate-persona-profiles?seed=${SEED}&batch=${BATCH}&force=false&maxConsecutiveFailures=${MAX_CONSECUTIVE_FAILURES}" \
    2>>"$LOG_FILE"
}

# dry-run용 내장 픽스처 — 실제 haltedReason 문구 두 가지 + 정상 종료를 순서대로 재현해
# 트리거 호출 없이도 대기·파싱·재시도 로직 전체를 시연한다.
DRY_RUN_FIXTURES=(
  '{"processed":6,"succeeded":6,"skipped":0,"remaining":94,"haltedReason":"LLM_ERROR_SIGNATURE: You'"'"'ve hit your session limit · resets 11am (UTC)"}'
  '{"processed":10,"succeeded":10,"skipped":0,"remaining":84,"haltedReason":"LLM_ERROR_SIGNATURE: You'"'"'ve hit your session limit · resets 8pm (Asia/Seoul)"}'
  '{"processed":84,"succeeded":84,"skipped":0,"remaining":0,"haltedReason":null}'
)
# attempt(1부터 시작)를 인자로 받아 인덱스를 계산한다 — $(...) 서브셸 안에서 전역 변수를
# 증가시키는 방식은 서브셸 종료 시 사라지므로 쓰지 않는다(최초 구현에서 실제로 이 버그로
# 첫 픽스처만 무한 반복되는 것을 dry-run 검증 중 발견·수정).
call_trigger_dry_run() {
  local idx=$(( $1 - 1 ))
  if [[ "$idx" -ge "${#DRY_RUN_FIXTURES[@]}" ]]; then
    # 픽스처 소진 — CONSECUTIVE_FAILURES로 종료 경로도 보여준다.
    echo '{"processed":1,"succeeded":0,"skipped":1,"remaining":1,"haltedReason":"CONSECUTIVE_FAILURES(5)"}'
    return
  fi
  echo "${DRY_RUN_FIXTURES[$idx]}"
}

if [[ "$DRY_RUN" -ne 1 ]]; then
  if ! docker ps --filter "name=^/${CONTAINER}$" --filter "status=running" -q | grep -q .; then
    log "ERROR: 컨테이너 ${CONTAINER}가 실행 중이 아니다 — 중단"
    send_telegram "❌ [persona-profile-regen/${ENVIRONMENT}] 컨테이너 ${CONTAINER} 미실행 — 재개 자동화 시작 불가"
    exit 1
  fi
fi

log "=== resume-persona-profile-regen start env=${ENVIRONMENT} container=${CONTAINER} seed=${SEED} batch=${BATCH} max-retries=${MAX_RETRIES} dry-run=${DRY_RUN} ==="

attempt=0
while true; do
  attempt=$((attempt + 1))
  if [[ "$attempt" -gt "$MAX_RETRIES" ]]; then
    log "ERROR: 재시도 상한(${MAX_RETRIES}) 초과 — 중단"
    send_telegram "❌ [persona-profile-regen/${ENVIRONMENT}] 재시도 상한(${MAX_RETRIES}) 초과, 아직 미완료 — 사람 확인 필요"
    exit 1
  fi

  log "attempt ${attempt}/${MAX_RETRIES}: regenerate-persona-profiles 호출 (seed=${SEED})"
  if [[ "$DRY_RUN" -eq 1 ]]; then
    RESPONSE=$(call_trigger_dry_run "$attempt")
  else
    RESPONSE=$(call_trigger)
  fi

  if [[ -z "${RESPONSE:-}" ]]; then
    log "WARN: 트리거 응답이 비어 있음(컨테이너 재시작/네트워크 문제 가능) — ${FALLBACK_WAIT_MINUTES}분 후 재시도"
    send_telegram "⚠️ [persona-profile-regen/${ENVIRONMENT}] 트리거 응답 없음(attempt ${attempt}/${MAX_RETRIES}) — ${FALLBACK_WAIT_MINUTES}분 후 재시도"
    if [[ "$DRY_RUN" -ne 1 ]]; then
      sleep "$((FALLBACK_WAIT_MINUTES * 60))"
    fi
    continue
  fi

  PROCESSED=$(extract_field "$RESPONSE" processed)
  SUCCEEDED=$(extract_field "$RESPONSE" succeeded)
  SKIPPED=$(extract_field "$RESPONSE" skipped)
  REMAINING=$(extract_field "$RESPONSE" remaining)
  HALTED=$(extract_field "$RESPONSE" haltedReason)

  log "result: processed=${PROCESSED:-?} succeeded=${SUCCEEDED:-?} skipped=${SKIPPED:-?} remaining=${REMAINING:-?} haltedReason=${HALTED:-<none>}"

  if [[ "$REMAINING" == "0" ]]; then
    log "=== 전원 완료 (remaining=0) — ${attempt}회 시도 만에 종료 ==="
    send_telegram "✅ [persona-profile-regen/${ENVIRONMENT}] 150명 재생성 완료 (seed=${SEED}, ${attempt}회 시도)"
    exit 0
  fi

  if [[ -z "$HALTED" ]]; then
    log "WARN: remaining=${REMAINING:-?}이지만 haltedReason이 비어 있음(예상치 못한 응답) — 대기 없이 즉시 재시도"
    continue
  fi

  case "$HALTED" in
    CONSECUTIVE_FAILURES*)
      log "ERROR: CONSECUTIVE_FAILURES로 중단 — 한도가 아니라 실제 결함이다. 재시도하지 않는다: ${HALTED}"
      send_telegram "❌ [persona-profile-regen/${ENVIRONMENT}] CONSECUTIVE_FAILURES로 중단(한도 아님, 사람 확인 필요): ${HALTED} · remaining=${REMAINING:-?}"
      exit 1
      ;;
    LLM_ERROR_SIGNATURE:*)
      RESET_EPOCH=""
      if RESET_EPOCH=$(parse_session_reset_epoch "$HALTED"); then
        NOW_EPOCH=$(date +%s)
        WAIT_SECONDS=$((RESET_EPOCH - NOW_EPOCH))
        [[ "$WAIT_SECONDS" -lt 0 ]] && WAIT_SECONDS=0
        RESET_HUMAN=$(date -d "@${RESET_EPOCH}" '+%F %T %Z')
        log "세션 한도 감지 — 리셋 시각 ${RESET_HUMAN} 까지 ${WAIT_SECONDS}초 대기 후 재시도"
        send_telegram "⏸️ [persona-profile-regen/${ENVIRONMENT}] 세션 한도 소진, 리셋(${RESET_HUMAN})까지 대기 후 자동 재시도. remaining=${REMAINING:-?} attempt=${attempt}/${MAX_RETRIES}"
      else
        WAIT_SECONDS=$((FALLBACK_WAIT_MINUTES * 60))
        log "WARN: haltedReason에서 리셋 시각 파싱 실패 — 고정 ${FALLBACK_WAIT_MINUTES}분 폴백 대기: ${HALTED}"
        send_telegram "⚠️ [persona-profile-regen/${ENVIRONMENT}] 리셋 시각 파싱 실패 — 고정 ${FALLBACK_WAIT_MINUTES}분 대기 후 재시도. remaining=${REMAINING:-?} attempt=${attempt}/${MAX_RETRIES}"
      fi
      if [[ "$DRY_RUN" -eq 1 ]]; then
        log "[dry-run] 실제로는 sleep ${WAIT_SECONDS}s 하지 않고 바로 다음 픽스처로 진행"
      else
        sleep "$WAIT_SECONDS"
      fi
      continue
      ;;
    *)
      log "ERROR: 알 수 없는 haltedReason — 자동 재시도 대상이 아니다: ${HALTED}"
      send_telegram "❌ [persona-profile-regen/${ENVIRONMENT}] 알 수 없는 haltedReason — 확인 필요: ${HALTED} · remaining=${REMAINING:-?}"
      exit 1
      ;;
  esac
done
