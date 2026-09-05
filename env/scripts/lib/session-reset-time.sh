#!/usr/bin/env bash
# 세션 한도 오류 문구에서 리셋 시각을 파싱하는 함수 — persona-diversity-v4 재개 자동화용
# (env/scripts/resume-persona-profile-regen.sh).
#
# 대상 문구 예시(LlmErrorSignatures 시그니처에 걸리는 실제 오류 텍스트, docs/shared/policies/
# llm-error-signatures.json 참고):
#   "You've hit your session limit · resets 11am (UTC)"
#   "You've hit your session limit · resets 8pm (Asia/Seoul)"
#
# 이 파일은 독립적으로 source 가능해야 한다(테스트: env/scripts/lib/test-session-reset-time.sh).
# docker/네트워크 호출을 하지 않는다 — 순수 문자열 파싱 + `date` 계산만 한다.

# parse_session_reset_epoch REASON
#   REASON 문자열에서 "resets <H>[:MM](am|pm) (<TZ>)" 패턴을 찾아, 그 다음 도래 시각의
#   UNIX epoch 초를 stdout에 출력하고 0을 반환한다. 이미 오늘 그 시각이 지났으면 내일로 넘긴다.
#   패턴을 찾지 못하거나 `date`가 그 TZ/시각을 해석하지 못하면 아무것도 출력하지 않고 1을 반환한다
#   (호출자가 고정 간격 폴백으로 넘어가야 한다는 신호).
parse_session_reset_epoch() {
  local reason="${1:-}"
  local re='resets[[:space:]]+([0-9]{1,2})(:([0-9]{2}))?[[:space:]]*([AaPp][Mm])[[:space:]]*\(([^)]+)\)'
  local hour min ampm tz
  if [[ "$reason" =~ $re ]]; then
    hour="${BASH_REMATCH[1]}"
    min="${BASH_REMATCH[3]:-00}"
    ampm="${BASH_REMATCH[4],,}"
    tz="${BASH_REMATCH[5]}"
  else
    return 1
  fi

  # 12시간제 → 24시간제. 10#$x로 8진수 오인(08, 09) 방지.
  hour=$((10#$hour))
  min=$((10#$min))
  if [[ "$ampm" == "pm" && "$hour" -ne 12 ]]; then
    hour=$((hour + 12))
  elif [[ "$ampm" == "am" && "$hour" -eq 12 ]]; then
    hour=0
  fi
  if [[ "$hour" -lt 0 || "$hour" -gt 23 || "$min" -lt 0 || "$min" -gt 59 ]]; then
    return 1
  fi
  local hhmm
  hhmm=$(printf '%02d:%02d' "$hour" "$min")

  local target_epoch now_epoch
  target_epoch=$(TZ="$tz" date -d "today ${hhmm}" +%s 2>/dev/null) || return 1
  now_epoch=$(date +%s)
  if [[ "$target_epoch" -le "$now_epoch" ]]; then
    target_epoch=$(TZ="$tz" date -d "tomorrow ${hhmm}" +%s 2>/dev/null) || return 1
  fi
  printf '%s\n' "$target_epoch"
  return 0
}
