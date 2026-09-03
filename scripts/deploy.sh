#!/usr/bin/env bash
#
# scripts/deploy.sh — dev/prod 배포 래퍼
#
# 사용법:
#   scripts/deploy.sh dev
#   scripts/deploy.sh prod --i-mean-it
#
# 배포와 검증을 한 명령으로 물리적으로 묶는다: compose 기동 → /api/health/deep
# 대기 → scripts/verify-deploy.sh <env> 자동 실행. verify-deploy.sh가 실패하면
# 이 스크립트도 exit 1로 실패한다 — "배포는 됐는데 검증을 깜빡했다"를 구조적으로
# 막기 위함이다.
#
# 왜 /api/health/deep 인가: `/api/health`는 liveness(DB 등 아무것도 안 보고
# 상수 200)라 배포 검증으로 쓸모가 없다. `/api/health/deep`은 DB `SELECT 1`을
# 실행하고 실패 시 503을 반환한다.
#
# 배경: docs/env/60-runtime/deployment.md · docs/_active/deploy-verification.md
#       AGENTS.md 절대 규칙 #4 (prod 배포는 명시 지시 시에만)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_DIR="$REPO_ROOT/env"
VERIFY_SCRIPT="$SCRIPT_DIR/verify-deploy.sh"

usage() {
  cat >&2 <<'EOF'
사용법: scripts/deploy.sh <dev|prod> [--i-mean-it]

  dev   : 즉시 진행. base + dev 스택 기동 → /api/health/deep 대기(:8090)
          → scripts/verify-deploy.sh dev 자동 실행.
          --ai-user-canary : verify 통과 후 scripts/ai-user-canary.sh 실행
            (STUB provider로 generate→publish 1사이클, LLM 미호출, dev 전용).
            예: scripts/deploy.sh dev --ai-user-canary

  prod  : 기본적으로 거부한다 (AGENTS.md 절대 규칙 #4 — prod 배포는 사용자의
          명시적 "prod에 배포해줘" 지시가 있을 때만). 진행하려면 아래 중 하나:
            - --i-mean-it 플래그를 붙인다 (사용자의 명시 지시를 이미 받은
              에이전트/스크립트가 무인 실행할 때)
            - 플래그 없이 대화형(tty)으로 실행해 "yes" 확인 프롬프트에 응답한다
          확인 후에도 base + prod 스택 기동 *전에* mariadb-prod 백업을 먼저
          수행한다 (절대 규칙 #4 순서: dev 배포·검증 → 명시 지시 → prod DB
          백업 → prod 배포). 이 스크립트는 e2e를 절대 실행하지 않는다 — e2e는
          dev(:8090) 전용이며 prod에서 돌리는 것 자체가 규칙 위반이다.
EOF
  exit 1
}

TARGET_ENV="${1:-}"
shift || true

I_MEAN_IT=""
AI_USER_CANARY=""
for arg in "$@"; do
  case "$arg" in
    --i-mean-it) I_MEAN_IT="1" ;;
    --ai-user-canary) AI_USER_CANARY="1" ;;
    *)
      echo "🚨 알 수 없는 인자: $arg" >&2
      usage
      ;;
  esac
done

case "$TARGET_ENV" in
  dev|prod) ;;
  *) usage ;;
esac

if [[ "$TARGET_ENV" == "prod" && "$AI_USER_CANARY" == "1" ]]; then
  echo "🚨 --ai-user-canary는 dev 전용이다. prod에서는 사용할 수 없다." >&2
  usage
fi

# prod 경로에서 e2e를 곁들여 돌리려는 시도를 조기 차단한다. 이 스크립트 자체는
# e2e를 절대 호출하지 않지만, RUN_E2E=1로 감싸 실행하는 실수를 막는다.
if [[ "$TARGET_ENV" == "prod" && -n "${RUN_E2E:-}" ]]; then
  echo "🚨 prod에서 e2e 실행은 금지된다 (AGENTS.md 절대 규칙 #4 — e2e는 dev:8090만). RUN_E2E를 unset 해라." >&2
  exit 1
fi

if [[ "$TARGET_ENV" == "prod" ]]; then
  if [[ -z "$I_MEAN_IT" ]]; then
    if [[ -t 0 ]]; then
      echo "⚠️  prod 배포는 사용자의 명시적 지시가 있을 때만 진행한다 (AGENTS.md 절대 규칙 #4)." >&2
      read -r -p "명시 지시를 받았음을 확인한다. 'yes'를 입력: " CONFIRM_ANSWER
      if [[ "$CONFIRM_ANSWER" != "yes" ]]; then
        echo "🚨 확인되지 않아 취소한다." >&2
        exit 1
      fi
    else
      echo "🚨 prod 배포는 명시 지시 없이 거부한다 (AGENTS.md 절대 규칙 #4)." >&2
      echo "   비대화형 실행이면 --i-mean-it 플래그가 필요하다: scripts/deploy.sh prod --i-mean-it" >&2
      exit 1
    fi
  fi
  echo "✅ prod 배포 확인됨 — 진행한다." >&2
fi

wait_for_health() {
  local port="$1" timeout="${2:-120}" waited=0
  echo "⏳ http://localhost:${port}/api/health/deep 대기 중 (timeout ${timeout}s)..." >&2
  while (( waited < timeout )); do
    if curl -sf "http://localhost:${port}/api/health/deep" >/dev/null 2>&1; then
      echo "✅ /api/health/deep OK (localhost:${port})" >&2
      return 0
    fi
    sleep 3
    waited=$((waited + 3))
  done
  echo "🚨 /api/health/deep 이 ${timeout}s 안에 200을 못 받았다 (localhost:${port}). DB 등 컴포넌트 장애 가능성." >&2
  return 1
}

cd "$ENV_DIR"

echo "▶ base 스택 기동 (공유 LLM)" >&2
docker compose up -d --build

case "$TARGET_ENV" in
  dev)
    PORT=8090
    echo "▶ dev 스택 기동" >&2
    docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build
    ;;
  prod)
    PORT=8091
    BACKUP_DIR="/home/justant/backups"
    echo "▶ prod DB 백업 (mariadb-prod) → ${BACKUP_DIR}" >&2
    mkdir -p "$BACKUP_DIR"
    docker exec againspring-mariadb-prod sh -c \
      'mariadb-dump -uroot -p"$MARIADB_ROOT_PASSWORD" --single-transaction --routines "$MARIADB_DATABASE"' \
      > "$BACKUP_DIR/prod-$(date +%Y%m%d-%H%M%S).sql"
    echo "✅ 백업 완료" >&2

    echo "▶ prod 스택 기동" >&2
    docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
    ;;
esac

wait_for_health "$PORT" "${HEALTH_TIMEOUT:-120}"

if [[ ! -f "$VERIFY_SCRIPT" ]]; then
  echo "🚨 ${VERIFY_SCRIPT} 가 아직 없다." >&2
  echo "   compose는 기동됐지만 실물 검증(scripts/verify-deploy.sh, docs/_active/deploy-verification.md 작업 #2)이" >&2
  echo "   없으면 배포를 '완료'로 볼 수 없다. verify-deploy.sh를 먼저 만들거나 다른 담당 에이전트를 기다려라." >&2
  exit 1
fi

echo "▶ scripts/verify-deploy.sh ${TARGET_ENV} 실행 (분리 불가 — 실패 시 이 배포도 실패로 처리)" >&2
bash "$VERIFY_SCRIPT" "$TARGET_ENV"

echo "✅ ${TARGET_ENV} 배포 + 검증 완료" >&2

if [[ "${AI_USER_CANARY:-}" == "1" ]]; then
  echo "▶ AI-user canary (dev, STUB, LLM 미호출)" >&2
  bash "$SCRIPT_DIR/ai-user-canary.sh"
fi
