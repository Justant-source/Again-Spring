# Solo 리포트 스펙 — V12

**최종 업데이트**: 2026-05-12  
**권위본**: 이 문서

---

## 개요

Solo 리포트는 **한 명이 finalize**한 세션에 생성됩니다.  
Duo(양쪽 참여) 리포트와 달리, 상대방 데이터 없이 본인 대화만으로 완성된 정리를 제공합니다.

---

## LLM 프롬프트

파일: `shared/docs/prompts/chat/solo_report.md`  
모델: Claude Sonnet (환경변수 `REPORT_LLM_MODEL`, 기본 `claude-sonnet-4-6`)

---

## 출력 JSON 스키마 (V12)

```json
{
  "coreSummary": "이 대화의 핵심 1~2 문장",
  "fourStageFlow": [
    {
      "stage": 1,
      "stageName": "감정 반영",
      "userQuote": "사용자 발언 그대로 인용",
      "interpretation": "다시봄이 이 발언에서 본 것"
    }
  ],
  "metaphor": {
    "id": "12종 ID 중 하나",
    "displayName": "한국어 레이블",
    "reason": "이 메타포를 선택한 이유"
  },
  "nvcReflection": {
    "observation": "객관적 사실",
    "feeling": "구체적 감정",
    "need": "진짜 필요",
    "request": "건설적 요청"
  },
  "recommendedActions": [
    {
      "action": "구체적 행동",
      "rationale": "왜 이 행동인지",
      "isUserChosen": true
    }
  ],
  "externalResourceGuidance": null
}
```

### 필드 규칙

| 필드 | 필수 | 규칙 |
|---|---|---|
| `coreSummary` | ✅ | null·빈 문자열 금지 |
| `fourStageFlow` | ✅ | 최소 1개, 최대 4개. 실제 대화 근거 있을 때만. |
| `fourStageFlow[].userQuote` | ✅ | 패러프레이즈 금지, 사용자 발언 그대로 |
| `metaphor.id` | ✅ | 아래 12종 중 정확히 하나 |
| `nvcReflection` 4항목 | ✅ | 메타 설명 금지, 구체 내용 필수 |
| `recommendedActions` | ✅ | 1~3개, `isUserChosen: true` 최소 1개 |
| `externalResourceGuidance` | 조건부 | 위기/법적/의료/재정 영역 시 객체, 아니면 null |

---

## 메타포 12종

| id | 한국어 레이블 |
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

FE 권위본: `frontend/lib/constants/metaphors.ts`

---

## FE 렌더링

### 컴포넌트 계층

```
SoloReport.tsx (메인 컨테이너)
├── coreSummary 카드
├── SoloStageFlowSection (4단계 흐름)
├── 메타포 카드 (Image + label + reason)
├── NVC 4항목 (관찰·느낌·욕구·부탁)
├── 추천 행동 체크리스트
├── ExternalResourceGuidance (조건부)
└── "다시 정리하기" 버튼
```

라우트: `app/session/result/[id]/page.tsx` (report.isSoloMode=true 분기)  
서브라우트: `app/session/result/[id]/solo/page.tsx`

### 화면에 절대 없어야 할 요소

- "초대 링크 다시 보내기" 또는 유사한 초대 CTA
- "두 분이 함께 해야" 등 Duo 유도 문구
- "완전한 리포트는 상대가 참여하면" 문구
- 욕구 차이 지도 패널 (Solo에서는 positionB 없음)

---

## 에러 처리

| 상태 | 처리 |
|---|---|
| `status=FAILED` | `SoloFailedState.tsx` 렌더 — "채팅으로 돌아가기" 버튼만 |
| 리포트 404 (생성 중) | FE 폴링 (5초 간격, 최대 3분) |

---

## DB 컬럼 (V23 마이그레이션 추가)

| 컬럼 | 타입 | Solo 사용 |
|---|---|---|
| `core_summary` | LONGTEXT | ✅ |
| `four_stage_flow` | JSON | ✅ |
| `metaphor_id` | VARCHAR(100) | ✅ |
| `metaphor_display_name` | VARCHAR(100) | ✅ |
| `metaphor_reason` | LONGTEXT | ✅ |
| `nvc_observation` | LONGTEXT | ✅ |
| `nvc_feeling` | LONGTEXT | ✅ |
| `nvc_need` | LONGTEXT | ✅ |
| `nvc_request` | LONGTEXT | ✅ |
| `recommended_actions` | JSON | ✅ |
| `external_resource_guidance` | JSON | 조건부 |
| `status` | ENUM(GENERATING,OK,FAILED) | ✅ |
