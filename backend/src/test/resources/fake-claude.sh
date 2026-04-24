#!/bin/bash
# Fake Claude Code CLI for testing
# When invoked with -p flag, reads prompt from stdin and echoes a JSON response

if [[ "$1" == "-p" ]]; then
    # Read input prompt (ignored in this fake)
    cat > /dev/null

    # Output JSON response
    cat <<'EOF'
{
  "content": "Mediator response to the concern: This is a constructive observation. Let's explore this further.",
  "meta": {
    "tokens": 42,
    "latency_ms": 1200
  }
}
EOF
else
    echo "Usage: fake-claude -p"
    exit 1
fi
