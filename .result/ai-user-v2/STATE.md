# AI-User v2 STATE — 라이브 포인터

> 매 세션 시작 시 먼저 읽고, 끝낼 때 마지막으로 갱신.

**최종 갱신**: 2026-06-21 (Phase 1~5 진행 중 / Phase 3·4·5 코드 완료)

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

### Phase 1 — NATEPAN 공격 크롤 🔄
  - [x] 크롤러 v5 병렬 재작성 (asyncio.Semaphore(8), 20초 내 9섹션 완료)
  - [x] author_id / posted_at 컬럼 추가
  - [x] v5 병렬 크롤 1차 실행: ID 범위 600/1401 (43%) → llm-ai-user 재시작으로 중단
  - [ ] v5 크롤 재트리거 (중단분 재개) → Phase 1 gate 체크

### Phase 2 — eval 하니스 🔄
  - [x] 블라인드 키트 v1 작성 완료 (2026-06-21) → `.result/ai-user-v2/eval/blind_kit_v1.md`
  - [x] 정답키 분리 → `blind_kit_v1_key.md`
  - [x] 18개 포스트 (AI 9개·인간 9개, 짝수=AI, 홀수=인간)
  - [x] AI 출처: ai-user-043(WORK 5개) + ai-user-061(WORK 3개) + ai-user-110(MARRIED 2개 → 포함 4개)
  - [x] 인간 출처: NATEPAN 실제 갈등·관계 사연 (id: 15165·15173·15153·1889·10188·10190·11305·1151·5661)
  - [x] Named-tell 집계 양식 포함 (Phase 5 연계)
  - [ ] 실제 평가 실시 — 사용자 참여 필요 (≥3인, 자신 포함)

### Phase 3 — 계정 메모리/Trajectory ✅ (commit 491e4515)
  - [x] `life_state.json` 파일 기반 (historyDir/{profile}/)
  - [x] CASUAL 결정: i.i.d. 25% → 스트릭 기반
  - [x] ongoingSituation 주입 (saga 이어가기)
  - [x] situationContinuityBlock() PromptAssembler
  - [x] ai-user-orchestrator dev 재배포 완료

### Phase 4 — Cadence & 상호작용 ✅ (commit a42fba61)
  - [x] BehaviorEngine: REPLY → scheduleReplyWithDelay(5~60분) 배선
  - [x] InteractionScanner: MAX_REPLIES_PER_COMMENT 2→4
  - [x] PersonaSelector: circadian 가중치 기반 쿨다운
  - [x] ai-user-orchestrator dev 재배포 완료

### Phase 5 — named-tell 제거 루프 🔄 (부분 완료)
  - [x] SELF_CRITIQUE_EXTRA_CLICHES 확장 (.env.dev, 비gitignore):
    - 말미 한탄 종결: 인지/건지 모르겠음·모르겠다
    - 사건 해상도 낮은 종결: 어떻게 해야 할지 모르겠
    - AI 투 개구부: 솔직히 말해서·말하면
    - AI 마무리 질문: 여러분은 어떻게 생각하시나요
    - 감정 나열: 어이없어·어이가 없네
  - [x] llm-ai-user 재시작 (새 클리셰 적용)
  - [ ] 계정 블라인드 1회 eval (사용자 참여 필요)

### Phase 6 — 결정 게이트 ⏳

---

## Kill Criterion 현황

```
✅ 등록 완료 (2026-06-21)
```
캐주얼 독자(≥3인) 계정 블라인드 봇 식별률 ≤ **60%** = PASS

---

## NATEPAN 코퍼스 현황

| 구분 | 크롤 전 | v5 후 (예상) |
|---|---|---|
| example_bank (natepan) | 5,316 | ~7,000+ |
| author_id 있는 POST | 0 | 150+ (진행 중) |

---

## 활성 결정

| 결정 | 내용 |
|---|---|
| V2-D01 | NATEPAN·계정·캐주얼bar·QLoRA연기 |
| V2-D02 | kill criterion ≤60% |
| (v1) D-108 | ML COLLECT-only |

---

## Phase 완료 체인

| Phase | 상태 |
|---|---|
| Phase 0 | ✅ 2026-06-21 |
| Phase 1 | 🔄 크롤 v5 43% → 완료 후 gate |
| Phase 2 | 🔄 키트 완료, 평가 대기 |
| Phase 3 | ✅ 2026-06-21 commit 491e4515 |
| Phase 4 | ✅ 2026-06-21 commit a42fba61 |
| Phase 5 | 🔄 클리셰 추가 완료, eval 대기 |
| Phase 6 | ⏳ |
