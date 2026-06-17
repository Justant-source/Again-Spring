# M5 블라인드 테스트 결과 — NATEPAN + THEQOO

## 개요

| 항목 | 값 |
|---|---|
| 테스트 날짜 | 2026-06-16 |
| 총 쌍 수 | 40 (NATEPAN 20 + THEQOO 20) |
| **전체 정확도** | **82.5% (33/40)** |
| cond5 목표 | ≤60% |
| **cond5 판정** | **❌ FAIL** |

## 커뮤니티별 결과

| 커뮤니티 | 정답 | 총 수 | 정확도 |
|---|---|---|---|
| NATEPAN | 16 | 20 | 80% |
| THEQOO | 17 | 20 | 85% |

## 오류 분석

### Human→AI 오분류 (5건)
- N007, N001, N004, N006: NATEPAN human이 AI로 오판 (구조적·상담조 human 텍스트)
- T005: THEQOO human이 AI로 오판

### AI→Human 오분류 (2건) — M7 신호
- **T013, T017**: THEQOO AI가 human으로 오판 → **features(오타·길이 변동) 적용 효과 확인**
  - 이 2건이 "messiness가 cond5를 낮춘다"는 근거 (M7 signal items)

## 시사점

- AI 탐지율 82.5% → cond5 FAIL (목표 ≤60% 대비 +22.5pp)
- T013/T017이 AI→Human 오분류 → writing_quirks features가 실제로 작동한 증거
- 탐지 주요 단서: 상담조 도입부, 균일 길이, 오타 0, 균형 구조
- R9 방향: 오타 주입 + 주제 다양화 + 길이 변동

## 관련 단계
- Step 35~38 (세션 16~17) — M7 features 적용 후 T013/T017 오분류 확인
- R5 블라인드 (2026-06-17) → `r5-blind-results.md`
