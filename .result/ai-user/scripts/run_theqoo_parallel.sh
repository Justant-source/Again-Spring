#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_PATH="$ROOT_DIR/scripts/crawl_theqoo.py"
LOG_DIR="${THEQOO_LOG_DIR:-$ROOT_DIR/logs/theqoo-crawl}"
WORKERS="${THEQOO_WORKERS:-8}"
PAGES_PER_SHARD="${THEQOO_PAGES_PER_SHARD:-4}"
START_PAGE="${THEQOO_START_PAGE:-1}"
BOARDS_RAW="${THEQOO_BOARDS:-square hot ktalk beauty}"
DRY_RUN="${THEQOO_DRY_RUN:-0}"

mkdir -p "$LOG_DIR"

if [[ ! -f "$SCRIPT_PATH" ]]; then
  echo "missing script: $SCRIPT_PATH" >&2
  exit 1
fi

mapfile -t BOARDS < <(printf '%s\n' "$BOARDS_RAW" | xargs -n1)

declare -a PIDS=()
declare -a SHARDS=()
worker_index=0
round_index=0

while (( worker_index < WORKERS )); do
  progress=0
  for board in "${BOARDS[@]}"; do
    if (( worker_index >= WORKERS )); then
      break
    fi
    shard_start=$((START_PAGE + round_index * PAGES_PER_SHARD))
    shard_name="$(printf 'w%02d-%s-p%03d' "$worker_index" "$board" "$shard_start")"
    log_path="$LOG_DIR/$shard_name.log"

    cmd=(
      python3 "$SCRIPT_PATH"
      --boards "$board"
      --page-start "$shard_start"
      --pages "$PAGES_PER_SHARD"
    )
    if [[ "$DRY_RUN" == "1" ]]; then
      cmd+=(--dry-run)
    fi

    (
      echo "[$(date -Is)] start $shard_name"
      printf '[cmd] %q ' "${cmd[@]}"
      printf '\n'
      "${cmd[@]}"
      echo "[$(date -Is)] done $shard_name"
    ) >"$log_path" 2>&1 &

    PIDS+=("$!")
    SHARDS+=("$shard_name")
    worker_index=$((worker_index + 1))
    progress=1
  done
  if (( progress == 0 )); then
    break
  fi
  round_index=$((round_index + 1))
done

if (( ${#PIDS[@]} == 0 )); then
  echo "no shards scheduled" >&2
  exit 1
fi

failures=0
for idx in "${!PIDS[@]}"; do
  pid="${PIDS[$idx]}"
  shard="${SHARDS[$idx]}"
  if wait "$pid"; then
    echo "OK   $shard"
  else
    echo "FAIL $shard ($LOG_DIR/$shard.log)" >&2
    failures=$((failures + 1))
  fi
done

echo "logs: $LOG_DIR"
if (( failures > 0 )); then
  exit 1
fi
