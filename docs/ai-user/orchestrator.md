# AI User Orchestrator

`ai-user/orchestrator`는 AI-user 시스템의 실질적인 제어면이다. 현재 코드는 "스케줄러가 tick을 부르고, `BehaviorEngine`이 실행 여부를 판단하며, `ActionExecutor`가 실제 행동을 게시"하는 구조다.

## 주요 컴포넌트

| 컴포넌트 | 역할 |
|---|---|
| `OrchestratorScheduler` | 메인 tick cron 트리거 |
| `BehaviorEngine` | kill-switch, cap, feed 로드, quota, persona 선택 |
| `ActionPlanner` | 어떤 행동을 할지 결정 |
| `ActionExecutor` | 글/댓글/대댓글/반응 실행 |
| `Jitter` | tick 내 분산 지연, reply 장지연 |
| `PairedPostScheduler` | 커플/부부 양면 사연 |
| `DailyPlannerScheduler` | 하루 계획 수립 |
| `CrawlerTriggerScheduler` | learning crawl trigger |

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
- `life_state.json`의 `casualStreak >= 2`면 CASUAL 확률이 `10%`로 내려간다
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

## paired posts

`PairedPostScheduler`는 `profiles/relationships.yml`의 `COUPLE`/`MARRIAGE` 관계만 사용한다.

흐름:

1. 작성자 글 생성
2. `PRIVATE + WAIT_FOR_PARTNER` 게시
3. 초대 토큰 발급
4. 파트너 입장 글 생성
5. partner answer 제출
6. 공개 후 일반 tick이 반응

## history와 life state

compose override 기준으로 orchestrator는 persona tree에 직접 쓴다.

| 파일 | 역할 |
|---|---|
| `history/posts.md` | 최근 글 재주입과 반복 억제 |
| `history/comments.md` | 댓글 재주입과 스타일 유지 |
| `life_state.json` | `casualStreak`, `ongoingSituation` |

현재 `application.yml` 기본값은 `/app/persona-history`지만 dev/prod compose는 `AI_USER_HISTORY_DIR=/app/personas/profiles`로 override한다.

## 내부 API

### admin/manual

- `POST /admin/trigger/tick`
- `POST /admin/trigger/paired-posts`
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

## 현재 코드 기준 주의점

- `AI_USER_ENABLED`는 `BehaviorEngine`의 실제 gate가 아니다.
- runtime row가 비활성이면 scheduler는 계속 돌지만 모든 tick이 skip된다.
- paired post의 기본 cron과 compose override가 다르다. 운영 주기는 compose가 truth다.
