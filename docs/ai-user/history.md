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

## persona-diversity-v4 (2026-09-05, Phase 1~3 완료 — Phase 4 prod 반영 진행 중)

**문제**: 150명 페르소나가 `lexicon` 3종·`reply_style` 2종만 공유해 말투가 사실상 동일했다
(`persona_strengthener.py`가 `voice_type`으로 SELECT 후 `voice_profile`을 일괄 덮어써 개별화를
지워버린 게 직접 원인). 30일간 글 0건인 페르소나가 52%, 상위 10명이 전체 글의 46%를 썼다(활동
쏠림). 연령이 10대~60대로 퍼져 있어 실제 타겟(20~40대)과 어긋났다(과거 글에 정년·손주·환갑
서사가 섞여 있었던 게 증상). 크롤 원문이 프롬프트에 그대로 들어가는 경로도 남아 있었다(레거시
`/generate/post`, 원문 800자를 그대로 실음).

**조치** (WP1~WP4 병렬 worktree, Phase 2 병합 commit `81ba5dc9`):

- 신원 축 10종 컬럼 신설(`V22__persona_identity_axes.sql`): `age_years`(23~49)·`gender`·`marital`·
  `married_years`·`has_kids`·`job_type`(9종)·`job_title`·`style_axes`(JSON 10축: directness·affect·
  humor·stance·length·speech·emoticon·spelling·linebreak·profanity)·`last_post_at`·`last_comment_at`.
  150명 쿼터를 축별로 결정론적으로 배정(`PersonaQuotaPlanner`, 성별 75:75·연령대 60:60:30·결혼
  60:90 등, 오차 ±3을 게이트 a로 검증) — 전체 계약은
  [`persona-identity-contract.md`](./30-components/persona-identity-contract.md).
- 150명 전원 프로필을 LLM(`PersonaProfileRegenerator` → llm 워커 `/generate/persona-profile`)으로
  재생성. 필수 11개 키 검증 + `voice_profile.profile_rev="v4"` 갱신 마커로 완료 판정.
- 크롤 원문을 그대로 프롬프트에 싣던 경로를 골격 추출로 교체(`POST /v2/extract-skeleton`, Haiku) —
  고유명사·금액·날짜를 일반화하고 원문 문장을 담지 않는다. 추출 실패 시 원문 폴백 없이 생성을
  건너뛴다.
- `PersonaCard`(400자 요약)로 프롬프트 페이로드를 통일 — AI_POST·PAIRED·HUMAN_POST·human-reply
  전부 `voiceProfile` 전체 JSON 대신 이 카드 하나(`personaCard` 필드)를 쓴다.
- 페르소나 선택을 `PersonaLottery`(tier×LRU 가중 비복원 추첨, `weight = tierW × (1 +
  hoursSinceLast/24)^1.5`)로 통일하고, 벡터 검색 기반 `PersonaCapsuleSearchService`와 hard
  filter+score `PersonaMatcherService`(+`PersonaSelector`·`service/match/**`·`service/capsule/**`)를
  코드에서 완전히 삭제했다(commit `66fbc529`).
- 카테고리 비율(WORK 35%·COUPLE 25%·FRIEND 15%·FAMILY 15%·MARRIED 10%)과 상대방(B) 시점 제한
  (WORK·FAMILY는 B시점 금지, COUPLE은 `marital != MARRIED`만 작성자)을 `CategoryMixPlanner`로
  강제해 기존 `romanticShare` 설정을 대체했다.

**감사 발견·수정 10건** (Phase 3, commit `c1ac52f7`·`556cba48`·`dcd14138`·`2048b25a`): marital
컬럼 미독해로 MARRIED 작성자 추첨이 0명이 되던 계약 위반, `SourceOverlapGuard`·`PersonaCard`
미배선, 프로필 저장과 감사 로그가 트랜잭션 밖에서 따로 커밋되던 데이터 무결성 결함, `has_kids
BIT(1)` 캐스팅 누락으로 게이트 JSON 집계가 깨지던 결함 등. 상세는
아래 **Phase 4 이후 추가 작업**과 [persona-identity-contract.md](30-components/persona-identity-contract.md).

**Phase 4 이후 추가 작업 (2026-09-05~09-08)**

prod 반영 과정에서 나온 것들이다. 대부분 "테스트는 통과하는데 실물에서 드러난" 유형이다.

- **발행 후처리 실패가 같은 사연을 두 번 게시**하던 결함(commit `7196bbc5`). 글이 백엔드에
  만들어진 뒤 상대방 예약·phase1 댓글에서 예외가 나면 예약 row가 재시도로 되돌아갔고, 이미
  만들어진 글은 남아 다음 틱이 다시 게시했다. 후처리를 try/catch로 격리해 발행을 성공으로
  확정한다. 인증 실패 경로에만 없던 재시도 상한(3회)도 함께 맞췄다.
- **도구 prod 가드가 우회 가능**했다(commit `3d78d7f5`). `--i-mean-it` 검사가 `--env-file`
  파일명만 봐서 `--db-container`로 대상을 바꾸면 dev 환경 파일로 prod에 쓸 수 있었다. 판정을
  실제 컨테이너·DB 이름 기준으로 바꿨다. DB 비밀번호가 `ps`·`docker top`에 평문으로 남던 것도
  `--env-file` 방식으로 바꿔 argv에서 제거했다.
- **골격 추출에 오류·거절 시그니처 검사가 없었다**. 프로필 생성에는 있는데 골격만 빠져,
  거부 응답이 골격 필드로 프롬프트에 실릴 수 있었다(절대 규칙 #7). 대댓글 경로만 페르소나
  카드를 무해화하지 않던 비대칭도 함께 고쳤다. 그 과정에서 `clean()`이 제어문자 제거 후
  문자열에 원본 길이로 substring을 걸어, 크롤 원문에 제어문자가 하나만 있어도 생성이 예외로
  죽던 사전 결함을 발견해 수정했다.
- **`persona_relationships`를 prod-dev-sync에서 제외**했다. 관계 유형이 양쪽 `marital`을
  전제하는데 personas가 독립 진화하는 이상 관계만 덮어쓰면 dev에서 미혼 쌍이 결혼 관계로
  묶인다. 관계 부여기는 admin 트리거 전용이라 자동 치유되지 않는다.
- **문체 축이 출력에 반영되지 않던 문제**(commit `9bd6439a`). 축을 균등 배정하고 배선까지
  했는데 실측 1건에서 5개 축 중 4개가 어긋났다. 압축 라벨(`직설/분석/진지`)을 LLM이 배경
  정보로 흘려 읽은 것이다. 축마다 행동 명령으로 펼치고(`반말만 쓴다 — ~요 종결 절대 금지`)
  페르소나 블록을 말투 규칙보다 앞에 뒀다. 이후 실측에서 10개 축이 거의 전부 반영됐다.
- **고용 형태·직군을 한국 통계 기준으로 재편**(commit `9d3d2b6e`·`8f4e1a72` 계열). 이전에는
  150명 전원이 취업자였고 직업이 조직 형태로만 나뉘어 직군 축이 없었다. 그래서 프로필 LLM이
  직함을 자유롭게 지어 하루치 6건 중 4건이 마케팅으로 몰렸다. 고용 형태 12종(임금근로자 88·
  비임금 28·비경제활동 34)과 직군 12종을 쿼터 축으로 올렸다. 값과 제약은
  [persona-identity-contract.md](30-components/persona-identity-contract.md)가 권위본이다.
- **기간과 나이의 정합**. 결혼 년차만 제약이 있어 23세가 6년차 연애를 말했다. 연애 기간은
  나이−19, 직장 경력은 나이−22를 넘지 못하게 프로필·글 생성 양쪽 프롬프트에 걸었다.
- **재생성 대상 판정을 축 전체 비교로 일반화**했다. `profile_rev` 마커와 일부 축만 보던 탓에
  성별 배정을 바꿨는데도 대상이 0으로 나왔다. 축을 추가할 때마다 판정 조건을 손으로 늘려야
  하는 구조였다.

**토큰을 낭비한 판단 착오 (기록)**: 전업주부에게 "매장관리팀 대리 7년차" 같은 현직 직함이
붙는 것을 보고 검증 가드부터 만들었다. 그런데 진짜 원인은 `PersonaProfileService.jobTypeKorean`이
새 고용 형태 3종(STUDENT·UNEMPLOYED·HOMEMAKER)을 몰라 전부 "직장인"으로 번역한 것이었고,
직군은 프롬프트에 아예 전달되지 않고 있었다. 축으로 배정만 하고 LLM에게 알려주지 않은 것이다.
증상이 아니라 프롬프트 입력을 먼저 열어봤으면 Sonnet 150회 재생성 두 사이클을 아낄 수 있었다.
**축을 바꾸면 재생성 대상 판정이 전량을 잡으므로 Sonnet 150회(약 2.5시간)가 따라온다** —
축 변경 전에 프롬프트 입력 경로가 그 축을 실제로 전달하는지부터 확인할 것.

**최종 상태(2026-09-08)**: prod 스키마 v23, 150명 프로필 재생성 완료, 관계 부여 완료.
게이트 a(분포)·b(다양성)·d(관계) 전부 PASS. 게이트 c(회전)는 개선 전 기준선을 기록해 둔
상태다 — 글 쓴 페르소나 26.0%, 상위 10명 점유 46.2%, 7일 운영 후 재측정해 비교한다.
시뮬레이션 기대치는 각각 94.7%·15.0%다.

**남은 것**: (1) 직군 축을 프롬프트에 전달하기 전에 만들어진 프로필이 남아 있어, 직군과
직함이 어긋나는 페르소나가 일부 있다(내부 축이라 독자에게는 보이지 않는다). (2) 크롤 소스가
갈등 사연이 아닌 경우(연예 가십 등) 골격 추출이 그대로 통과시킨다 — 소스 적합성 필터가
필요하다. 둘 다 재생성 비용과 무관하지 않으므로 다음 트랙에서 다룬다.
