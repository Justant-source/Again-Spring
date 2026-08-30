#!/usr/bin/env bash
# WSL → 로컬. 구현은 claude-oauth-peer.sh
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$ROOT/claude-oauth-peer.sh" pull "${AS_SSH:-justant@100.81.189.92}"
