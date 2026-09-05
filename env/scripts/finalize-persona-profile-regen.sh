#!/usr/bin/env bash
# persona-diversity-v4 Phase 4 — 150명 재생성 완료 후 마무리 러너.
#
# 배경(docs/_active/persona-diversity-v4.md §7): 150명 페르소나 프로필 재생성
# (`POST /admin/trigger/regenerate-persona-profiles`, `env/scripts/resume-persona-profile-regen.sh`가
# 세션 한도 리셋 재시도만 자동화)이 끝난 뒤에는 사람이 순서대로 여러 명령을 직접 쳐야 했다.
# 이 스크립트는 그 "완료 후" 순서를 하나로 묶어 자동 실행하고 결과를 표로 정리한다.
#
# 순서가 중요하다(뒤 단계일수록 앞 단계 완료를 전제한다):
#   1) 재생성 완료 확인 — voice_profile.profile_rev 마커가 현재 리비전인 페르소나가
#      PERSONA_COUNT(150)명인가 (PersonaProfileRegenerator.CURRENT_PROFILE_REV과 동기화 필요).
#      미완료면 이후 단계 전부 중단 — 특히 3)은 marital이 확정되지 않은 채로 돌리면
#      MARRIAGE/COUPLE 관계가 하나도 안 생긴다(§7.3, docs/ai-user/60-runtime/operations.md §9).
#   2) 게이트 a(분포)·b(다양성) 실행(`ai-user/tools/persona_gate_check.py`). 배포 게이트 —
#      FAIL이면 3)~5) 전부 중단한다(관계 부여는 prod 쓰기이므로 분포가 틀린 채로 진행하지 않는다).
#   3) 관계 부여 — `POST /admin/trigger/fill-persona-relationships?seed=<n>` (PersonaRelationshipFiller,
#      기존 관계는 유지·존중, coverage만 채운다).
#   4) 게이트 d(관계) 실행. 실패해도 5)는 계속 진행한다(5)는 순수 조회라 막을 이유가 없다) —
#      다만 스크립트 전체 종료 코드에는 반영한다.
#   5) 게이트 c(회전, 참고용) 실행 — 배포 게이트가 아니다. 7일 운영 후에야 의미가 있으므로
#      FAIL 판정 자체가 없고(persona_gate_check.py가 항상 exit 0), 이 스크립트도 결과와 무관하게
#      진행·종료 코드에 반영하지 않는다. 지금 시점 실행분은 향후 비교용 기준선일 뿐이다.
#   6) 전체 결과 요약 표 출력.
#
# LLM을 이 스크립트가 직접 호출하지 않는다. 3)만 prod/dev DB에 쓴다(PersonaRelationshipFiller
# 내부에서). 1)·2)·4)·5)는 DB 읽기만 한다(persona_gate_check.py 및 이 스크립트의 COUNT 쿼리).
# docker 컨테이너를 재빌드·재시작하지 않는다 — 기존 orchestrator admin trigger 엔드포인트
# (내부 도커 네트워크 전용, 외부 미노출)와 게이트 스크립트만 호출한다.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ROOT_DIR="$(cd "$ENV_DIR/.." && pwd)"
LOG_FILE="$ENV_DIR/logs/finalize-persona-profile-regen.log"
mkdir -p "$(dirname "$LOG_FILE")"

GATE_SCRIPT="$ROOT_DIR/ai-user/tools/persona_gate_check.py"

# ── 기본값 ────────────────────────────────────────────────────────────────
ENVIRONMENT=""
RELATIONSHIP_SEED=""
GATE_C_DAYS=7
# PersonaProfileRegenerator.CURRENT_PROFILE_REV / persona_gate_check.py의 CURRENT_PROFILE_REV와
# 동일해야 한다 — 축 배정 알고리즘이 바뀌어 리비전이 오르면 이 값도 같이 올릴 것.
PROFILE_REV="v5"
PERSONA_COUNT=150   # PersonaQuotaPlanner.PERSONA_COUNT
TRIGGER_TIMEOUT_SECONDS=${FINALIZE_PERSONA_REGEN_TRIGGER_TIMEOUT:-300}
I_MEAN_IT=0
DRY_RUN=0

log() { printf '[%s] %s\n' "$(date '+%F %T %Z')" "$*" | tee -a "$LOG_FILE"; }
# run_gate()의 stdout은 command substitution으로 캡처해 RESULT 페이로드로 쓴다 — 그 안에서
# log()(stdout에도 씀)를 호출하면 페이로드가 오염된다. 그 함수 내부 알림 전용으로 stderr에만 쓴다.
log_err() { printf '[%s] %s\n' "$(date '+%F %T %Z')" "$*" | tee -a "$LOG_FILE" >&2; }

usage() {
  cat <<'EOF'
사용법: finalize-persona-profile-regen.sh --env dev|prod --relationship-seed <long> [옵션]

persona-diversity-v4 150명 페르소나 프로필 재생성이 끝난 뒤 해야 하는 마무리 절차
(완료 확인 → 게이트 a·b → 관계 부여 → 게이트 d → 게이트 c → 요약)를 순서대로 실행한다.

필수:
  --env dev|prod              대상 환경 (컨테이너·env 파일 선택에 사용)
  --relationship-seed N       fill-persona-relationships 호출에 쓸 seed

옵션:
  --gate-c-days N              게이트 c(회전) 집계 기간(일), 기본 7
  --i-mean-it                  --env prod 사용 시 필수 동반 플래그(실수 방지)
  --dry-run                    실제 DB 조회·트리거 호출 없이 내장 픽스처로 전체 흐름만 시연
  -h, --help                   이 도움말

예:
  ./finalize-persona-profile-regen.sh --env dev --relationship-seed 20260905
  ./finalize-persona-profile-regen.sh --env prod --relationship-seed 20260905 --i-mean-it
  ./finalize-persona-profile-regen.sh --dry-run --env prod --relationship-seed 1

판정 기준 요약(실패 시 볼 곳):
  1) 재생성 완료   : profile_rev='v5' 페르소나 수 == 150. 미달이면
                     env/scripts/resume-persona-profile-regen.sh 로 재개하거나
                     env/logs/resume-persona-profile-regen.log 확인.
  2) 게이트 a·b    : persona_gate_check.py 종료 코드 0. 실패 항목은 출력의 [FAIL] 줄 참고.
                     종료 코드 2면 V22 마이그레이션 미적용 — orchestrator flyway 로그 확인.
  3) 관계 부여     : 트리거 응답에 status=error 가 없으면 호출 성공(내용 정합성은 4에서 판정).
                     실패 시 컨테이너 상태(docker ps)와 orchestrator 로그 확인.
  4) 게이트 d      : persona_gate_check.py --gate d 종료 코드 0. uncovered_personas·
                     gender_age_violations·marital_consistency_violations 각 detail 확인.
                     기존 시드에 섞여 있던 위반(동성 COUPLE 등)은 관계 재부여로 안 고쳐질 수
                     있다 — docs/ai-user/60-runtime/operations.md §9 참고.
  5) 게이트 c      : 참고용, 항상 진행 — 오늘 실행분은 7일 후 비교용 기준선으로만 쓴다.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env) ENVIRONMENT="${2:-}"; shift 2 ;;
    --relationship-seed) RELATIONSHIP_SEED="${2:-}"; shift 2 ;;
    --gate-c-days) GATE_C_DAYS="${2:-}"; shift 2 ;;
    --i-mean-it) I_MEAN_IT=1; shift ;;
    --dry-run) DRY_RUN=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "알 수 없는 인자: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ -z "$ENVIRONMENT" || -z "$RELATIONSHIP_SEED" ]]; then
  echo "ERROR: --env와 --relationship-seed는 필수다." >&2
  usage >&2
  exit 2
fi
if ! [[ "$RELATIONSHIP_SEED" =~ ^-?[0-9]+$ ]]; then
  echo "ERROR: --relationship-seed는 정수여야 한다: $RELATIONSHIP_SEED" >&2
  exit 2
fi
if ! [[ "$GATE_C_DAYS" =~ ^[0-9]+$ ]]; then
  echo "ERROR: --gate-c-days는 정수여야 한다: $GATE_C_DAYS" >&2
  exit 2
fi

case "$ENVIRONMENT" in
  dev)
    ORCH_CONTAINER=againspring-ai-user-orchestrator-dev
    DB_CONTAINER=againspring-mariadb-dev
    ENV_FILE="$ENV_DIR/.env.dev"
    ;;
  prod)
    # 🚨 prod 컨테이너 이름에는 -prod 접미사가 붙지 않는다(env/docker-compose.ai-user.yml
    # container_name: againspring-ai-user-orchestrator). dev만 -dev 접미사가 붙는다.
    ORCH_CONTAINER=againspring-ai-user-orchestrator
    DB_CONTAINER=againspring-mariadb-prod
    ENV_FILE="$ENV_DIR/.env.prod"
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

# ── 텔레그램 알림 — ops-watchdog.sh / resume-persona-profile-regen.sh와 동일 자격
# 파일·함수를 그대로 재사용한다(새 자격 경로/함수를 만들지 않는다 — AGENTS.md 하위 임무 규칙) ──
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
if isinstance(val, bool):
    print("true" if val else "false")
else:
    print("" if val is None else val)
' "$json" "$field" 2>/dev/null
}

# 게이트 스크립트 --json 출력에서 실패한 체크 이름·detail만 뽑아 보여준다(가이드 문구용).
extract_failed_checks() {
  local json="$1"
  python3 -c '
import json, sys
try:
    data = json.loads(sys.argv[1])
except Exception:
    sys.exit(0)
for c in data.get("checks", []):
    if not c.get("pass", True):
        print("    - {}: {}".format(c.get("check"), c.get("detail")))
' "$json" 2>/dev/null
}

# ── 요약 표 데이터 축적 ──────────────────────────────────────────────────
declare -a SUMMARY_STEPS=()
declare -a SUMMARY_STATUS=()
declare -a SUMMARY_DETAIL=()

add_summary() {
  SUMMARY_STEPS+=("$1")
  SUMMARY_STATUS+=("$2")
  SUMMARY_DETAIL+=("$3")
}

print_summary() {
  log ""
  log "=== 요약 (env=${ENVIRONMENT} dry-run=${DRY_RUN}) ==="
  printf '%-28s | %-6s | %s\n' "단계" "상태" "상세" | tee -a "$LOG_FILE"
  printf '%s\n' "-----------------------------|--------|-----------------------------------" | tee -a "$LOG_FILE"
  local i
  for i in "${!SUMMARY_STEPS[@]}"; do
    printf '%-28s | %-6s | %s\n' "${SUMMARY_STEPS[$i]}" "${SUMMARY_STATUS[$i]}" "${SUMMARY_DETAIL[$i]}" | tee -a "$LOG_FILE"
  done
}

# ── DB 헬퍼 (nightly-ai-user-batch.sh / persona_gate_check.py와 동일 관례: docker exec mariadb) ──
db_query() {
  # --raw: mariadb -B(batch) 모드의 이중 백슬래시 이스케이프 방지(persona_gate_check.py와 동일 이유).
  docker exec "$DB_CONTAINER" mariadb --raw -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -N -B -e "$1" 2>>"$LOG_FILE"
}

if [[ "$DRY_RUN" -ne 1 ]]; then
  if [[ ! -f "$ENV_FILE" ]]; then
    log "ERROR: $ENV_FILE 없음 — 중단"
    exit 1
  fi
  DB_USER=$(grep -oP '^MARIADB_USER=\K.*' "$ENV_FILE")
  DB_PASS=$(grep -oP '^MARIADB_PASSWORD=\K.*' "$ENV_FILE")
  DB_NAME=$(grep -oP '^MARIADB_DATABASE=\K.*' "$ENV_FILE")
  if [[ -z "$DB_USER" || -z "$DB_NAME" ]]; then
    log "ERROR: $ENV_FILE 에서 MARIADB_USER/MARIADB_DATABASE를 읽지 못함 — 중단"
    exit 1
  fi

  if ! docker ps --filter "name=^/${ORCH_CONTAINER}$" --filter "status=running" -q | grep -q .; then
    log "ERROR: 컨테이너 ${ORCH_CONTAINER}가 실행 중이 아니다 — 중단"
    send_telegram "❌ [persona-regen-finalize/${ENVIRONMENT}] 컨테이너 ${ORCH_CONTAINER} 미실행 — 마무리 러너 시작 불가"
    exit 1
  fi
  if ! docker ps --filter "name=^/${DB_CONTAINER}$" --filter "status=running" -q | grep -q .; then
    log "ERROR: 컨테이너 ${DB_CONTAINER}가 실행 중이 아니다 — 중단"
    send_telegram "❌ [persona-regen-finalize/${ENVIRONMENT}] 컨테이너 ${DB_CONTAINER} 미실행 — 마무리 러너 시작 불가"
    exit 1
  fi
fi

log "=== finalize-persona-profile-regen start env=${ENVIRONMENT} orch=${ORCH_CONTAINER} db=${DB_CONTAINER} relationship-seed=${RELATIONSHIP_SEED} gate-c-days=${GATE_C_DAYS} dry-run=${DRY_RUN} ==="
send_telegram "▶️ [persona-regen-finalize/${ENVIRONMENT}] 마무리 러너 시작(1.완료확인→2.게이트a·b→3.관계부여→4.게이트d→5.게이트c)"

OVERALL_RC=0

# ── 1) 재생성 완료 확인 ─────────────────────────────────────────────────
log "--- 1) 재생성 완료 확인 (profile_rev='${PROFILE_REV}' 페르소나 수 == ${PERSONA_COUNT}) ---"
if [[ "$DRY_RUN" -eq 1 ]]; then
  REGEN_COUNT=150
  TOTAL_ACTIVE=150
  log "[dry-run] DB 조회 생략 — 픽스처: regen_count=${REGEN_COUNT} total_active=${TOTAL_ACTIVE}"
else
  REGEN_COUNT=$(db_query "SELECT COUNT(*) FROM personas WHERE active=1 AND style_axes IS NOT NULL AND JSON_UNQUOTE(JSON_EXTRACT(voice_profile,'\$.profile_rev'))='${PROFILE_REV}';" | tr -d '[:space:]')
  TOTAL_ACTIVE=$(db_query "SELECT COUNT(*) FROM personas WHERE active=1;" | tr -d '[:space:]')
  log "재생성 완료 카운트: ${REGEN_COUNT:-<조회실패>} / 활성 전체 ${TOTAL_ACTIVE:-<조회실패>} (목표 ${PERSONA_COUNT})"
fi

if [[ "${REGEN_COUNT:-0}" =~ ^[0-9]+$ ]] && [[ "$REGEN_COUNT" -eq "$PERSONA_COUNT" ]]; then
  add_summary "1. 재생성 완료 확인" "PASS" "profile_rev='${PROFILE_REV}' ${REGEN_COUNT}/${PERSONA_COUNT}"
  log "1) PASS — 재생성 ${REGEN_COUNT}/${PERSONA_COUNT}명 완료"
else
  add_summary "1. 재생성 완료 확인" "FAIL" "profile_rev='${PROFILE_REV}' ${REGEN_COUNT:-?}/${PERSONA_COUNT} (미완료 ${TOTAL_ACTIVE:-?}명 중)"
  log "1) FAIL — 재생성 미완료(${REGEN_COUNT:-?}/${PERSONA_COUNT}). 이후 단계를 진행하지 않는다."
  log "   확인할 곳: env/scripts/resume-persona-profile-regen.sh 로 재개, 또는"
  log "   env/logs/resume-persona-profile-regen.log 의 최근 haltedReason."
  send_telegram "⏸️ [persona-regen-finalize/${ENVIRONMENT}] 1) 재생성 미완료(${REGEN_COUNT:-?}/${PERSONA_COUNT}) — 중단, 게이트/관계부여 실행 안 함"
  print_summary
  exit 1
fi

# ── 게이트 스크립트 호출 래퍼 ────────────────────────────────────────────
# DRY_RUN_GATE_FIXTURES: gate → (exit_code, json). 150명 정상 분포/다양성/무위반 픽스처.
run_gate() {
  local gate="$1"
  local extra_args="${2:-}"
  local json rc
  if [[ "$DRY_RUN" -eq 1 ]]; then
    case "$gate" in
      a)
        json='{"gate":"a","passed":true,"note":"재생성 진척: 150/150 완료, 0명 미재생성(컬럼 기본값)","checks":[{"check":"persona_count","pass":true,"detail":"active personas=150"},{"check":"gender:M","pass":true,"detail":"actual=75 quota=75 diff=0 (허용 ±3)"}]}'
        rc=0
        ;;
      b)
        json='{"gate":"b","passed":true,"note":"재생성 진척: 150/150 완료, 0명 미재생성(컬럼 기본값)","checks":[{"check":"signature_phrases_unique","pass":true,"detail":"unique=147 min=140"},{"check":"general_style_pairwise_jaccard","pass":true,"detail":"max=0.0623 threshold<0.1 worst_pair=(12, 88)"}]}'
        rc=0
        ;;
      d)
        json='{"gate":"d","passed":true,"note":"관계 유형별(ACTIVE): {\"COUPLE\": 30, \"MARRIAGE\": 90, \"FRIEND\": 40}","checks":[{"check":"uncovered_personas","pass":true,"detail":"count=0 (COUPLE|MARRIAGE|FRIEND ACTIVE 관계가 0개인 활성 페르소나)"},{"check":"gender_age_violations","pass":true,"detail":"count=0"},{"check":"marital_consistency_violations","pass":true,"detail":"count=0"}]}'
        rc=0
        ;;
      c)
        json='{"gate":"c","passed":true,"note":"참고용 — 배포 게이트 아님, 항상 PASS 취급","checks":[{"check":"posting_persona_share","pass":true,"detail":"actual=92.7% target>=90% raw_pass=true"},{"check":"top10_post_share","pass":true,"detail":"actual=18.4% target<25% raw_pass=true"},{"check":"comments_under_30_share","pass":true,"detail":"count=6/128 (4.7%) — 참고치, PASS/FAIL 없음"}]}'
        rc=0
        ;;
      *)
        json='{}'
        rc=2
        ;;
    esac
    log_err "[dry-run] persona_gate_check.py --gate ${gate} 호출 생략 — 픽스처 사용"
  else
    json=$(python3 "$GATE_SCRIPT" --env-file "$ENV_FILE" --gate "$gate" --json $extra_args 2>>"$LOG_FILE")
    rc=$?
  fi
  printf '%s\x1e%s' "$rc" "$json"
}

# ── 2) 게이트 a·b (배포 게이트 — FAIL이면 3)~5) 중단) ────────────────────
log "--- 2) 게이트 a(분포)·b(다양성) 실행 ---"
GATE_AB_FAILED=0
for g in a b; do
  RESULT=$(run_gate "$g")
  RC="${RESULT%%$'\x1e'*}"
  JSON="${RESULT#*$'\x1e'}"
  PASSED=$(extract_field "$JSON" passed)
  NOTE=$(extract_field "$JSON" note)
  log "게이트 ${g}: exit=${RC} passed=${PASSED:-?} note=${NOTE:-<none>}"
  if [[ "$RC" == "2" ]]; then
    add_summary "2. 게이트 ${g}" "FAIL" "V22 컬럼 미적용(exit=2) — orchestrator flyway 로그 확인"
    GATE_AB_FAILED=1
  elif [[ "$RC" == "1" ]]; then
    add_summary "2. 게이트 ${g}" "FAIL" "${NOTE:-} — 실패 항목:"
    extract_failed_checks "$JSON" | tee -a "$LOG_FILE"
    GATE_AB_FAILED=1
  elif [[ "$RC" == "0" && "$PASSED" == "true" ]]; then
    add_summary "2. 게이트 ${g}" "PASS" "${NOTE:-}"
  else
    add_summary "2. 게이트 ${g}" "FAIL" "exit=${RC} passed=${PASSED:-?} — persona_gate_check.py 예상치 못한 응답(로그 확인)"
    GATE_AB_FAILED=1
  fi
done

if [[ "$GATE_AB_FAILED" -eq 1 ]]; then
  log "2) FAIL — 게이트 a 또는 b 실패. 3)~5) 실행하지 않는다(관계 부여는 prod/dev DB 쓰기)."
  send_telegram "❌ [persona-regen-finalize/${ENVIRONMENT}] 2) 게이트 a/b FAIL — 중단, 관계부여 실행 안 함. 상세는 로그 참고"
  print_summary
  exit 1
fi
log "2) PASS — 게이트 a·b 통과"

# ── 3) 관계 부여 ─────────────────────────────────────────────────────────
log "--- 3) 관계 부여: POST /admin/trigger/fill-persona-relationships?seed=${RELATIONSHIP_SEED} ---"
if [[ "$DRY_RUN" -eq 1 ]]; then
  FILL_RESPONSE='{"totalActive":150,"coveredBefore":118,"coveredAfter":150,"created":42,"stillUncovered":0}'
  log "[dry-run] 트리거 호출 생략 — 픽스처: ${FILL_RESPONSE}"
else
  FILL_RESPONSE=$(docker exec "$ORCH_CONTAINER" wget -qO- -T "$TRIGGER_TIMEOUT_SECONDS" --post-data='' \
    "http://localhost:8096/admin/trigger/fill-persona-relationships?seed=${RELATIONSHIP_SEED}" \
    2>>"$LOG_FILE")
  log "fill-persona-relationships 응답: ${FILL_RESPONSE:-<empty>}"
fi

FILL_STATUS=$(extract_field "${FILL_RESPONSE:-}" status)
if [[ -z "${FILL_RESPONSE:-}" ]]; then
  add_summary "3. 관계 부여" "FAIL" "응답 없음(컨테이너/네트워크 문제 가능)"
  log "3) FAIL — 응답 없음. 4)는 실제 데이터로 판정되므로 계속 진행하되 실패로 기록한다."
  send_telegram "❌ [persona-regen-finalize/${ENVIRONMENT}] 3) fill-persona-relationships 응답 없음 — orchestrator 로그 확인 필요"
  STEP3_OK=0
elif [[ "$FILL_STATUS" == "error" ]]; then
  FILL_MSG=$(extract_field "$FILL_RESPONSE" message)
  add_summary "3. 관계 부여" "FAIL" "status=error message=${FILL_MSG:-?}"
  log "3) FAIL — status=error message=${FILL_MSG:-?}. PersonaRelationshipFiller 예외 — orchestrator 로그 확인."
  send_telegram "❌ [persona-regen-finalize/${ENVIRONMENT}] 3) fill-persona-relationships 실패: ${FILL_MSG:-?}"
  STEP3_OK=0
else
  TOTAL_ACTIVE_F=$(extract_field "$FILL_RESPONSE" totalActive)
  COVERED_BEFORE=$(extract_field "$FILL_RESPONSE" coveredBefore)
  COVERED_AFTER=$(extract_field "$FILL_RESPONSE" coveredAfter)
  CREATED=$(extract_field "$FILL_RESPONSE" created)
  STILL_UNCOVERED=$(extract_field "$FILL_RESPONSE" stillUncovered)
  add_summary "3. 관계 부여" "OK" "totalActive=${TOTAL_ACTIVE_F:-?} covered ${COVERED_BEFORE:-?}→${COVERED_AFTER:-?} created=${CREATED:-?} stillUncovered=${STILL_UNCOVERED:-?}"
  log "3) 호출 성공 — totalActive=${TOTAL_ACTIVE_F:-?} covered ${COVERED_BEFORE:-?}→${COVERED_AFTER:-?} created=${CREATED:-?} stillUncovered=${STILL_UNCOVERED:-?}"
  if [[ "${STILL_UNCOVERED:-0}" != "0" ]]; then
    log "   WARN: stillUncovered=${STILL_UNCOVERED} — 게이트 d에서 uncovered_personas로 다시 잡힐 것"
  fi
  STEP3_OK=1
fi

# ── 4) 게이트 d(관계) — 실패해도 5)는 계속 진행, 종료 코드에는 반영 ──────
log "--- 4) 게이트 d(관계) 실행 ---"
RESULT=$(run_gate "d")
RC="${RESULT%%$'\x1e'*}"
JSON="${RESULT#*$'\x1e'}"
PASSED=$(extract_field "$JSON" passed)
NOTE=$(extract_field "$JSON" note)
log "게이트 d: exit=${RC} passed=${PASSED:-?} note=${NOTE:-<none>}"
if [[ "$RC" == "0" && "$PASSED" == "true" ]]; then
  add_summary "4. 게이트 d(관계)" "PASS" "${NOTE:-}"
  log "4) PASS"
else
  DETAIL="exit=${RC} ${NOTE:-}"
  add_summary "4. 게이트 d(관계)" "FAIL" "$DETAIL"
  log "4) FAIL — ${DETAIL}. 실패 항목:"
  extract_failed_checks "$JSON" | tee -a "$LOG_FILE"
  log "   참고: 기존 시드에 섞여 있던 위반(동성 COUPLE·나이차 초과 등)은 관계 재부여로"
  log "   저절로 고쳐지지 않을 수 있다 — docs/ai-user/60-runtime/operations.md §9 참고."
  send_telegram "⚠️ [persona-regen-finalize/${ENVIRONMENT}] 4) 게이트 d FAIL — 상세는 로그 참고(5는 계속 진행)"
  OVERALL_RC=1
fi
if [[ "$STEP3_OK" -eq 0 ]]; then
  OVERALL_RC=1
fi

# ── 5) 게이트 c(회전, 참고용) — 항상 실행, 결과가 종료 코드에 영향 없음 ──
log "--- 5) 게이트 c(회전, 참고용, days=${GATE_C_DAYS}) 실행 — 배포 게이트 아님 ---"
RESULT=$(run_gate "c" "--days ${GATE_C_DAYS}")
JSON="${RESULT#*$'\x1e'}"
NOTE=$(extract_field "$JSON" note)
log "게이트 c: ${NOTE:-<note 없음>}"
extract_failed_checks "$JSON" | tee -a "$LOG_FILE"  # 게이트 c는 항상 pass=true지만 detail은 로그에 남긴다
add_summary "5. 게이트 c(회전, 참고용)" "INFO" "7일 후 재실행해 비교할 기준선(days=${GATE_C_DAYS})"
log "5) 기록 완료 — 참고용, 지금 결과만으로는 PASS/FAIL을 매기지 않는다(7일 운영 후 재실행해 비교)"

print_summary

if [[ "$OVERALL_RC" -eq 0 ]]; then
  send_telegram "✅ [persona-regen-finalize/${ENVIRONMENT}] 마무리 완료 — 1~4단계 전부 PASS(게이트 c는 참고용 기준선만 기록)"
else
  send_telegram "⚠️ [persona-regen-finalize/${ENVIRONMENT}] 마무리 실행은 끝났지만 일부 단계 FAIL — 로그/요약 확인 필요"
fi

exit "$OVERALL_RC"
