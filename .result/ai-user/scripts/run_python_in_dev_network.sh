#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="${REPO_ROOT:-/home/justant/Data/Again-Spring}"
DEV_NETWORK="${DEV_DOCKER_NETWORK:-againspring-dev}"
PY_IMAGE="${DEV_PY_IMAGE:-python:3.12-slim}"

if [ "$#" -lt 1 ]; then
  echo "usage: $0 <python-script> [args...]" >&2
  exit 2
fi

docker run --rm \
  --network "${DEV_NETWORK}" \
  -e LLM_AI_USER_URL="${LLM_AI_USER_URL:-http://againspring-llm-ai-user:8092}" \
  -e AI_USER_ML_BASE_URL="${AI_USER_ML_BASE_URL:-http://100.115.252.61:8201}" \
  -e AI_USER_ML_API_TOKEN="${AI_USER_ML_API_TOKEN:-aiuser-ml-api-token-dev-2026}" \
  -v "${REPO_ROOT}:${REPO_ROOT}" \
  -w "${REPO_ROOT}" \
  "${PY_IMAGE}" \
  python3 "$@"
