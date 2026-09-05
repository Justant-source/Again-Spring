#!/usr/bin/env bash
# parse_session_reset_epoch()의 최소 유닛 테스트. 네트워크/docker 호출 없음.
# 실행: bash env/scripts/lib/test-session-reset-time.sh
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./session-reset-time.sh
source "$SCRIPT_DIR/session-reset-time.sh"

FAILED=0

assert_parses() {
  local desc="$1" reason="$2"
  local epoch now
  if ! epoch=$(parse_session_reset_epoch "$reason"); then
    echo "FAIL: $desc — parse_session_reset_epoch returned non-zero for: $reason"
    FAILED=1
    return
  fi
  if ! [[ "$epoch" =~ ^[0-9]+$ ]]; then
    echo "FAIL: $desc — output is not a plain epoch integer: '$epoch'"
    FAILED=1
    return
  fi
  now=$(date +%s)
  if [[ "$epoch" -le "$now" ]]; then
    echo "FAIL: $desc — parsed epoch $epoch is not in the future (now=$now)"
    FAILED=1
    return
  fi
  # 다음 도래 시각까지 최대 24시간 이내여야 한다(파싱이 엉뚱한 미래 날짜를 짚으면 버그).
  if [[ $((epoch - now)) -gt 86400 ]]; then
    echo "FAIL: $desc — parsed epoch is more than 24h away ($(( (epoch - now) / 60 ))분 후): $reason"
    FAILED=1
    return
  fi
  echo "PASS: $desc — resets at $(date -d "@$epoch" '+%F %T %Z') (in $(( (epoch - now) / 60 ))분)"
}

assert_fails() {
  local desc="$1" reason="$2"
  if parse_session_reset_epoch "$reason" >/dev/null 2>&1; then
    echo "FAIL: $desc — expected parse failure but got a match for: $reason"
    FAILED=1
    return
  fi
  echo "PASS: $desc — correctly failed to parse"
}

# 실제 시그니처 예시 1: UTC, 분 없음
assert_parses "UTC 예시 (분 없음)" \
  "LLM_ERROR_SIGNATURE: You've hit your session limit · resets 11am (UTC)"

# 실제 시그니처 예시 2: IANA 타임존, 분 없음
assert_parses "Asia/Seoul 예시 (분 없음)" \
  "LLM_ERROR_SIGNATURE: You've hit your session limit · resets 8pm (Asia/Seoul)"

# 분이 포함된 변형도 다뤄야 한다(관측된 문구가 향후 "11:30am" 식으로 바뀔 가능성 대비)
assert_parses "분 포함 변형" \
  "LLM_ERROR_SIGNATURE: You've hit your session limit · resets 11:30pm (UTC)"

# 자정 경계값(12am/12pm)도 올바르게 0시/12시로 변환되는지 확인
assert_parses "자정(12am) 경계값" \
  "LLM_ERROR_SIGNATURE: You've hit your session limit · resets 12am (UTC)"
assert_parses "정오(12pm) 경계값" \
  "LLM_ERROR_SIGNATURE: You've hit your session limit · resets 12pm (UTC)"

# 파싱 실패 케이스: 리셋 시각 문구가 아예 없는 오류 텍스트 (예: CONSECUTIVE_FAILURES, 다른 오류)
assert_fails "리셋 문구 없음" \
  "CONSECUTIVE_FAILURES(5)"
assert_fails "완전히 무관한 텍스트" \
  "LLM_ERROR_SIGNATURE: internal server error, please try again later"

echo "---"
if [[ "$FAILED" -eq 0 ]]; then
  echo "ALL PASS"
  exit 0
else
  echo "SOME TESTS FAILED"
  exit 1
fi
