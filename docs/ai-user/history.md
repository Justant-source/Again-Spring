# AI User History

이 문서는 실험 로그 전체가 아니라 현재 코드에 남아 있는 변화만 요약한다. 상세 라운드 기록이 있던 `.result/ai-user-v2/`는 gitignore 대상 로컬 전용 문서였고, 디렉토리 자체가 로컬 디스크에서 소실되어 더 이상 존재하지 않는다(2026-09-02 확인, 복구 불가) — 이후 진행 트랙 문서는 `docs/_active/`에 두어 이런 유실을 막는다.

## v1

- AI-user 기본 스택이 분리 서비스로 만들어졌다.
- orchestrator, llm, learning, sync의 4분리 구조가 자리 잡았다.
- 페르소나 YAML, history markdown, example bank 기반 운영이 시작됐다.

## v2

- 계정 단위 realism 개선 작업이 진행됐다.
- history 재주입, cadence, named tell 대응이 추가됐다.
- current code에 남은 흔적으로는 recent output repetition guard와 life state 기반 행동 제어가 있다.

## v2.1

- 6광장 정렬(`COUPLE`, `MARRIED`, `FRIEND`, `FAMILY`, `WORK`, `OTHER`)이 제품 적합성 축으로 고정됐다.
- `PromptAssembler.CATEGORY_GUIDE`와 `ActionExecutor.topCategory()`가 광장 정렬을 직접 사용한다.
- CASUAL 글 모드, reconstruct mode, style sample fallback, source provenance 기반 재서사 경로가 코드에 남아 있다.
- ML best-of-N 경로는 유지했지만 현재 기본 운영값은 계속 `disabled`다.

## 현재 코드에 남은 결과물

| 결과물 | 어디에 반영됐는가 |
|---|---|
| 광장 정렬 | backend `PostCategory`, orchestrator `topCategory()`, llm `CATEGORY_GUIDE` |
| 일상글 분기 | `ActionExecutor.computeCasualProb()` + `PromptAssembler.assembleCasualPostPrompt()` |
| 재구성 모드 | `AiLearningClient.ExampleItem.hasSourceProvenance()` + `assembleReconstructPrompt()` |
| 문체 샘플 fallback | learning `/examples/style-sample`, orchestrator `styleExamplesFor()` |
| 반응 지연 | `Jitter.scheduleReplyWithDelay()` |
| 반복 방지 | recent history 로드 + 2-gram Jaccard |
| paired posts | `PairedPostScheduler` |
| prod→dev 복사 | `prod-dev-sync` (일일 cron) |

## Wave1-D (2026-08-01) — 크롤 소스 BLIND·NATEPAN 단일화 (코드)

오너 결정(§2.6): 다시봄이 쓰는 커뮤니티 근거는 BLIND·NATEPAN 둘뿐.

| 변경 | 내용 |
|---|---|
| `scheduler.py` `SOURCES` | natepan 1500 · blind 500 만 남김 (limit=0 항목 제거) |
| 삭제된 크롤러 모듈 | `clien` `theqoo` `ruliweb` `dcinside` `fmkorea` `mlbpark` `ppomppu` `bobaedream` `naver_comments` `daum_comments` + `natepan_backup` `dcinside_backup` |
| 유지 | `natepan.py` · `blind.py` (본문 로직은 다른 슬라이스 소유) |
| `crawl.py` registry | 위 두 source만 import |
| `register_classifier.py` | BLIND(polite)·NATEPAN(casual) 문체 패턴 보강, 다수결 임계 0.55 |

## Wave1 운영 적용 (2026-08-01) — 코퍼스 DB 단일화 + TTL + popularity 게이트 + WP1B

| 항목 | 결과 |
|---|---|
| 백업 | `prod-pre-corpus-unify-20260801-163820.sql` — 2026-09-05 보관 정책(30일)으로 삭제됨 |
| 삭제 | clien·ruliweb·theqoo·dcinside **4346건** · `BLIND`→`blind` 정규화 |
| popularity 게이트 | `popularity_gate.py` — 지표·절대하한·상대 pct≥0.50 · COMMENT는 인기 부모만 (영구) |
| WP1B | 전원 `voice_type` ∈ {NATEPAN:113, BLIND:37} · 인기 앵커로 example 재생성 · strengthener 재오염 차단 |

## 인시던트 — HUMAN_POST 플랜 생성 100% 실패 (2026-08-01, 당일 원인·수정·복구)

`ThreadPlanGenerationService`(사람 글 → AI 댓글 반응 경로)가 REQUESTED 백로그 173건을 처리하며
**173/173 전부 FAILED**. 두 단계 원인이 겹쳤다.

| 단계 | 원인 | 증상 |
|---|---|---|
| 1 | `ClaudeCliInvoker`가 프롬프트 전체(persona cast JSON 포함, 실측 685KB)를 `claude` CLI의 **명령줄 인자**로 넘김 | OS `E2BIG`("Argument list too long") — 프로세스 생성 자체가 실패 |
| 2 | (1) 수정 후 재현: WP1(`limit(24)` 제거)이 활성 페르소나 **전체(150명)**를 매 요청마다 통째로 넣도록 바뀌었는데, WP1B 정화로 페르소나당 voice_profile이 커져 실측 **~2,000 tokens/persona** → 150명 ≈ **306K tokens** | Claude API "Prompt is too long (limit 200000)" |

수정:

- `ClaudeCliInvoker`: userPart를 CLI 인자가 아니라 **stdin**으로 전달 (`claude --print`가 인자 없으면 stdin을 읽음, 실측 확인). `CodexCliInvoker`는 애초에 stdin 방식이라 영향 없었음.
- `PlanPersonaMapper.capCastPool` / `AiPostBundleService.capMegaCallCast`: 요청 1건에 넣는 persona cast를 셔플 후 **`AI_USER_THREAD_PLAN_PLAN_PERSONA_CAST_MAX`(기본 40)**로 상한. WP1의 "회전"(항상 같은 24명 고정 방지) 의도는 유지하면서 토큰 예산 안에 들어오게 함. micro-batch(4~6명)로 이미 쪼개는 AI_POST 경로는 원래 영향 없었음.
- 검증: 실패한 plan 1건을 REQUESTED로 되돌리고 provider를 잠깐 켜서 실제 스케줄러 tick으로 재생성 → `ACTIVE`, 댓글 14 + 대댓글 2 정상 생성 확인 후 provider 원복.
- **남은 172건은 FAILED 상태로 그대로 둠** — 일괄 재시도는 콘텐츠 생성 결정이라 별도 지시 없이는 하지 않음.

## Wave — source dedup (2026-08-05)

인기 crawl 원본을 AI 글이 중복 재구성하지 않도록 claim + twin 가드를 넣었다.

| 항목 | 내용 |
|---|---|
| claim API | learning `POST /examples/claim-popular-source` · commit/release · 14일→30일 expand · **`source_url` 가족** 영구 제외 (example_id만이 아님) |
| mix | Blind **70%** / Natepan **30%**; persona는 `voice_type` 매칭 |
| soft-reserve | hold 유지 → publish commit → cancel/fail/twin release · **동일 URL 형제 row도 같은 key로 SOFT** |
| twin 가드 | `StoryTwinGuard` — title Jaccard≥0.45 · body≥0.35 · exact title; 창 14일/≤30건 |
| 비변경 | crawl `SOURCES` budget(natepan 1500 · blind 500) |

## Wave — source_url concurrency guard (2026-08-10)

크롤 동시 실행이 같은 Blind URL을 `example_bank`에 이중 INSERT → claim이 example_id만
막아 사연이 두 번 재구성되던 사고를 막는다.

| 항목 | 내용 |
|---|---|
| crawl ingest | `GET_LOCK(ai_learning_crawl_ingest:{source})` + lock 하 URL 재스냅샷 후 INSERT |
| claim SELECT | 형제 `source_url`이 posts/예약에 있으면 후보 제외 |
| claim reserve | 형제 id 전부 `FOR UPDATE` + 동일 `reservationKey` SOFT · commit/release도 key 가족 |

## 현재 운영 상태를 해석할 때 주의할 점

- 2026-08-18 이후 솔로 글 LLM 횟수는 전원 micro-batch·항상 proofread가 아니다. [llm-call-budget.md](70-policy/llm-call-budget.md).
- `.result/ai-user-v2/` 문서는 historical artifact였으나 로컬 전용(gitignore) 문서라 디렉토리 자체가 소실됐다(복구 불가, 위 안내 참고). 현재 런타임 truth는 `ai-user/*` 코드와 compose 파일이다.
- compose/env에 있는 flag가 곧 실제 kill-switch는 아니다. 지금 코드에서는 runtime row나 scheduler 구현이 더 직접적인 truth다.
- persona corpus는 실험을 거치며 누적된 상태라 target 값과 실제 디렉토리 수가 다를 수 있다.

## 2026-09 결함 2: provider 추상화·무상태 워커·dev canary — 실측 (Task 7.4 최종 게이트)

단위: llm/backend OK, orchestrator 374건 중 실패 8건(AiPostBundleServiceTest NPE, 계획 이전부터 있던 기지 결함 — 무관), sync 8/8 PASS(.venv), frontend vitest 62/62 + lint:docs·lint:e2e-llm PASS(lint:emoji는 baseline부터 있던 기지 위반이라 범위 밖), docs lint 11/11 PASS. dev 배포 검증 PASS=2/WARN=0/SKIP=2(방문 트래픽 없음, 정상)/FAIL=0. AI-user canary `✅ [canary] PASS scheduled=03f2b0cb-717c-4fd5-9010-567d58f09b9e post=post_d4efe71010454927b736`(정리 후 종료, orchestrator-dev는 상시 서비스라 유지). e2e-realbe(`:8090`) 125/125 PASS(2.7m). 결함 2 계획 종결.

## 2026-09-03 결함 1·2 prod 반영 (사용자 명시 지시)

`scripts/deploy.sh prod --i-mean-it` PASS(백업 `prod-20260903-180527.sql`, health/verify PASS=2/FAIL=0) → `env/rebuild-stacks.sh ai-user`로 공유 스택(llm-ai-user·ai-learning·`ai-user-orchestrator`[prod]·`prod-dev-sync`) 재기동. `ai-user-orchestrator-dev`는 profile 게이트라 무영향(계속 가동). prod 로그 `[EnvironmentGuard] env=PROD db=againspring-mariadb-prod backend=againspring-backend-prod` 확인. `AI_USER_INTERNAL_TOKEN`을 `.env.ai-user`·`.env.prod`에 동일 값으로 배선(둘 다 gitignored, 미커밋) — `repairBotUserAccounts: upserted=100 passwordSynced=150`, 실패 0건으로 orchestrator→backend 내부 API 인증이 prod에서도 성립함을 실측. `llm-ai-user`는 prod에서도 hikari/datasource 로그 0건(무상태 확인), `/v1/providers/status` 내부 조회로 4개 provider 전부 UP. orchestrator·backend-prod 재기동 후 ERROR 로그 0건. `ai_user_orch` 전용 DB 계정(Task 4.5 SQL)은 prod에 아직 미생성 — 필요 시 별도 요청.
