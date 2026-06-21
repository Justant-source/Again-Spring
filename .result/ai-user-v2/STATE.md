# AI-User v2 STATE — 라이브 포인터

> 매 세션 시작 시 먼저 읽고, 끝낼 때 마지막으로 갱신.

**최종 갱신**: 2026-06-21 세션 (Phase 0 — 창립 문서 생성)

---

## ⚠️ v2 핵심 규율 (잊지 말 것)

> - **단위 = 계정.** proxy·MAUVE 의사결정 금지(R1·R2).
> - **NATEPAN 전용.** 다른 커뮤니티 금지(R3).
> - **변수 1개/측정.** 고빈도 라운드 금지(R3).
> - **판별기 = QA 전용.** rerank OFF(R6).
> - **v1 제약 승계.** `AI_USER_ML_ENABLED=false` 영구(R7).
> - **kill criterion 사전 등록.** 미등록 상태에서 Phase 2 eval 시작 금지.

---

## 현재 위치

- **Phase**: **Phase 0 — 창립 진행 중 (2026-06-21)**
  - [x] V2-D01 스코프 확정 (`decisions.md`)
  - [x] DIAGNOSIS.md 창립 문서 작성
  - [x] README.md charter 작성
  - [x] decisions.md 초기화
  - [x] STATE.md 초기화
  - [ ] roadmap.md 작성
  - [ ] **kill criterion 오너 사전 등록** ← 🔴 Phase 0 완료 블로커
  - [ ] `lint:docs` 통과 + commit

---

## Kill Criterion 현황

```
🔴 미등록 — 오너 확정 필요
```
캐주얼 독자(≥3인) 계정 블라인드에서 봇 정확 식별률 ≤ **__%**

---

## NATEPAN 코퍼스 현황 (스냅샷)

| 구분 | v2 시작 시점 (2026-06-21) | Phase 1 목표 |
|---|---|---|
| clean human (example_bank) | **~2,589** | 최대화 (목표 TBD) |
| 작성자-그룹 타임라인 | 0 (미보존) | eval용 ≥ M개 |
| ML discriminator n_human | ~2,589 | 코퍼스와 동기 |

---

## 활성 결정

| 결정 | 내용 |
|---|---|
| V2-D01 | 스코프: NATEPAN·계정·캐주얼bar·QLoRA연기 |
| (v1) D-108 | ML COLLECT-only 유지 |
| (v1) D-107 | v1 출하 레버 승계 |

---

## Phase 완료 체인

| Phase | 상태 |
|---|---|
| Phase 0 | 🔄 진행 중 (kill criterion 미등록) |
| Phase 1 | ⏳ 대기 |
| Phase 2 | ⏳ 대기 |
| Phase 3 | ⏳ 대기 |
| Phase 4 | ⏳ 대기 |
| Phase 5 | ⏳ 대기 |
| Phase 6 | ⏳ 대기 |
