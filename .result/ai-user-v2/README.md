# AI-User v2 — Charter

> **프로젝트**: 계정 단위 현실성 — NATEPAN 전용  
> **시작일**: 2026-06-21  
> **전임**: AI-User v1 (CLOSED D-106~D-108, `.result/ai-user/`)  
> **창립 진단**: `DIAGNOSIS.md`

---

## 성공 기준 (bar)

> **"계정 타임라인 전체를 무심한 독자가 봇으로 안 보는 수준"**

- **타깃 독자**: 봇헌터가 아닌 **캐주얼 일반 독자** (포렌식 아님)
- **평가 단위**: 글 1개(×) → **계정 타임라인 전체**(○)
- **공동체**: **NATEPAN 전용** (변수 고정 원칙)
- **QLoRA**: 데이터 게이트 뒤로 연기 (Phase 6에서 판정)

**NATEPAN을 선택한 이유**: 갈등·관계 사연(事緣) 게시판 → AI 페르소나의 갈등 사연과 **장르 일치**. v1 phase-2의 최대 교란(THEQOO 연예뉴스 vs AI 갈등사연 불일치) 제거. 최심 clean 코퍼스(human 2589).

---

## v2 표준 규율 (R1~R8) — 전 Phase 불변

| # | 규율 | 근거 |
|---|---|---|
| R1 | **단위 = 계정 타임라인.** 모든 eval·목표·게이트는 계정 단위. 메시지 단위 지표(proxy·MAUVE) 의사결정 근거 금지. | lesson 1·2 |
| R2 | **proxy 사다리 금지.** LLM-as-judge·MAUVE = v1에서 사람 기준 대비 갭 큼 검증됨 → 폐기. 유일 오라클 = 사람 계정 블라인드. | lesson 2 |
| R3 | **변수 고정.** NATEPAN 전용. 생성기(Sonnet POST/Haiku reply)·코퍼스 스냅샷·페르소나 셋 고정. 측정 1회당 변수 1개만 변경. | lesson 3 |
| R4 | **저빈도 고정보 eval.** 사람 블라인드는 드물게·비싸게. 각 회차 = named-tell 라벨셋 산출. tell을 결정론적으로 제거. | lesson 4 |
| R5 | **kill criterion 사전 등록.** Phase 0에서 종료 조건 사전 등록. 충족 시 추가 튜닝 없이 종료/피벗. | lesson 5 |
| R6 | **판별기는 QA로만.** feature attribution으로 tell 진단만. optimizer/selector 사용 금지(Goodhart). `AiUserMlClient` rerank OFF 유지. | D-106 |
| R7 | **v1 제약 승계.** `AI_USER_ML_ENABLED=false` 영구, D-108 COLLECT-only 유지, 출하 레버(injectTypos·cleanupTheqoo·CASUAL 25%) 보존. `ActionExecutor.java:427`·`AiUserMlClient.java:174` 미변경. | v1 |
| R8 | **main 단일 브랜치 · docs-as-code · prod 배포 게이트(절대규칙 #4·#8·#9).** 코드 변경 시 동일 커밋 문서 갱신 + `lint:docs`. | CLAUDE.md |

---

## Kill Criterion (사전 등록 — Phase 0)

> **측정 전 오너가 확정해야 함.** 아래는 기본값 제안.

```
✅ 등록 완료 (2026-06-21, 오너 확정)

Phase 3~4 계정 레버 적용 + named-tell 제거 사이클 2회 완료 후:
캐주얼 독자(≥3인) 계정 블라인드에서 봇 정확 식별률 ≤ 60%

→ PASS (≤60%): NATEPAN 계정 레버 prod 출하
→ FAIL (>60%): QLoRA 데이터게이트 평가 (NATEPAN ≥5000 clean → 발동 옵션 제시) 또는 품질-피벗
```

---

## Phase 로드맵 요약

| Phase | 핵심 | 코드/GPU |
|---|---|---|
| 0 | 창립·방법론 동결·kill criterion 등록 | 0·0 |
| 1 | NATEPAN 갈등 사연 최대 크롤 + 작성자-그룹핑 | WSL·임베딩만 |
| 2 | 계정 단위 eval 하니스 + 실계정 타임라인 baseline | 일부·0 |
| 3 | 메모리·topic trajectory (생성 개선 핵심) | Java·0 |
| 4 | Cadence·대댓글 현실화 | Java·0 |
| 5 | named-tell 결정론 제거 루프 | 후처리·0 |
| 6 | kill criterion 판정·QLoRA게이트·클로즈아웃 | 0·0 |

---

## 핵심 코드 훅

| 레버 | 위치 |
|---|---|
| 크롤러 | `ai-user/learning/app/crawlers/natepan*` · `AI_LEARNING_CRAWL_ENABLED` |
| 코퍼스 | `example_bank`(8099) · ML corpus(8201) |
| 메모리/궤적 | `ActionExecutor.loadRecentBodies`(L1298)·`writeHistory`(L1161)·`PromptAssembler.assemblePostPrompt`(L119) |
| cadence | `Jitter.scheduleReplyWithDelay`(L55-59, 미호출) · `BehaviorEngine`(L222) · `PersonaSelector`(L28-29) |
| tell 제거 | `OutputSanitizer`(L210-291) · `SELF_CRITIQUE_EXTRA_CLICHES`(.env.dev:86) |
| 동결 | `ActionExecutor.java:427` · `AiUserMlClient.java:174` |

---

**마지막 갱신**: 2026-06-21 Phase 0 창립
