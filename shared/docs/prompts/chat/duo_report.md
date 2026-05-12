# Duo 모드 최종 리포트 — Sonnet 프롬프트

`solo_report.md`와 동일한 6개 기본 필드를 포함하되, Duo 전용 3개 필드를 추가합니다.
**JSON 형식만** 출력합니다. 순수 JSON만, 코드 블록 마커 없이.

## 분석 대상

`<conversation_history>` 전체 + `<perspective>` 태그로 지정된 사람의 시점으로 분석.

- `<perspective>A</perspective>` — USER_A의 관점에서 `nvcReflection`·`fourStageFlow`·`recommendedActions` 작성
- `<perspective>B</perspective>` — USER_B의 관점에서 작성

`coreSummary`·`metaphor`·`rawContributionRatio`·`conflictType`은 양쪽 시점을 모두 고려하여 작성.

## 출력 JSON 스키마

    {
      "coreSummary": "이 대화의 핵심 1~2 문장 (양쪽 시점 통합). 예: '집안일 분담에 대한 기대 차이가 핵심이었으며, 두 분이 서로의 피로를 인정하는 방향으로 대화가 마무리되었어요.'",

      "fourStageFlow": [
        {
          "stage": 1,
          "stageName": "감정 반영",
          "userQuote": "<perspective> 사용자의 핵심 발언 그대로 인용",
          "interpretation": "이 발언에서 보이는 것 1~2 문장"
        }
      ],

      "metaphor": {
        "id": "아래 12개 중 1개 ID",
        "displayName": "한국어 레이블",
        "reason": "이 메타포를 선택한 이유 1~2 문장"
      },

      "nvcReflection": {
        "observation": "<perspective> 사용자 발언에서 추출한 객관적 사실 (메타 설명 금지)",
        "feeling": "<perspective> 사용자가 표현한 구체적 감정",
        "need": "<perspective> 사용자 발언에서 드러난 진짜 필요",
        "request": "<perspective> 사용자가 표현할 수 있는 건설적 요청"
      },

      "recommendedActions": [
        {
          "action": "<perspective> 사용자에게 맞는 구체적 행동",
          "rationale": "왜 이 행동인지 1 문장",
          "isUserChosen": true
        }
      ],

      "externalResourceGuidance": null,

      "rawContributionRatio": {
        "a": 55,
        "b": 45
      },

      "fourHorsemenObservation": {
        "criticism": 3,
        "contempt": 1,
        "defensiveness": 4,
        "stonewalling": 2
      },

      "conflictType": "factual|difference|mixed"
    }

## 메타포 12개 (solo_report.md와 동일)

| id | displayName |
|---|---|
| `locked-mailbox` | 잠겨있는 우체통 |
| `boiling-kettle` | 끓는 주전자 |
| `locked-door` | 걸어 잠근 문 |
| `too-big-umbrella` | 너무 큰 우산 |
| `person-in-rain` | 비 맞는 사람 |
| `frozen-pond` | 얼어붙은 연못 |
| `cracked-window` | 금 간 유리창 |
| `empty-chair` | 빈 의자 |
| `overflowing-cup` | 넘치는 컵 |
| `rope-bridge` | 흔들리는 다리 |
| `half-open-letter` | 반쯤 열린 편지 |
| `two-trees-roots` | 뿌리 얽힌 두 나무 |

## Duo 추가 필드 설명

### `rawContributionRatio`

화해 기여도 raw 값 (합산 100, 소수점 가능):
- `a` — USER_A의 회복 시도·이해 표현·상대 감정 인정 정도 (%)
- `b` — USER_B의 같은 지표

**중요**: raw 점수만 출력. 클리핑·반올림·페널티는 BE의 `RatioEnforcer`가 처리.

### `fourHorsemenObservation`

<perspective> 사용자 발화에서 감지된 고트만 4기사 강도 (0~10 정수):
- 0: 없음, 1~3: 낮음, 4~6: 중간, 7~10: 높음
- 실제 발화의 빈도·강도 기반, 단순 단어 매칭 아님

### `conflictType`

양쪽 발화 분석 후 갈등 유형:
- `factual` — 객관적 사실에 대한 인식 차이
- `difference` — 가치관·감정·필요의 차이
- `mixed` — 두 종류 혼합

## 절대 금지 (solo_report.md와 동일)

1. `coreSummary`, `nvcReflection` 4항목, `metaphor` 3항목, `recommendedActions` 반드시 채울 것
2. `userQuote`는 실제 발언 그대로 인용 — 패러프레이즈 금지
3. `nvcReflection` 항목에 메타 설명 금지 — 구체 내용 필수
4. `metaphor.id`는 12개 중 정확히 하나
5. `isUserChosen: true` 최소 1개
6. JSON 외 텍스트 금지
7. 한 단락 5문장 초과 금지
