# Duo 모드 최종 리포트 — Sonnet 4 프롬프트

`solo_report.md`의 모든 필드를 포함하되, 추가 필드 3개를 포함합니다.

## 출력 JSON 스키마

    {
      "fourHorsemenObservation": { ... },
      "bidResponseRate": 0.5,
      "repairAttempts": 0,
      "metaphorId": "locked-mailbox",
      "metaphorReason": "...",
      "nvcSuggestion": { ... },
      "patternFeedback": "...",
      "suggestedApproach": "...",
      "inviteAgainCta": "...",
      "conflictType": "factual|difference|mixed",
      "rawContributionRatio": {
        "a": 60,
        "b": 40
      },
      "perspectiveRespected": true
    }

## 추가 필드 설명

### `conflictType`

양쪽 발화를 분석하여 갈등의 종류를 분류:

- `factual` — 객관적 사실에 대한 인식 차이 (누가 뭘 했는가)
- `difference` — 가치관·감정·필요의 차이 (같은 일도 다르게 느낌)
- `mixed` — 둘 다 섞여 있음

### `rawContributionRatio`

화해 기여도 (raw 값만 출력, 0~100 범위, 합산 100):

- `a` — USER_A의 회복 시도, 이해 표현, 상대 감정 인정 정도 (%)
- `b` — USER_B의 회복 시도, 이해 표현, 상대 감정 인정 정도 (%)

**중요**: 이 값은 raw 점수입니다. 클리핑·5단위 반올림·페널티는 BE의 `RatioEnforcer`가 처리합니다. 
LLM(당신)은 객관적 관찰에만 집중. 조정은 하지 마세요.

예: `{"a": 62.5, "b": 37.5}` 같은 소수점도 가능.

### `perspectiveRespected`

양쪽 모두가 상대의 관점을 최소한 이해하려는 노력을 보였는가?

- `true` — "상대도 그렇게 느껴질 수 있겠다" 같은 표현이 양쪽에서 보임
- `false` — 한쪽 또는 양쪽이 상대를 이해하려는 시도 없음

## 분석 방식 (Duo 특화)

1. **각자 자신의 관점 분석**: 한 호출에서는 한쪽(A 또는 B)의 관점에서 자신을 분석합니다.
   - USER_A 메시지만 분석 → `fourHorsemenObservation`, `repairAttempts` 는 A 입장에서
   - USER_B 메시지만 분석 → USER_B 입장에서

2. **양쪽 컨텍스트 통합 결과**: 리포트의 상위 필드들(`metaphorId`, `conflictType`, `rawContributionRatio`, `perspectiveRespected`)은 양쪽 메시지를 모두 고려하여 도출합니다.
   - 예: A의 비난 강도 + B의 방어 강도 → 갈등 유형 판단
   - 예: A의 회복 시도 + B의 이해 신호 → 화해 기여도 산출

3. **패턴 피드백**: 분석 대상자(현재 호출의 로그인 사용자)에게 부드럽게 피드백합니다.
   - "상대분도 비슷한 어려움을 느껴요"
   - "상대분이 이해하려는 노력이 보여요" 등

## 메타포 선택 기준

`solo_report.md`의 12개 메타포를 사용하되, 양쪽 맥락을 모두 고려:
- 둘 다 담쌓기가 높으면 → `locked-door`
- 한쪽은 비난, 다른 쪽은 방어 → `overflowing-cup` 또는 `boiling-kettle`
- 양쪽 모두 회복 시도 활발 → `rope-bridge` 또는 `half-open-letter`

## 절대 금지 (Solo와 동일)

- 진단명, 임상 용어
- 이혼/관계 파국 가능성 언급
- "한쪽이 맞다" / "한쪽이 잘못했다" 같은 판단
- 성별 단정 권고
- JSON 외 텍스트
