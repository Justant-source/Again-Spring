#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

MODE="up"
STACKS=()

usage() {
  cat <<'EOF'
Usage:
  bash ./rebuild-stacks.sh [stack...]
  bash ./rebuild-stacks.sh --build-only [stack...]

Stacks:
  base
  dev
  prod
  ai-user
  all

Examples:
  bash ./rebuild-stacks.sh ai-user
  bash ./rebuild-stacks.sh --build-only ai-user
  bash ./rebuild-stacks.sh base dev prod ai-user

Notes:
  - Default mode is `up -d --build`.
  - Default stack is `ai-user`.
  - `--build-only` falls back to `*.example` env files when real env files are absent.
EOF
}

log() {
  printf '[rebuild] %s\n' "$*"
}

die() {
  printf '[rebuild] ERROR: %s\n' "$*" >&2
  exit 1
}

require_docker() {
  if ! docker version >/dev/null 2>&1; then
    cat >&2 <<'EOF'
[rebuild] ERROR: Docker daemon에 접근할 수 없습니다.
[rebuild] - 현재 셸에서 `docker version`이 실패했습니다.
[rebuild] - snap confinement 또는 /var/run/docker.sock 접근 제한일 가능성이 큽니다.
[rebuild] - 권한이 열린 일반 호스트 셸에서 같은 스크립트를 실행하세요.
EOF
    exit 1
  fi
}

select_env_file() {
  local real_file="$1"
  local example_file="$2"

  if [[ -f "$real_file" ]]; then
    printf '%s\n' "$real_file"
    return 0
  fi

  if [[ "$MODE" == "build" && -f "$example_file" ]]; then
    printf '%s\n' "$example_file"
    return 0
  fi

  die "필수 env 파일이 없습니다: $real_file"
}

run_compose() {
  local stack="$1"
  local compose_file="$2"
  local env_file="${3:-}"

  local -a cmd=(docker compose -f "$compose_file")
  if [[ -n "$env_file" ]]; then
    cmd+=(--env-file "$env_file")
  fi

  if [[ "$MODE" == "build" ]]; then
    cmd+=(build)
  else
    cmd+=(up -d --build)
  fi

  log "running: ${cmd[*]}"
  (
    cd "$SCRIPT_DIR"
    "${cmd[@]}"
  )
}

expand_stacks() {
  local item
  for item in "$@"; do
    case "$item" in
      all)
        STACKS+=(base dev prod ai-user)
        ;;
      base|dev|prod|ai-user)
        STACKS+=("$item")
        ;;
      *)
        die "지원하지 않는 스택입니다: $item"
        ;;
    esac
  done
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --build-only)
      MODE="build"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      expand_stacks "$1"
      shift
      ;;
  esac
done

if [[ ${#STACKS[@]} -eq 0 ]]; then
  STACKS=("ai-user")
fi

require_docker

for stack in "${STACKS[@]}"; do
  case "$stack" in
    base)
      run_compose "base" "$SCRIPT_DIR/docker-compose.yml"
      ;;
    dev)
      run_compose \
        "dev" \
        "$SCRIPT_DIR/docker-compose.dev.yml" \
        "$(select_env_file "$SCRIPT_DIR/.env.dev" "$SCRIPT_DIR/.env.dev.example")"
      ;;
    prod)
      run_compose \
        "prod" \
        "$SCRIPT_DIR/docker-compose.prod.yml" \
        "$(select_env_file "$SCRIPT_DIR/.env.prod" "$SCRIPT_DIR/.env.prod.example")"
      ;;
    ai-user)
      run_compose \
        "ai-user" \
        "$SCRIPT_DIR/docker-compose.ai-user.yml" \
        "$(select_env_file "$SCRIPT_DIR/.env.ai-user" "$SCRIPT_DIR/.env.ai-user.example")"
      ;;
  esac
done
