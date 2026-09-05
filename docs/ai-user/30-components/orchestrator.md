# AI User Orchestrator

`ai-user/orchestrator`는 AI-user 시스템의 실질적인 제어면이다. 신규 경로는 outbox에서 PLAN을 만들고 due item을 게시하는 구조이며, 기존 tick/`ActionExecutor`는 전환 기간의 호환 경로다.

## 주요 컴포넌트

| 컴포넌트 | 역할 |
|---|---|
| `EnvironmentGuard` | 기동 시 `AI_USER_ENV`(prod\|dev)와 실제 DB·backend 호스트명을 대조, 불일치/누락이면 기동을 거부 |
| `OrchestratorScheduler` | 메인 tick cron 트리거 |
| `BehaviorEngine` | kill-switch, cap, feed 로드, quota, persona 선택 |
| `ActionPlanner` | 어떤 행동을 할지 결정 |
| `ActionExecutor` | 글/댓글/대댓글/반응 실행 |
| `BackendInternalClient` | orchestrator → backend `/api/internal/ai-user/**` 전용 HTTP 클라이언트(`AI_USER_INTERNAL_TOKEN`). synthetic 계정 upsert·비밀번호 회전·조회수 reconcile 호출을 담당 — orchestrator는 더 이상 `users`/`post_views`를 직접 쓰지 않는다 |
| `ViewDispatcher` | 조회수 배분 결정 후 `BackendInternalClient`로 backend reconcile 호출(`POST /api/internal/ai-user/views/reconcile`). `post_views` 직접 INSERT는 제거됨 |
| `PromptTemplateCache` | admin이 편집하는 `ai_prompt_template`을 5분 TTL로 읽어 워커 요청의 `promptOverrides`에 실어 보냄 — `llm-ai-user`는 DB를 모르므로(무상태, 2026-09) 이 캐시가 유일한 경로 ([llm.md](llm.md) § prompt source) |
| `Jitter` | tick 내 분산 지연, reply 장지연 |
| `PairedPostScheduler` | 연인/부부/친구 양면 사연 |
| `DailyPlannerScheduler` | 하루 계획 수립 (04:00 KST) |
| `DailyPlannerRetryScheduler` | 플래너 실패 복구 (04:30 KST, 최대 1회) |
| `CrawlerTriggerScheduler` | learning crawl trigger |
| `BackendOutboxScheduler` | backend outbox를 소비해 plan/inbox 생성 |
| `ThreadPlanGenerationScheduler` | 요청된 plan을 구조화 LLM으로 한 번에 생성 |
| `ThreadQualityGate` | LLM 응답(및 micro-batch merge) 후 cast·**story-side 제외(작성자/상대방 자작 댓글 금지)**·parent·safety·stance≤80% 검사; READY 하한 미달 시 댓글 LLM 1회 재생성 → 재미달이면 얇은 READY(kept 보존). 퍼블리셔도 `STORY_PERSONA_COMMENT`로 최종 차단 |
| `ThreadPlanPublisherScheduler` | due item lease·멱등 게시 |
| `HumanReplyBatchScheduler` | 사람 댓글/대댓글을 30분 단위로 묶어 reply 생성 |
| `HumanReplyTtlCleanupScheduler` | inbox/REQUESTED plan TTL 정리 (플래그 기본 OFF, no-op) |
| `PersonaLottery` | 작성자·댓글자 가중 비복원 추첨(LRU×tier). 2026-09-05 `PersonaMatcherService`·`PersonaCapsuleSearchService`를 대체(§ 페르소나 스키마·선택 알고리즘) |
| `StoryTwinGuard` | 최근 14일 published AI 글(≤30) 대비 title/body bigram twin 가드 (`AiPostBundleService`) |
| `PlanSourceStoryResolver` | AI_POST primary source = `claimPopularSource` (findSimilar 아님) |

## Persona 신원 축 (V22, persona-diversity-v4 / WP1, 2026-09-05)

`personas` 테이블에 `age_years`(23~49) · `gender`(M/F) · `marital`(SINGLE/DATING/ENGAGED/MARRIED) ·
`married_years` · `has_kids` · `job_type`(9종: CORP_LARGE/CORP_MID/STARTUP/PUBLIC/PROFESSIONAL/
SELF_EMPLOYED/FREELANCER/JOBSEEKER/PARENT_LEAVE) · `job_title` · `style_axes`(JSON, 10축:
directness/affect/humor/stance/length/speech/emoticon/spelling/linebreak/profanity) ·
`last_post_at`/`last_comment_at` 컬럼 추가(`V22__persona_identity_axes.sql`). 기존
`voice_profile.age`(밴드)/`gender`/`job`은 호환용으로 동시 갱신된다.

- `persona.PersonaQuotaPlanner` — 활성 페르소나 id 목록 + seed → 150명 쿼터(성별 75:75, 연령대
  23-29:60/30-36:60/37-49:30, 결혼 교차 15/45/30, has_kids 45/90, tier 20/80/50, job_type 9종,
  style_axes 10축)를 결정론적으로 배정. 같은 seed면 같은 결과.
- `persona.PersonaCard` — `Persona`(+선택적 nickname) → 400자 이내 카드 텍스트. AI_POST·PAIRED·
  HUMAN_POST·human-reply 요청이 `voiceProfile` 전체 대신 이 카드 하나(`personaCard` 필드)를 쓴다.
- `persona.PersonaProfileRegenerator` — QuotaPlanner → llm 워커 `POST /generate/persona-profile`
  (`PersonaProfileLlmClient` 경유, [llm.md](llm.md) § persona-profile 참고) → 응답 검증 + 고유성
  (Jaccard < 0.3, 직전 페르소나들의 signature_phrases 집합과 비교, 최대 3회 재시도) →
  `personas` UPDATE + `persona_action_log`에 `PROFILE_REGENERATED` 감사.
- `persona.PersonaRelationshipFiller` — 150명 전원이 `COUPLE`/`MARRIAGE`/`FRIEND` ACTIVE 관계를
  최소 1개 갖도록 보정(기존 관계는 유지). MARRIED끼리 MARRIAGE(나이차 ≤8), DATING/ENGAGED끼리
  COUPLE, 나머지는 동일 연령대 ±5세 FRIEND 1~2개.

## Capsule search / Persona matcher — 삭제됨 (persona-diversity-v4, 2026-09-05)

과거 이 절이 설명하던 capsule 벡터 검색(`PersonaCapsuleSearchService`)과 그 위의 hard filter +
가중합 score matcher(`PersonaMatcherService`)는 WP3에서 **코드에서 삭제됐다**(commit `66fbc529`·
`81ba5dc9` — `PersonaMatcherService`/`PersonaCapsuleSearchService` grep 0건). 같이 삭제:
`engine/PersonaSelector`, `service/match/**`(`PersonaHardFilter`·`RankedPersona`·`FilterResult`),
`service/capsule/**`, `service/persona/PersonaAutoProvisionService`. admin 트리거
`backfill-persona-capsules`·`auto-persona-for-story`도 함께 제거됐다.

**대체**: `PersonaLottery`가 작성자·댓글자를 LRU×tier 가중 비복원 추첨으로 뽑는다(아래
"페르소나 스키마·선택 알고리즘" 절). `AiPostBundleService`는 이제 `StoryProfileAnalyzer`로
source 사연을 구조화한 뒤 `PersonaLottery.drawCommenters`로 cast를 뽑는다(author=`personas[0]`
유지). micro-batch(4~6 persona/call) 생성과 publisher가 저장된 텍스트만 게시하는 구조는 그대로다.

`persona_semantic_capsules`(V11)·`persona_match_audits`(V12)·`persona_fact_assertions`(V10)
테이블·도메인 클래스는 **삭제하지 않았다** — 마이그레이션과 리포지토리는 남아 있으나 미사용
상태다. 상세: [architecture.md](./architecture.md) § Persona 선택 — capsule 검색·matcher 폐기.

## 스케줄

| 작업 | 현재 코드 기본값 | compose override |
|---|---|---|
| main tick | `0 */10 * * * *` | 동일 |
| paired posts | `0 0 5 * * *` | dev/prod 모두 `0 0 */2 * * *` |
| daily planner | `0 0 4 * * *` | 없음 |
| planner retry | `0 30 4 * * *` (실패 30분 후, 최대 1회) | 없음 |
| crawler trigger | retired (learning 02:00 KST) | orchestrator `AI_LEARNING_CRAWL_ENABLED=false`; learning APScheduler SSOT |

## Daily Planner 실패 복구

`DailyPlannerScheduler`가 04:00에 실패하면 다음과 같이 자동으로 복구된다:

1. **1차 실패 기록 (04:00)**: `DailyPlannerScheduler.planDaily()`가 예외를 던지면, `DailyPlannerRetryService.recordInitialFailure()`가 `daily_planner_retry_log` 테이블에 FAILED 상태로 기록한다.
   - status=FAILED, attempt_count=1, error_message/class/stacktrace 저장
   - 무한 재시도 방지: 이미 기록된 날짜에는 중복 기록하지 않음

2. **자동 재시도 (04:30)**: `DailyPlannerRetryScheduler.retryFailedPlanDaily()`가 30분 뒤 자동으로 실행되어, 어제 실패한 쿼터 계획을 한 번 더 수행한다.
   - `DailyPlanner.planForToday()` 재호출
   - attempt_count=2로 갱신
   - status를 SUCCESS 또는 최종 FAILED로 기록

3. **2회차 이상 금지**: attempt_count≥2인 항목은 재시도 스케줄러가 스킵한다.

4. **리밸런싱 (단조성)**: `DailyPlanner`의 `targetViews` 업데이트는 이미 진행 중인 쿼터를 절대 감소시키지 않는다.
   - 재계산한 targetViews가 기존 targetViews보다 작으면, 기존값을 유지한다.
   - doneViews > 0이면 새로운 target = max(신규, 기존)으로 조정한다.
   - 이를 통해 재시도 시 이미 배치된 생성 작업이 줄어들지 않는다.

**관찰 포인트**:

```sql
-- 재시도 로그 확인
SELECT day_bucket, attempt_count, status, error_class, created_at, retry_attempted_at
FROM daily_planner_retry_log
WHERE day_bucket >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
ORDER BY created_at DESC;

-- 성공률 분석
SELECT status, COUNT(*) as count FROM daily_planner_retry_log GROUP BY status;
```

## tick 흐름

1. `BehaviorEngine`가 `ai_user_runtime.id=1`을 읽는다.
2. `enabled=0`이면 즉시 skip한다.
3. generation config가 있으면 일일 목표 합계로 `daily_global_cap`을 재계산한다.
4. 현재 시간대 가중치와 남은 tick을 바탕으로 이번 tick 예산을 계산한다.
5. backend feed를 최대 5페이지까지 가져온다.
6. content-aware 결정이 켜져 있으면 신규 글을 최대 `analysis-budget-per-tick=3`건 분석한다.
7. 활성 페르소나 중 cooldown이 아닌 계정을 고르고 행동 타입 quota에 맞춰 계획한다.
8. reply는 `scheduleReplyWithDelay()`, 나머지는 `scheduleWithinTick()`으로 넘긴다.
9. 완료 수를 `actions_today`에 반영한다.

## 현재 글 생성 로직

### 카테고리

- `topCategory(persona)`가 `persona.interests`에서 가장 높은 6광장 하나를 뽑는다.
- 이 값이 backend post category와 RAG category, prompt guide에 동시에 쓰인다.

### CASUAL vs CONFLICT

- 기본 CASUAL 확률은 `25%`
- `persona_life_state.casual_streak >= 2`면 CASUAL 확률이 `10%`로 내려간다
- CASUAL 글은 `assembleCasualPostPrompt()`를 사용하고 갈등 예시 few-shot을 생략한다

### AI_POST source (2026-08-05 — popularity claim)

예약/배치 AI 글의 **primary reconstruct source**는 `findSimilar`를 쓰지 않는다.

1. 배치가 source community를 먼저 고른다: **Blind 70% / Natepan 30%**
   (`SourceMixPlanner` / nightly·paired 슬롯 — remainder는 blind 우선).
2. author persona는 `voice_profile.voice_type`이 그 community와 맞는 계정
   (BLIND→blind, NATEPAN→natepan).
3. `PlanSourceStoryResolver.claimAndResolve` →
   `AiLearningClient.claimPopularSource(source, reservationKey, reserveUntil, plazaCategory)`.
   **plazaCategory**(COUPLE/MARRIED/…)로 claim 풀을 스코프한다 — reconstruct 본문이
   페르소나 interest 라벨과 어긋나지 않게 (2026-08-12: 카테고리 무시 버그 수정).
4. claim hit → `reconstructMode=true` + sourceExampleId/body/url/title.
   empty → 그 **시도**는 skip (archetype freestyle 폴백 없음). 새벽 fill /
   `generate-scheduled-posts`는 다른 페르소나·광장·소스(blind↔natepan)로
   재claim해 `count`/`target_posts` 저장을 맞춘다. 같은 example에 LLM 재시도는 하지 않는다.
   상세: [operations.md](../60-runtime/operations.md) §8 새벽 fill.
5. soft-reserve lifecycle: hold 성공 시 **같은 source_url 형제 row까지** 같은 key로
   reserve 유지 → publish 시 `commitSource`(key 가족 COMMITTED) →
   cancel/fail/twin-reject 시 `releaseSource` (lifecycle 경로 소유).
6. legacy tick `ActionExecutor`의 findSimilar RAG는 **호환 경로**로만 남는다.

코드: `PlanSourceStoryResolver`, `AiLearningClient`, learning
`/examples/claim-popular-source` ([learning.md](./learning.md)).

### Story twin 가드 (2026-08-05)

`StoryTwinGuard` — LLM이 title/body를 반환한 직후 `AiPostBundleService`가 검사.
최근 **14일** published AI 글(`users.synthetic=1`, ≤30건)과 비교.

| 조건 | 임계 |
|---|---|
| 정규화 제목 완전 일치 | twin |
| title char-bigram Jaccard | ≥ **0.45** |
| body char-bigram Jaccard | ≥ **0.35** |

twin이면 bundle 실패(hold skip). soft-reserve release는 lifecycle 경로.

### 게시 직전 맞춤법 (2026-08-18)

`AiPostBundleService` / legacy `ActionExecutor`는 `SoftProofread`로 **오탈자 휴리스틱이 맞을 때만** `/generate/proofread`를 호출한다. 패턴: `됬`/`됬어`/`되요`/`할께요`/`왠일` 및 자모 연속 등. 커뮤니티 비속어·특수문자는 트리거가 아니다.

교정 LLM이 줄 수를 바꾸거나(`PROOFREAD_STRUCTURE_CHANGED`) 타임아웃나면 **생성된 원문을 유지**하고 hold/게시를 버리지 않는다. 소스 길이·특수문자로 claim을 사전 skip하지 않는다.

솔로 글 전체 호출 표: [llm-call-budget.md](../70-policy/llm-call-budget.md).

### 광장 주제 적합성 게이트 (2026-08-22)

`PlazaTopicalFitGate`(`safety/`)는 생성된 사연이 선언된 광장(plaza)과 실제로 맞는지 규칙 기반으로 검사한다. LLM 호출 없음 — 제목·본문 키워드 점수(제목 ×3, 본문 주요 키워드 ×2, 보조 ×1)로 판정.

**도입 배경**: 광장은 프롬프트에 하드 제약으로 들어간다("가족 갈등만, 연인·직장·친구 제외" 등). 소스가 잘못 분류되면 LLM이 그 형식에 맞춰 조용히 각색한다 — 실사례: 역사 기사("고종의 딸 덕혜옹주가…")가 제목의 '친구' 때문에 FRIEND로 claim돼 "덕혜옹주가 일본 친구한테 털어놓은 고종 독살 얘기"로 발행됐다. 표절 가드(`StoryTwinGuard`, bigram 유사도)는 각색된 텍스트를 통과시키므로 이 오분류를 못 잡는다.

**판정**:
- `EXEMPT` — 선언 광장이 `OTHER`(특정 광장에 안 맞는 갈등이라는 뜻이라 항상 불일치로 잡히므로 평가 제외)
- `MATCH` — 선언=추론 동일 / 인접 광장(`COUPLE↔MARRIED`, `FRIEND↔FAMILY`) / 1·2위 점수차(margin) ≤ 4
- `MISMATCH` — margin > 4 (추론 광장이 명확히 우세)

로그: `[PLAZA_FIT] declaredPlaza=... inferredPlaza=... declaredScore=... inferredScore=... margin=... verdict=... reason=...`

**설정** (`ai-user.thread-plan.plaza-topical-fit-gate`, `OrchestratorProperties` — 현재 env override 없이 코드 기본값만):
| 필드 | 기본값 | 설명 |
|---|---|---|
| `loggingEnabled` | `true` | 판정·로그 수행 여부. false면 평가 자체를 건너뜀 |
| `blockingEnabled` | `false` | MISMATCH 시 발행 차단 여부. 현재 로그 전용 |

**실측**: 초기 규칙(OTHER 미제외 + 등장 여부만 판정)은 실제 발행 글 275건에서 오탐 32.0%. OTHER 제외 + 인접 광장 허용 + margin>4 판정으로 재설계 후 258건 기준 오탐 **5.0%**(13건, 그중 9건은 실제 오분류를 정상 탐지).

### 구조화 생성 실패 텔레그램 알림 (2026-08-21)

스키마 강제 없이 프롬프트 지시로 JSON을 생성하는 모드([llm.md](./llm.md) 참조)에서는 파스 실패가 조용한 콘텐츠 누락으로 이어질 수 있다. 두 신호를 분리해 알림한다.

- **번들 유실(하드 실패)**: `AiPostBundleService`의 `generateAndPublish`/`generateAndHoldResult` 실패 경로에서 즉시 알림
- **PARSE_FAIL(재시도로 복구됨)**: 빈도 기반 — 임계값 초과 시 1회 알림 후 쿨다운 동안 재알림 억제(복구 가능한 실패마다 울리면 곧 무시하게 됨)

**설정** (`ai-user.thread-plan.structured-generation`, env override 가능):
| 키 | 환경변수 | 기본값 |
|---|---|---|
| `failure-alerts-enabled` | `AI_USER_STRUCTURED_GENERATION_FAILURE_ALERTS_ENABLED` | `true` |
| `parse-fail-threshold` | `AI_USER_STRUCTURED_GENERATION_PARSE_FAIL_THRESHOLD` | `3` |
| `parse-fail-window-minutes` | `AI_USER_STRUCTURED_GENERATION_PARSE_FAIL_WINDOW_MINUTES` | `30` |
| `parse-fail-cooldown-minutes` | `AI_USER_STRUCTURED_GENERATION_PARSE_FAIL_COOLDOWN_MINUTES` | `360` |

PARSE_FAIL은 오케스트레이터가 아니라 `ai-user/llm`(별도 gradle 모듈) 워커에서 발생하므로, 같은 알림·레이트리미터 로직을 워커 쪽에도 별도 구현해 중복 유지한다(`LlmStatsLogger` 중복과 동일한 이유 — 모듈 간 의존성을 새로 만들지 않기 위함). 워커측 설정 키는 `llm.structured.*`, env var는 `LLM_STRUCTURED_GENERATION_*` 접두사.

알림 문구에는 환경·상관ID·실패 사유·롤백 힌트(`LLM_STRUCTURED_PROMPT_MODE=false` + 재빌드)가 포함된다. 전송 실패가 생성을 깨뜨리지 않도록 try/catch로 감싼다.

### 반복/길이 가드 (legacy tick)

- 최근 글 history에서 본문 3개를 읽어 2-gram Jaccard를 계산한다.
- `AI_USER_REPETITION_THRESHOLD=0.45` 초과 시 1회 재생성
- `AI_USER_MIN_POST_CHARS=50` 미만이면 `MEDIUM` 길이로 1회 재생성

### ML rerank

- `AiUserMlClient.isEnabledFor(community)`가 true일 때만 best-of-N 사용
- 기본 compose는 `AI_USER_ML_ENABLED=false`
- 현재 운영 기본 경로는 single draft다

## 댓글/대댓글 로직

- comment mode는 `REACTION_ONLY`, `SHORT_AGREE`, `QUESTION`, `DISAGREE`, `EXPERIENCE`, `ADVICE`, `TANGENT` 중 하나를 가중치로 고른다.
- 초단문 지향 힌트가 prompt에 들어간다.
- 댓글/대댓글 응답에서 `<<<REACT>>>` 이하 JSON을 분리해 piggyback reactions에 사용한다.

## PLAN-first 실행 경로

1. 글 생성/수정과 사람 댓글/대댓글은 backend outbox에 기록된다.
2. AI 글은 한 요청으로 post와 후보 풀을, 사람 글은 후보 풀을 생성한다. 후보 풀은 8~30개 범위이며 기본 24개다.
3. 사람 interaction은 30분마다 최대 10개 post/50개 입력을 묶고, input comment ID마다 최대 한 개의 AI reply만 받는다.
4. publisher는 부모·공개상태·revision·차단 여부를 재확인한 뒤 `Idempotency-Key`로 게시한다. 실패는 `FAILED`로 남기며 다른 provider로 자동 전환하지 않는다.
5. 게시글은 최대 24시간만 관리하고, 초기 활동 창에 더 많이 배치하되 KST 사람 활동 분포에 맞춰 심야 집중을 피한다.

세부 계약과 상태 전이는 [thread-planning.md](../60-runtime/thread-planning.md)가 권위본이다.

## paired posts (양면 사연, prod 활성)

`PairedPostScheduler`는 `profiles/relationships.yml`의 `COUPLE`/`MARRIAGE`/`FRIEND` 관계로 **작성자(A)+상대방(B)** 가 각자 입장을 쓰는 양면 사연을 만든다.

**운영 정책 (2026-08-02~)**: 하루 AI 글의 **20%**(`PAIRED_POST_TARGET_SHARE=0.20`)는 양면 사연이어야 한다. prod orchestrator 기본은 `PAIRED_POST_ENABLED=true`. 새벽 배치(`nightly-ai-user-batch.sh`)가 단독 예약글과 양면 사연 수를 `ceil(N×0.20)`로 나눠 생성하고, cron은 당일 부족분을 보충한다.

정책:

- 하루 목표(`ai_user_generation_config.target_posts`)의 `20%`를 양면 사연으로 채운다 (`ceil`).
- 양면 사연 내부 구성은 `COUPLE + MARRIAGE`가 `80%`, `FRIEND`가 `20%`.
- `PAIRED_POST_PAIRS`는 한 스케줄 실행 상한. 야간 배치는 `?count=`로 당일 할당분을 넘긴다.
- LLM: 작성자는 `stance=AUTHOR`(`voice/post_paired_author.md`), 상대방은 `stance=PARTNER`(`voice/partner.md`).

흐름 (**2026-08-04~ author-public-first** — `PRIVATE + WAIT_FOR_PARTNER` 즉시비공개 폐기):

1. **Call1**: 작성자 글 + phase1 댓글 후보(작성자 본문만, 약 2–4 최상위). generate ≠ publish — solo `generateAndHold`처럼 `scheduledPublishAt`까지 홀딩.
2. 작성자 발행 슬롯: **KST 02–06 하드 밴**(quiet hours). 슬롯 도래 시 **즉시 PUBLIC**(투표·댓글 가능). `WAIT_FOR_PARTNER` enum을 쓰더라도 동작은 `PUBLISH_NOW`와 동일.
3. 초대 토큰 발급. phase1 댓글 `scheduledAt`은 파트너 도착 시각(T0+Δ) **이전**으로 clamp.
4. 파트너 제출 시각 T0+Δ, Δ ∈ **[10분, 120분]**, 중앙값 **~50–60분**(치우친 분포). 파트너 슬롯은 quiet hours(02–06)에 **착륙 허용**.
5. **Call2**: 파트너 본문(`PARTNER`) + phase2 댓글 후보(작성자+상대 양쪽). Call2 컨텍스트에 게시된 최상위 댓글 **최대 5–8개**(없으면 0 OK).
6. partner answer 제출 → 이미 PUBLIC인 글에 상대 본문 부착 (+ outbox `PARTNER_ANSWER_ADDED`). **첫 PUBLIC 게이트가 아님**.
7. 파트너 도착 시: **미게시** plan item 취소 → phase2(양쪽 컨텍스트) regenerate. **이미 게시된 phase1 댓글은 보존**.

> **레거시 메모**: 2026-08-03 이전엔 partner answer로 PRIVATE→PUBLIC 전환 직후 `ensureCommentPlanForPairedPost` 한 번에 양쪽 PLAN을 심었다. 지금은 phase1(author-only) → phase2(both) 이단. 백필: `POST /admin/trigger/ensure-paired-comment-plan?postId=…`.

> 사람 파트너가 **기존 공개 글에 나중에 답**해 revision이 생기는 경우의 PLAN 재생성은 동일 replan 계약([architecture.md](./architecture.md) · [thread-planning.md](../60-runtime/thread-planning.md)).

## 페르소나 스키마 · 선택 알고리즘 (2026-09 persona-diversity-v4, WP1~WP4 병합 완료·2026-09-05)

전체 계약: `docs/_active/persona-diversity-v4.md`. `personas`(Flyway `V22__persona_identity_axes.sql`,
WP1)에 정체성 축이 추가된다:

| 컬럼 | 값 | 비고 |
|---|---|---|
| `age_years` | 23~49 | |
| `gender` | `M`\|`F` | |
| `marital` | `SINGLE`\|`DATING`\|`ENGAGED`\|`MARRIED` | |
| `married_years` | 1~24, `≤ age_years−23` | MARRIED만. 결혼 최소 연령 23세(2026-09-05 수정 — 최초 25세안이 계약2의 23~29세 밴드 MARRIED 15명 요구와 상충해 married_years=0 기혼(23세)이 나오던 결함 수정). married_years=0 금지 → MARRIED 배정 최소 연령 24세 |
| `has_kids` | bool | MARRIED만 1 가능 |
| `job_type` | 8종(`CORP_LARGE`·`CORP_MID`·`STARTUP`·`PUBLIC`·`PROFESSIONAL`·`SELF_EMPLOYED`·`FREELANCER`·`JOBSEEKER`·`PARENT_LEAVE`) | |
| `job_title` | LLM 생성 문자열 | |
| `style_axes` | JSON, 10축(directness·affect·humor·stance·length·speech·emoticon·spelling·linebreak·profanity) | 축별 균등 분포 강제 |
| `last_post_at` / `last_comment_at` | | 선택 가중치 계산용, WP3 갱신 |

150명 쿼터(성별 75/75·연령대 60/60/30·결혼 미혼60/기혼90·기혼 중 자녀45·tier
HEAVY20/REGULAR80/LIGHT50·voice_type NATEPAN75/BLIND75)는 `python3 ai-user/tools/persona_gate_check.py
--gate a`로 검증한다(±3 허용). `PersonaCard.render(Persona)`(400자 이내)가 `voiceProfile` 전체
JSON을 대체해 모든 생성 요청의 `personaCard` 필드로 들어간다.

**선택 알고리즘**: 작성자·댓글자 선택은 하드 필터(카테고리별 시점 제한, `active=1`, 자기 글
댓글 금지) 통과자 중 `weight = tierW × (1 + hoursSinceLast/24)^1.5`
(HEAVY 3.0 / REGULAR 1.5 / LIGHT 1.0) 가중 비복원 추첨(`PersonaLottery`)이다 — 삭제된
score 기반 matcher(`PersonaMatcherService`, 위 "Capsule search / Persona matcher — 삭제됨" 참고)를
대체한다. `personaId` 기준 결정론 정렬 금지.

## history와 life state

현재 orchestrator는 persona tree가 아니라 DB에 직접 쓴다.

| 저장소 | 역할 |
|---|---|
| `persona_history_entries` | 최근 글/댓글 재주입과 반복 억제 |
| `persona_life_state` | `casual_streak`, `ongoing_situation` |

`LegacyPersonaHistoryMigrator`가 startup 시 남아 있는 `profiles/*/history/*.md`, `life_state.json`을 DB로 가져온 뒤, `ActionExecutor`는 이후 DB만 읽고 쓴다.
host 권한 때문에 일부 root-owned legacy 파일이 남을 수 있지만 current runtime source는 아니다.

## 내부 API

### admin/manual

- `POST /admin/trigger/tick`
- `POST /admin/trigger/paired-posts` (`?count=` 선택 — 최대 N쌍)
- `POST /admin/trigger/ensure-paired-comment-plan?postId=` — 공개 양면 사연 댓글 PLAN 강제 생성
- `POST /admin/trigger/reset-counter`
- `POST /admin/trigger/backfill-comment-likes`
- `POST /admin/trigger/generate-posts`
- `POST /admin/trigger/generate-scheduled-posts?skipSourceClaim=true` — `PlanSourceStoryResolver`의 소스 claim(reconstruct) 단계를 건너뛴다. dev canary 전용(`scripts/ai-user-canary.sh`) — prod에서 쓰면 실제 소스 없이 freestyle로 발행되므로 쓰지 않는다
- `POST /admin/trigger/publish-scheduled-post?id=&force=true` — 예약글 단건 즉시 게시. dev canary 전용. `force=true`는 슬롯 시각(`scheduledPublishAt` 미도래)과 QuietHours(KST 02–06) 밴만 무시하며, kill switch(`ai_user_kill_switch`/`schedule_execution_paused`)는 절대 우회하지 않는다(96b1b935). prod에서 `force=true` 호출 금지 — 새벽 게시가 발생할 수 있다
- `POST /admin/trigger/update-cap`
- `POST /admin/trigger/regenerate-persona-profiles?seed=&batch=10&dryRun=true&only=<id,id>&force=false` — WP1 신원 축 재생성. `dryRun=true`면 QuotaPlanner 분포만 반환(LLM 호출 없음, 계약 2 쿼터 검증용). `only`는 콤마구분 personaId — QuotaPlanner는 항상 전체 활성 인원 기준으로 계산하고 실제 LLM 호출·DB 갱신만 그 id들로 좁힌다. `force=true`면 `style_axes`가 이미 있어도 재생성한다
- `POST /admin/trigger/fill-persona-relationships?seed=` — WP1 150명 관계 ≥1 보장(기존 관계 유지)

> **(2026-09 persona-diversity-v4, 2026-09-05 병합 후 확정)** WP1~WP3 병합(commit `66fbc529`)이
> admin 트리거 2개를 추가(위 `regenerate-persona-profiles`·`fill-persona-relationships` — 이미
> 반영됨)하고 2개를 삭제했다: `backfill-persona-capsules`·`auto-persona-for-story`
> (`PersonaCapsuleSearchService`/`PersonaAutoProvisionService` 삭제와 함께 제거, 코드에 0건).

### test

- `POST /api/test/plan-daily`

### admin/metrics — LLM 관찰성

```
GET /admin/metrics/llm-today
Authorization: (내부 네트워크만, JWT 불필요)
```

AI-user orchestrator의 LLM 호출 통계를 24시간 rolling 집계로 반환한다. **메모리 기반**(영속하지 않음, 재시작 시 리셋).

**Response 200**
```json
{
  "timestamp": "2026-08-20T15:30:45.123Z",
  "scope": "in-memory 24h rolling",
  "stats": {
    "AI_POST": {
      "totalCalls": 42,
      "totalRetries": 3,
      "retryRate": "7.1%",
      "retryReasons": {
        "PROVIDER_ERROR": 2,
        "PARSE_FAIL": 1
      },
      "resultCounts": {
        "OK": 40,
        "FAIL": 2
      },
      "totalInputTokens": 125000,
      "totalOutputTokens": 45000,
      "totalCacheRead": 8000,
      "totalCacheWrite": 2000,
      "avgCacheHitPercent": "5.6%"
    },
    "COMMENT": { ... },
    "HUMAN_REPLY": { ... }
  }
}
```

**LlmCircuitBreaker**(`service/llm/`): 동일 `retryReason`이 3회 연속 발생하면 OPEN → 30분 뒤 자동 HALF_OPEN(재시도 허용). OPEN 상태에서는 해당 사유의 생성을 스킵하고 아래 로그를 남긴다. `/admin/metrics/llm-today` 응답에도 현재 서킷 상태가 포함된다.
```
[CIRCUIT] OPEN reason=PROVIDER_ERROR consecutiveFailures=3 promptHashes=[...]
```

**[LLMSTATS] 로그 형식** (내부, 단일행):

orchestrator가 LLM을 호출할 때마다 로그에 다음 포맷의 한 줄이 나온다:

```
[LLMSTATS] ts=2026-08-20T15:30:45Z sys=AS type=AI_POST model=claude-sonnet-5 attempt=1 retryReason=NONE in=1500 out=450 cache_read=200 cache_write=50 cache_hit=13% result=OK duration_ms=2500 corrId=f47ac10b-58cc-4372-a567-0e02b2c3d479
```

| 필드 | 설명 |
|---|---|
| `ts` | ISO-8601 UTC 타임스탬프 |
| `sys` | 시스템 식별자 (`AS` = Again-Spring 메인) |
| `type` | 워크로드 타입 (`AI_POST`, `COMMENT`, `REPLY`, `HUMAN_POST`, `HUMAN_REPLY`, `PAIRED_PHASE1`, `PAIRED_PHASE2` 등) |
| `model` | 사용한 모델 ID (`claude-haiku-4-5-20251001`, `claude-sonnet-5` 등) |
| `attempt` | 시도 번호 (1부터 시작) |
| `retryReason` | 재시도 사유: `NONE` / `PROVIDER_ERROR` / `PARSE_FAIL` / `EMPTY_RESULT` / `CRITIQUE_FAIL` / `SAFETY_BLOCKED` / `TIMEOUT` |
| `in` | 입력 토큰 수 |
| `out` | 출력 토큰 수 |
| `cache_read` | 캐시 읽기 토큰 수 |
| `cache_write` | 캐시 쓰기 토큰 수 |
| `cache_hit` | 캐시 히트율(%) |
| `result` | 최종 결과: `OK` / `RETRY` (재시도 중) / `FAIL` (최종 실패) |
| `duration_ms` | 실행 시간(밀리초) |
| `corrId` | 요청 correlation ID (UUID) |

**활용**: grep + jq로 실시간 파싱 가능. 예시:
```bash
# 지난 1시간의 AI_POST retry rate
docker logs orchestrator | grep '\[LLMSTATS\]' | jq 'select(.type=="AI_POST" and .retryReason!="NONE")' | wc -l

# 오늘의 마케팅 LLM 비용 추정 (tokenomics 기준)
curl http://localhost:8096/admin/metrics/llm-today | jq '.stats | to_entries[] | {type: .key, totalIn: .value.totalInputTokens, totalOut: .value.totalOutputTokens}'
```

---

| 설정 | 코드 기본 | compose dev | compose prod |
|---|---|---|---|
| `AI_USER_DAILY_GLOBAL_CAP` | `200` | `200` | `500` |
| `AI_LEARNING_ENABLED` | `false` | `true` | `true` |
| `AI_USER_ML_ENABLED` | `false` | `false` | `false` |
| `PAIRED_POST_PAIRS` | `2` | `3` | `3` |
| `PAIRED_POST_TARGET_SHARE` | `0.20` | `0.20` | `0.20` |
| `PAIRED_POST_ROMANTIC_SHARE` | `0.80` | `0.80` | `0.80` |
| `PAIRED_POST_ENABLED` | `false`(yml) | `false`(휴면) | `true` |

## 현재 코드 기준 주의점

- `AI_USER_ENABLED`는 `BehaviorEngine`과 PLAN service의 실제 gate다.
- runtime row가 비활성이면 scheduler는 계속 돌지만 모든 tick이 skip된다.
- PLAN rollout은 환경의 `AI_USER_THREAD_PLAN_*` gate와 DB config의 `scheduler_mode/provider`가 모두 필요하다. provider `OFF`는 새 job만 막고, pause/kill switch의 의미는 [operations.md](../60-runtime/operations.md)를 따른다.
