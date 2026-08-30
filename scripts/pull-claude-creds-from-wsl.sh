#!/usr/bin/env bash
# 로컬 ← WSL. 구현은 claude-oauth-peer.sh
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$ROOT/claude-oauth-peer.sh" pull "${WSL_SSH:-justant@100.115.252.61}"
