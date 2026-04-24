#!/bin/bash

# Claude CLI Wrapper for Testing
# Reads prompt from stdin, calls claude -p, returns JSON-structured response

set -e

TIMEOUT=${TIMEOUT:-30}
OUTPUT_FORMAT="${OUTPUT_FORMAT:-json}"

# ============================================================
# Read Input
# ============================================================

if [ -t 0 ]; then
    echo "Usage: echo 'prompt text' | ./scripts/claude-wrapper.sh"
    echo ""
    echo "Environment variables:"
    echo "  TIMEOUT (default: 30s) — timeout for claude command"
    exit 1
fi

PROMPT=$(cat)
if [ -z "$PROMPT" ]; then
    echo '{"error": "Empty prompt"}' >&2
    exit 1
fi

# ============================================================
# Call Claude
# ============================================================

START_TIME=$(date +%s)
EXIT_CODE=0
OUTPUT=""

# Use timeout command if available
if command -v timeout &> /dev/null; then
    OUTPUT=$(timeout "$TIMEOUT" claude -p "$PROMPT" 2>&1 || true)
    EXIT_CODE=$?
else
    OUTPUT=$(claude -p "$PROMPT" 2>&1 || true)
    EXIT_CODE=$?
fi

END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))

# ============================================================
# Format Response
# ============================================================

if [ "$OUTPUT_FORMAT" = "json" ]; then
    # Escape output for JSON
    ESCAPED_OUTPUT=$(echo "$OUTPUT" | jq -Rs .)

    if [ "$EXIT_CODE" -eq 0 ] && [ "$ELAPSED" -lt "$TIMEOUT" ]; then
        cat <<EOF
{
  "success": true,
  "response": $ESCAPED_OUTPUT,
  "elapsed_seconds": $ELAPSED,
  "model": "claude-code"
}
EOF
    else
        cat <<EOF
{
  "success": false,
  "error": $ESCAPED_OUTPUT,
  "exit_code": $EXIT_CODE,
  "elapsed_seconds": $ELAPSED,
  "timeout": $TIMEOUT
}
EOF
    fi
else
    # Raw output
    echo "$OUTPUT"
fi

exit "$EXIT_CODE"
