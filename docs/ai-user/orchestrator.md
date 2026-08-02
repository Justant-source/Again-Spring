# AI User Orchestrator

`ai-user/orchestrator`는 AI-user 시스템의 실질적인 제어면이다. 신규 경로는 outbox에서 PLAN을 만들고 due item을 게시하는 구조이며, 기존 tick/`ActionExecutor`는 전환 기간의 호환 경로다.

## 주요 컴포넌트

| 컴포넌트 | 역할 |
|---|---|
| `OrchestratorScheduler` | 메인 tick cron 트리거 |
| `BehaviorEngine` | kill-switch, cap, feed 로드, quota, persona 선택 |
| `ActionPlanner` | 어떤 행동을 할지 결정 |
| `ActionExecutor` | 글/댓글/대댓글/반응 실행 |
| `Jitter` | tick 내 분산 지연, reply 장지연 |
| `PairedPostScheduler` | 연인/부부/친구 양면 사연 |
| `DailyPlannerScheduler` | 하루 계획 수립 |
| `CrawlerTriggerScheduler` | learning crawl trigger |
| `BackendOutboxScheduler` | backend outbox를 소비해 plan/inbox 생성 |
| `ThreadPlanGenerationScheduler` | 요청된 plan을 구조화 LLM으로 한 번에 생성 |
| `ThreadQualityGate` | LLM 응답(및 micro-batch merge) 후 cast·parent·safety·stance≤80% 검사; READY 하한 미달 시 `QUALITY_BELOW_MIN_ITEMS` |
| `ThreadPlanPublisherScheduler` | due item lease·멱등 게시 |
| `HumanReplyBatchScheduler` | 사람 댓글/대댓글을 30분 단위로 묶어 reply 생성 |
| `HumanReplyTtlCleanupScheduler` | inbox/REQUESTED plan TTL 정리 (플래그 기본 OFF, no-op) |
| `PersonaCapsuleSearchService` | story→persona top-K (capsule vector + interests fallback, LLM 없음) |
| `PersonaMatcherService` | StoryProfile → hard filter + capsule search + author/comment score + match audits (WP3) |

## Capsule search (WP2)

- 입력: 검색 텍스트(또는 category+topics), `topK`, optional register(`NATEPAN`|`BLIND`)
- 경로: learning embed → `persona_semantic_capsules` cosine top rows → persona 집계
- fallback: 활성 페르소나 `interests` (COUPLE/MARRIED/FRIEND/FAMILY/WORK/OTHER)
- optional audit: `persona_match_audits` (`AUTHOR_CANDIDATE` / `COMMENT_CANDIDATE`)
- 상세 토폴로지: [architecture.md](./architecture.md) § Capsule persona search

## Persona matcher (WP3 / W4-B)

- 입력: `StoryProfile` (`toSearchDocument()` ≤512) + topK + sourceExampleId/correlationId
- 경로: capsule pool (author≈20 / comment≈60) → `PersonaHardFilter` (active·register·age·gender·job·region만; marriage/parenting/cannot_claim은 `UNEVALUATED`) → score
- score: `0.45*semantic + 0.25*register + 0.15*fact_ratio + 0.15*interest[category]`
- API: `matchAuthors` / `matchCommenters` / `bestAuthorAbove` (threshold default `ai-user.matcher.author-threshold=0.35`)
- audits: purpose `AUTHOR_CANDIDATE`|`COMMENT_CANDIDATE`, reasons에 UNEVALUATED 축·점수 기록 (capsule search purpose 미설정 → 이중 기록 방지)
- PLAN 연동: `AiPostBundleService`가 source 사연을 `StoryProfileAnalyzer`로 1회 구조화하고, `matchCommenters`로 cast를 재정렬(author=`personas[0]` 유지). 요청에 `storyProfile`/`storySearchDoc` 포함. 기본 생성은 **micro-batch**(4~6 persona/call, `ai-user.thread-plan.micro-batch-enabled`, 기본 ON)이며 publisher는 저장된 텍스트만 게시한다.
- 매칭 실패 시: `PersonaAutoProvisionService` + `POST /admin/trigger/auto-persona-for-story`
- SSOT: `python3 ai-user/tools/wp3_persona_ssot_report.py`

## 스케줄

| 작업 | 현재 코드 기본값 | compose override |
|---|---|---|
| main tick | `0 */10 * * * *` | 동일 |
| paired posts | `0 0 5 * * *` | dev/prod 모두 `0 0 */2 * * *` |
| daily planner | `0 0 4 * * *` | 없음 |
| crawler trigger | `0 30 18 * * *` | `AI_LEARNING_CRAWL_ENABLED`로 on/off |

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

### RAG와 reconstruct mode

1. `AiLearningClient.findSimilar(topicSeed, "POST", category, 3, register)` 호출
2. 1순위 예시에 `source_url`이 있으면 그 예시를 원본으로 간주
3. 그 경우 `reconstructMode=true`, `sourceBody`, `sourceExampleId`를 llm에 전달
4. 나머지 예시는 style anchor로만 사용
5. 아무 예시도 없으면 `styleSample()`로 말투 샘플을 보충

### 반복/길이 가드

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

세부 계약과 상태 전이는 [thread-planning.md](./thread-planning.md)가 권위본이다.

## paired posts (양면 사연, prod 활성)

`PairedPostScheduler`는 `profiles/relationships.yml`의 `COUPLE`/`MARRIAGE`/`FRIEND` 관계로 **작성자(A)+상대방(B)** 가 각자 입장을 쓰는 양면 사연을 만든다.

**운영 정책 (2026-08-02~)**: 하루 AI 글의 **20%**(`PAIRED_POST_TARGET_SHARE=0.20`)는 양면 사연이어야 한다. prod orchestrator 기본은 `PAIRED_POST_ENABLED=true`. 새벽 배치(`nightly-ai-user-batch.sh`)가 단독 예약글과 양면 사연 수를 `ceil(N×0.20)`로 나눠 생성하고, cron은 당일 부족분을 보충한다.

정책:

- 하루 목표(`ai_user_generation_config.target_posts`)의 `20%`를 양면 사연으로 채운다 (`ceil`).
- 양면 사연 내부 구성은 `COUPLE + MARRIAGE`가 `80%`, `FRIEND`가 `20%`.
- `PAIRED_POST_PAIRS`는 한 스케줄 실행 상한. 야간 배치는 `?count=`로 당일 할당분을 넘긴다.
- LLM: 작성자는 `stance=AUTHOR`(`voice/post_paired_author.md`), 상대방은 `stance=PARTNER`(`voice/partner.md`).

흐름:

1. 작성자 글 생성 (`AUTHOR` — 상대가 재해석할 사건 앵커)
2. `PRIVATE + WAIT_FOR_PARTNER` 게시
3. 초대 토큰 발급
4. 파트너 입장 글 생성 (`PARTNER` — 원글 사건 재해석, 동등 분량)
5. partner answer 제출 → PUBLIC (+ outbox `PARTNER_ANSWER_ADDED` / `POST_PUBLISHED`)
6. **즉시** `ThreadPlanGenerationService.ensureCommentPlanForPairedPost`로 댓글 후보 PLAN 생성·스케줄
   (solo `generateAndHold`와 동일하게 발행 시점에 댓글이 이미 잡혀 있어야 함.
   DB `provider_*=OFF`여도 yml provider로 fallback — daytime cron / nightly EXIT trap에서도 멈추지 않음)

> **버그 수정 (2026-08-03)**: 예전에는 step 6을 outbox→REQUESTED(`HUMAN_POST`)에만 맡겼다.
> `provider_human_post_plan=OFF`면 generation이 스킵되어 양면 사연에 댓글 스케줄이 0건으로 남았다
> (예: `post_a1fd41b3c5584c8e99f7`). 백필: `POST /admin/trigger/ensure-paired-comment-plan?postId=…`.

> 참고: 이미 공개된 글에 사람/파트너가 **나중에** 답해서 revision이 생기는 경우의 PLAN 재생성 규칙은 별개다([architecture.md](./architecture.md)).

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
- `POST /admin/trigger/cleanup-ㅠ`
- `POST /admin/trigger/update-cap`

### test

- `POST /api/test/plan-daily`

## 설정값 메모

| 설정 | 코드 기본 | compose dev | compose prod |
|---|---|---|---|
| `AI_USER_PERSONA_TARGET` | `10` | `50` | `50` |
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
- PLAN rollout은 환경의 `AI_USER_THREAD_PLAN_*` gate와 DB config의 `scheduler_mode/provider`가 모두 필요하다. provider `OFF`는 새 job만 막고, pause/kill switch의 의미는 [operations.md](./operations.md)를 따른다.
