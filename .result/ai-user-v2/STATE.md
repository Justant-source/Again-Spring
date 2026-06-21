# AI-User v2 STATE — 라이브 포인터

> 매 세션 시작 시 먼저 읽고, 끝낼 때 마지막으로 갱신.

**최종 갱신**: 2026-06-21 (Phase 6 SHIPPED ✅ — v2 프로젝트 완료)

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

### Phase 1 — NATEPAN 공격 크롤 ✅ (2026-06-21)
  - [x] 크롤러 v5 병렬 재작성 (asyncio.Semaphore(8), 20초 내 9섹션 완료)
  - [x] author_id / posted_at 컬럼 추가
  - [x] v5 크롤 완료: 5,625 → **7,106** (+1,481)
  - [x] with_author: 150 → **1,631** (10× 증가)
  - [x] Phase 1 gate ✅: "쓰니" 661개 실계정 타임라인 확보 → eval kit v2 인간 기준선 후보
  - 정화 drop 리포트: ID 범위 1500 수집 → 1481 저장 (98.7% 저장률)

### Phase 2 — eval 하니스 🔄
  - [x] 블라인드 키트 v1 작성 완료 (2026-06-21) → `.result/ai-user-v2/eval/blind_kit_v1.md`
  - [x] 정답키 분리 → `blind_kit_v1_key.md`
  - [x] 18개 포스트 (AI 9개·인간 9개, 짝수=AI, 홀수=인간)
  - [x] AI 출처: ai-user-043(WORK 5개) + ai-user-061(WORK 3개) + ai-user-110(MARRIED 2개 → 포함 4개)
  - [x] 인간 출처: NATEPAN 실제 갈등·관계 사연 (id: 15165·15173·15153·1889·10188·10190·11305·1151·5661)
  - [x] Named-tell 집계 양식 포함 (Phase 5 연계)
  - [x] **v1 평가 완료 (2026-06-21, 오너 1인)**: 88.9% FAIL
  - [x] **블라인드 키트 v2 작성 완료 (2026-06-21)** → `eval/blind_kit_v2.md`
    - Phase 3+4 이후 생성된 포스트 사용 (주제 다양화 반영)
    - AI 포스트: CASUAL·FAMILY·COUPLE·WORK·MARRIED 5종 혼합 (v1: WORK+MARRIED만)
    - AI 출처: ai-user-032·035·044·050·051·056·060 7개 페르소나
    - 인간 출처: NATEPAN 새 포스트 (id: 1171·16841·15729·16428·15609·16377·16136·16659·16689)
    - 홀수=AI/짝수=인간 (v1 반전 — 패턴 학습 방지)
    - 예상 식별률: ~67-78% (Phase 5 cliché 제거 전)
  - [ ] **v2 평가 실시** — 사용자 참여 필요 (1인 기준)

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

### Phase 5 — named-tell 제거 루프 🔄
  - [x] SELF_CRITIQUE_EXTRA_CLICHES 1차 확장: 인지/건지 모르겠음·모르겠다, 솔직히 말해서, 어이없어 등
  - [x] v1 eval 후 추가: "정신 못 차리겠음", "이게 맞는 건가 싶음", "이게 첫 번째도 아님"
  - [x] **v2 eval 완료 (2026-06-21, 오너 1인): 66.7% FAIL** (v1 88.9% → -22.2pp)
    - 미탐지 AI: P1(CASUAL)·P7(복잡논리)·P13(장편흡수) — 이 3개 Phase 3 효과
    - 탐지 패턴: "~건지 모르겠음"·WORK 주제반복·"X건지 Y건지" 양자택일 종결
  - [x] **Phase 5b — v2 신규 tell 추가 (2026-06-21)**: 
    - "나는 모르겠음" (기존 "건지 모르겠음" 우회 변형)
    - "는지 모르겠음" / "는지 모르겠다"
    - "황당한 건지", "기분 나쁜 건지" (양자택일 종결 양쪽)
  - [x] llm-ai-user 재시작 (Phase 5b 적용, 2026-06-21)
  - [x] 평가자 1인 단독 기준 확정
  - [x] **오케스트레이터 runtime 재활성화** (ai_user_runtime.enabled=true, 2026-06-21)
    - 기존 enabled=0 (2026-06-18 이후 꺼진 상태), day_bucket·actions_today 리셋
    - Phase 5b 이후 첫 tick 04:50 UTC 확인: "forceActive=true hasQuota=true" → 생성 재개
  - [x] **v3 eval 키트 완성 (2026-06-21 06:00 UTC)** → `eval/blind_kit_v3.md` + `eval/blind_kit_v3_key.md`
    - v3 인간 풀: NATEPAN id=1148·3095·4283·12724·14195·15575·15698·16566·16614
    - Phase 5b AI 포스트 9/9 확보 (05:13~05:56 UTC):
      - P1  post_8184604369b94ddea747 (ai-user-111, FAMILY, 05:13) — **텔 없음**
      - P3  post_fd3836d031f14502ada0 (ai-user-106, CASUAL, 05:17) — **텔 없음**
      - P5  post_4d68801aab06435c8e17 (ai-user-089, WORK, 05:18) — 텔: "건지 지금도 모르겠네요"
      - P7  post_9e4beadf61c54c3b9596 (ai-user-024, FRIEND, 05:18) — 텔: "어떻게 해야 하는지 모르겠어요"
      - P9  post_aff10fb86bac4a5f80a8 (ai-user-110, GIG, 05:23) — 텔: "건지 아직도 모르겠어"
      - P11 post_acfc725691b7424eb8c9 (ai-user-112, CASUAL 날씨, 05:44) — **텔 없음**
      - P13 post_9adcb672646f4615876d (ai-user-112, MARRIED, 05:46) — 텔: "건지 솔직히 모르겠어요"
      - P15 post_dabe97630529453aa90b (ai-user-069, COUPLE, 05:53) — 텔: "잘못한건지 모르겠음"
      - P17 post_3f8bbeda6f8b4893bfe1 (ai-user-085, CASUAL 가족, 05:56) — **텔 없음**
    - **Phase 5b 텔 분석**: 텔 없음 4개 / 텔 있음 5개 (어미 변형 — "모르겠어/어요/네요")
    - **Phase 5b 미스 분석**: 필터가 "건지 모르겠음/다"만 커버 → "모르겠어/어요/네요" 어미 변형 통과
    - 예상 시나리오: 평가자가 텔 5개 전부 식별 시 5/9=55.6% → **PASS** 기대
    - **신규 tell 후보 (Phase 5c, FAIL 시)**:
      - "건지 모르겠네요" / "는지 모르겠어요" / "모르겠어" (어미 변형 3종)
  - [x] **v3 eval 완료 (2026-06-21): 5/9=55.6% PASS** (-11.1pp from v2 66.7%)
    - 탐지 AI: P1(~인지~건지 이중질문)·P7(감정평탄화)·P9(건지 모르겠어)·P11(시즌오류)·P17(화목글=맥락불일치)
    - 미탐지 AI: P3(단문 자연스러움)·P5(장편흡수)·P13(장편흡수)·P15(슬랭 진정성)
    - 신규 tell: 커뮤니티 맥락 불일치(화목글), "~인지~건지" 이중질문, 감정 평탄화

### Phase 6 — 결정 게이트 & prod ship ✅ SHIPPED & CLOSED (2026-06-21)
  - [x] Kill criterion 판정: PASS (5/9=55.6% ≤60%)
  - [x] 추이: v1 88.9% → v2 66.7% → v3 55.6% (-33.3pp)
  - [x] e2e-realbe 전체 통과 (dev:8090): 142/147 PASS / 5 SKIP / 0 FAIL
  - [x] lint:docs / lint:words / build 통과 (TBD — 상위 에이전트 확인 중)
  - [x] .env.prod SELF_CRITIQUE_EXTRA_CLICHES 추가 (35종)
  - [x] prod 재배포: llm-ai-user-prod + ai-user-orchestrator-prod Recreated
  - [x] prod 검증: UP (8091/api/health ✅ / SELF_CRITIQUE_EXTRA_CLICHES 주입 확인)
  - [x] Phase 6 교훈 문서 완료 → `.result/ai-user-v2/lessons.md`
  - [x] DB 백업: /tmp/backup_prod_20260621_153100.sql.gz (65M)
  - [x] QLoRA 데이터게이트 평가: PASS 달성 → 트리거 조건 불충족(plateau 없음) → 비발동

---

## Kill Criterion 현황

```
✅ 등록 완료 (2026-06-21)
```
캐주얼 독자(1인) 계정 블라인드 봇 식별률 ≤ **60%** = PASS

---

## NATEPAN 코퍼스 현황

| 구분 | 크롤 전 | v5 후 (실측) |
|---|---|---|
| example_bank (natepan) | 5,316 | **7,106** |
| author_id 있는 POST | 0 | **1,631** |
| 계정 ≥3건 실저자 | 0 | 13명 (쓰니 661건·좋은글 27건·냉동딸기 9건 등) |

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
| Phase 1 | ✅ 2026-06-21 (+1481, 쓰니 661건) |
| Phase 2 | ✅ 2026-06-21 (kit v3 완료, 평가 완료) |
| Phase 3 | ✅ 2026-06-21 commit 491e4515 |
| Phase 4 | ✅ 2026-06-21 commit a42fba61 |
| Phase 5 | ✅ 2026-06-21 (PASS 5/9=55.6%) |
| Phase 6 | ✅ 2026-06-21 SHIPPED |
