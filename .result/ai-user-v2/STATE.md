# AI-User v2 STATE — 라이브 포인터

> 매 세션 시작 시 먼저 읽고, 끝낼 때 마지막으로 갱신.

**최종 갱신**: 2026-06-21 (Phase 1 크롤 v5 병렬 진행 중 / Phase 3 완료)

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

- **Phase**: **Phase 1 완료 + Phase 3 완료 (2026-06-21)**

### Phase 1 — NATEPAN 공격 크롤 ✅
  - [x] 크롤러 v5 병렬 재작성 (asyncio.Semaphore(8) + to_thread)
  - [x] scheduler: natepan 1500/day 전용
  - [x] author_id / posted_at 컬럼 추가
  - [x] v5 병렬 크롤 실행 중 (정적 9섹션 20초 완료 → ID 범위 1401건 목표)
  - [ ] 크롤 완료 후 Phase 1 gate 확인

### Phase 3 — 계정 메모리/Trajectory ✅
  - [x] `life_state.json` 파일 기반 저장 (historyDir/{profile}/)
  - [x] CASUAL 결정: i.i.d. 25% → 스트릭 기반 (2연속 CASUAL → 10%)
  - [x] `ongoingSituation` 저장·주입 (갈등 글 첫 문장 → saga 이어가기)
  - [x] `situationContinuityBlock()` PromptAssembler 추가
  - [x] BUILD SUCCESSFUL (commit 491e4515)
  - [ ] dev 배포 (컨테이너 재빌드)

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

| 구분 | 크롤 전 (2026-06-21) | v5 크롤 후 | Phase 1 목표 |
|---|---|---|---|
| example_bank 전체 (natepan) | 5,316 | 진행 중 (+1,500 목표) | 최대화 |
| author_id 있는 POST | 0 | 150+ (v4에서 확인) | 가능한 많이 |
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
| Phase 1 | 🔄 크롤 v5 진행 중 → gate 확인 후 완료 |
| Phase 2 | ⏳ eval 하니스 준비 필요 (사용자 참여 필요) |
| Phase 3 | ✅ 완료 (2026-06-21, commit 491e4515) |
| Phase 4 | ⏳ 대기 (cadence/대댓글) |
| Phase 5 | ⏳ 대기 (named-tell 제거) |
| Phase 6 | ⏳ 대기 (결정 게이트) |
