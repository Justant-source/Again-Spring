# AI-User v2 STATE — 라이브 포인터

> 매 세션 시작 시 먼저 읽고, 끝낼 때 마지막으로 갱신.

**최종 갱신**: 2026-06-21 세션 (Phase 1 — NATEPAN 공격 크롤 실행 중)

---

## ⚠️ v2 핵심 규율 (잊지 말 것)

> - **단위 = 계정.** proxy·MAUVE 의사결정 금지(R1·R2).
> - **NATEPAN 전용.** 다른 커뮤니티 금지(R3).
> - **변수 1개/측정.** 고빈도 라운드 금지(R3).
> - **판별기 = QA 전용.** rerank OFF(R6).
> - **v1 제약 승계.** `AI_USER_ML_ENABLED=false` 영구(R7).
> - **kill criterion 사전 등록 완료** ≤60%.

---

## 현재 위치

- **Phase**: **Phase 1 — NATEPAN 공격 크롤 진행 중 (2026-06-21)**
  - [x] 크롤러 v3 재작성 (섹션 3종 + 130페이지 + author/posted_at)
  - [x] scheduler: natepan 1500/day, 나머지 11종 비활성
  - [x] crawl.py: author_id, posted_at INSERT 추가
  - [x] models.py: ALTER TABLE author_id/posted_at/index
  - [x] commit `0f47a270` + push
  - [x] ai-learning 컨테이너 재빌드 완료
  - [x] 크롤 트리거: POST /crawl/natepan?limit=1500 (진행 중)
  - [ ] 크롤 완료 확인 + before/after 스냅샷 기록

---

## Kill Criterion 현황

```
✅ 등록 완료 (2026-06-21, 오너 확정)
```
캐주얼 독자(≥3인) 계정 블라인드에서 봇 정확 식별률 ≤ **60%**

- PASS (≤60%) → NATEPAN 계정 레버 prod 출하
- FAIL (>60%) → QLoRA 데이터게이트 평가 또는 품질-피벗

---

## NATEPAN 코퍼스 현황 (스냅샷)

| 구분 | 크롤 전 (2026-06-21) | 크롤 중 | Phase 1 목표 |
|---|---|---|---|
| example_bank 전체 (natepan) | **5,316** | 진행 중 | 최대화 |
| author_id 있는 POST | 0 (신규 컬럼) | 진행 중 | 가능한 많이 |
| 작성자-그룹 타임라인 (≥3글) | 0 | 진행 중 | eval용 ≥ 20명 |

---

## 활성 결정

| 결정 | 내용 |
|---|---|
| V2-D01 | 스코프: NATEPAN·계정·캐주얼bar·QLoRA연기 |
| V2-D02 | kill criterion ≤60% 등록 |
| (v1) D-108 | ML COLLECT-only 유지 |

---

## Phase 완료 체인

| Phase | 상태 |
|---|---|
| Phase 0 | ✅ 완료 (2026-06-21) |
| Phase 1 | 🔄 크롤 진행 중 |
| Phase 2 | ⏳ 대기 |
| Phase 3 | ⏳ 대기 |
| Phase 4 | ⏳ 대기 |
| Phase 5 | ⏳ 대기 |
| Phase 6 | ⏳ 대기 |
