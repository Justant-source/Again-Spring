# Duo 리포트 스펙 — V12

**최종 업데이트**: 2026-05-12  
**권위본**: 이 문서

---

## 개요

Duo 리포트는 **양쪽 모두 finalize**한 세션에 생성됩니다.  
Solo 6필드 + Duo 전용 추가 필드(기여도, 4Horsemen, 갈등유형)로 구성됩니다.

---

## LLM 프롬프트

파일: `shared/docs/prompts/chat/duo_report.md`  
모델: Claude Sonnet (환경변수 `REPORT_LLM_MODEL`)

---

## 출력 JSON 스키마 (V12)

Solo 6필드와 동일 + 아래 Duo 전용 추가:

```json
{
  "coreSummary": "...",
  "fourStageFlow": [...],
  "metaphor": {...},
  "nvcReflection": {...},
  "recommendedActions": [...],
  "externalResourceGuidance": null,

  "rawContributionRatio": {
    "a": 55,
    "b": 45
  },
  "fourHorsemenObservation": {
    "criticism":     { "score": 3 },
    "contempt":      { "score": 0 },
    "defensiveness": { "score": 6 },
    "stonewalling":  { "score": 0 }
  },
  "conflictType": "factual | difference | mixed"
}
```

### Duo 전용 필드 규칙

| 필드 | 규칙 |
|---|---|
| `rawContributionRatio.a + b` | 반드시 합계 100. a, b 각각 20~80 범위 (양극단 금지) |
| `fourHorsemenObservation.*.score` | 0~10 정수. 0=없음, 1-3=낮음, 4-6=중간, 7-10=높음 |
| `conflictType` | 세 가지 중 하나 (factual/difference/mixed) |

---

## FE 렌더링

Duo 리포트는 기존 `ReportLayout.tsx`가 담당합니다. Solo 컴포넌트와 격리되어 있으며 Solo 전용 요소(SoloStageFlow, SoloReport 등)를 포함하지 않습니다.

---

## DB 컬럼

Solo 컬럼 전체 + 기존 Duo 컬럼 유지:

| 컬럼 | Solo | Duo |
|---|---|---|
| `core_summary` | ✅ | ✅ |
| `four_stage_flow` | ✅ | ✅ |
| `metaphor_*` (3개) | ✅ | ✅ |
| `nvc_observation/feeling/need/request` | ✅ | ✅ |
| `recommended_actions` | ✅ | ✅ |
| `external_resource_guidance` | 조건부 | 조건부 |
| `status` | ✅ | ✅ |
| `contribution_ratio` | — | ✅ (기존) |
| `nvc_scripts` | — | ✅ (기존, A→B / B→A) |
| `four_horsemen` | — | ✅ (기존) |
| `conflict_type` | — | ✅ (기존) |
