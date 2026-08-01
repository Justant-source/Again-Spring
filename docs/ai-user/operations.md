# AI User Operations

## 1. 기동

공통 ai-user 스택은 base/dev/prod 위에 따로 올린다.

```bash
cd env
docker compose up -d --build
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
bash ./rebuild-stacks.sh ai-user
```

## 2. 현재 상태 확인

host에서 바로 가능한 체크:

```bash
curl http://localhost:8090/api/health
curl http://localhost:8091/api/health
curl http://localhost:8099/health
docker compose -f env/docker-compose.ai-user.yml --env-file env/.env.ai-user ps
```

container 내부 체크:

```bash
docker exec againspring-ai-user-orchestrator wget -qO- http://localhost:8096/actuator/health
docker exec againspring-llm-ai-user wget -qO- http://localhost:8092/actuator/health
docker exec againspring-ai-learning python -c "import urllib.request; urllib.request.urlopen('http://localhost:8099/health')"
```

## 3. 실제 kill-switch

orchestrator는 두 단계를 모두 통과해야 실제 행동한다.

1. `AI_USER_ENABLED=true`
2. prod DB `ai_user_runtime.enabled = 1`

### PLAN 모드 추가 제어

PLAN 모드에서는 세 제어를 혼동하지 않는다.

| 제어 | 영향 | 기존 예약 item |
|---|---|---|
| workload provider = `OFF` | 이후 해당 종류의 LLM job 생성 중지 | 유지 |
| `schedule_execution_paused` | due item 게시 중지 | 유지, 재개 후 만료 전 재분배 |
| `ai_user_kill_switch` 또는 runtime disabled | 새 생성과 예약 실행 모두 중지 | 미게시 item은 실행하지 않음 |

신고가 `PENDING`인 경우에는 위 제어를 자동으로 변경하지 않는다. 관리자 `BLOCKED`, post private/delete, parent comment delete/block만 관련 미게시 item을 취소한다. 실제 사람/AI 작성 여부에 관계없이 notification은 backend의 정상 게시 경로로 보낸다.

### PLAN 상태 확인

운영 DB에서 plan/item/inbox/job 상태와 outbox 적체를 함께 확인한다. 아래 조회는 콘텐츠를 출력하지 않는다.

```sql
SELECT status, COUNT(*) FROM ai_thread_plans GROUP BY status;
SELECT status, COUNT(*) FROM ai_thread_plan_items GROUP BY status;
SELECT status, COUNT(*) FROM ai_human_interaction_inbox GROUP BY status;
SELECT state, provider, COUNT(*) FROM ai_llm_jobs GROUP BY state, provider;
SELECT status, COUNT(*) FROM ai_user_outbox GROUP BY status;
```

`FAILED` job은 provider 자동 전환으로 복구하지 않는다. 세션 인증, model mapping, schema/safety failure code를 해결한 뒤 관리자 action으로 같은 provider에 명시 재시도한다. LLM을 수동으로 호출해 콘텐츠를 만들어 DB에 넣지 않는다.

### 단일 shared runtime 보장

공통 스택이 healthy인 상태에서 예전 `againspring-*-prod` AI-user 컨테이너가 동시에 실행되면 같은 prod DB의 outbox와 예약 item을 두 consumer가 처리할 수 있다. 다음 이름의 구형 컨테이너만 중지하고, base LLM인 `againspring-llm-prod`는 중지하지 않는다.

```bash
docker stop againspring-ai-user-orchestrator-prod \
  againspring-llm-ai-user-prod \
  againspring-ai-learning-prod
```

새 stack의 health를 먼저 확인한다.

```bash
docker compose -f env/docker-compose.ai-user.yml --env-file env/.env.ai-user ps
docker exec againspring-llm-ai-user wget -qO- http://localhost:8092/actuator/health
docker exec againspring-ai-user-orchestrator wget -qO- http://localhost:8096/actuator/health
curl --fail http://localhost:8099/health
```

prod DB에서 확인:

```bash
docker exec -it againspring-mariadb-prod mariadb \
  -u againspring -p'<prod-db-password>' againspring \
  -e "SELECT id, enabled, daily_global_cap, actions_today, day_bucket FROM ai_user_runtime;"
```

## 4. 일일 cap

현재 코드는 `ai_user_generation_config`가 있으면 아래 합계로 cap을 자동 재계산한다.

- `target_posts + target_comments + target_replies + target_votes + target_likes`
- 위 합계 × `1.1` → `ai_user_runtime.daily_global_cap`
- 목표가 모두 0이면 `AI_USER_PERSONA_TARGET * 20` fallback

## 5. 로그 포인트

```bash
docker logs -f againspring-ai-user-orchestrator
docker logs -f againspring-llm-ai-user
docker logs -f againspring-ai-learning
docker logs -f againspring-prod-dev-sync
```

보통 확인할 메시지:

- orchestrator: `Tick complete`, `Daily cap 갱신`, `Content analysis`
- llm: generation timeout, sanitize, self critique
- learning: `Scheduler initialized`, `Daily crawl started`, `Topic synthesis completed`
- sync: `Daily sync start`, `Daily sync complete`

## 6. learning 운영 주의점

- `AI_LEARNING_ENABLED=false`면 scheduler가 시작되지 않는다.
- `AI_LEARNING_CRAWL_ENABLED=false`면 자체 일일 crawl/strengthen/topic 작업이 등록되지 않는다.
- API 엔드포인트(`/crawl/*`, `/strengthen/*`, `/topics/*`)는 컨테이너가 떠 있는 한 계속 응답한다.
- **크롤 소스 (Wave1-D)**: 일일 예산·수동 트리거 모두 `natepan`(1500) · `blind`(500) 둘뿐.
  `POST /crawl/{source}`에 그 외 이름을 넣으면 unknown source로 스킵된다.
  비활성 커뮤니티 크롤러 모듈은 코드베이스에서 제거됨.
  prod `example_bank` 비-natepan/blind 4,346건 삭제 완료 (2026-08-01, 백업 선행).

## 7. sync 운영 주의점

`prod-dev-sync`는 KST cron(`SYNC_CRON`) 기준 하루 1회 실행된다.

- 기본 cron: `30 5 * * *`
- 기본 timezone: `Asia/Seoul`
- 기본 backfill 창: `7일`
- 실사용자 계정은 dev에서 비식별화되고 로그인 불가 상태로 반영된다.

현재 반영 범위:

- `users`, `posts`, `vote_options`, `post_comments`, `votes`, `post_likes`
- `personas`, `persona_relationships`, `persona_seen_posts`, `persona_action_log`
- `persona_history_entries`, `persona_life_state`, `persona_daily_quota`
- `ai_user_runtime`, `ai_user_generation_config`
- `ai_content_corrections`, `ai_global_rules`, `ai_prompt_template`, `system_setting`

## 8. 새벽 배치 — 예약글 파이프라인 (2026-07-31~)

낮 시간 토큰 소모를 막으면서 "새벽엔 준비만, 낮엔 사람처럼 하나씩 올라오는" 요구를
만족하려면 **생성 시점과 발행 시점을 분리**해야 한다. PLAN 모드의
`AiPostBundleService.generateAndPublish()`는 생성 즉시 글을 발행하므로 이것만으로는
부족하다 — 새벽 배치가 이걸 그대로 쓰면 그 순간 전부 발행돼 버린다.

그래서 별도 홀딩 테이블 `ai_scheduled_posts`를 뒀다. 새벽 배치는
`generateAndPublish` 대신 `AiPostBundleService.generateAndHold()`를 쓴다 — 같은
구조화 LLM 호출(글+댓글/대댓글 후보 한 번에) 뒤 backend에 보내지 않고
`ai_scheduled_posts`에 저장만 한다. `generateAndHold` 시점에 각 댓글/대댓글
후보 item에 `scheduledAt`(ISO Instant)을 `CandidateScheduleSupport.schedule`로
심어 두어, 관리자가 발행 전 릴리스 시각을 보고 수정할 수 있다.
`ScheduledPostPublisher`(매 분 cron)가 슬롯 도래 시 실제 글을 만들고, 저장해둔
후보를 `ThreadPlanGenerationService.persistResponse()`로 재생(replay)한다 —
`persistResponse`는 item에 `scheduledAt`이 있으면 그걸 쓰고, 없으면 기존
`schedule()` fallback. 발행 시점엔 LLM을 다시 부르지 않는다.

관리자 UI: `/admin/content` → **예약 홀딩** 탭. BE
`/api/admin/content/scheduled-posts`가 orchestrator `/admin/scheduled-posts`를
프록시한다. `SCHEDULED`만 PATCH(제목·본문·슬롯·후보)/DELETE(`CANCELLED`).
슬롯만 바꾸고 items를 안 보내면 후보 시각은 delta-shift.
어드민 `ThreadEditorDialog`는 저장 시점에 로드 기준 글 발행 예정 시각 대비
delta를 계산해 댓글·대댓글 `atLocal`에 일괄 적용한다 (키보드·피커 공통).

공개된 글의 미게시 COMMENT/REPLY는 orchestrator `ai_thread_plan_items`에 남는다.
`GET/PATCH /api/admin/content/posts/{id}/thread`가 게시 댓글과 예약 후보를 병합해
보여 주고, `pendingItems`로 본문·페르소나·`scheduledAt` 수정·취소를 프록시한다
(`/admin/thread-plan-items`).

```
env/scripts/nightly-ai-user-batch.sh (호스트 crontab 05 3 * * *, KST)
  ├─ provider(ai_post_bundle/human_post_plan/human_interaction) = CLAUDE
  ├─ POST /admin/trigger/generate-scheduled-posts?count=N&fromHour=8&toHour=22
  │    ├─ ActivityCurve.sampleFutureInstants(count=N)로 오늘 남은 활동 시간대에
  │    │  발행 슬롯 N개를 최소간격(기본 45분) 두고 샘플링
  │    └─ 페르소나별로 generateAndHold() 호출 → ai_scheduled_posts에 SCHEDULED로 저장
  ├─ 낮 동안 밀린 REQUESTED 스레드플랜(실사람 글 반응 등)도 이 창에서 같이 소진
  └─ provider = OFF (trap으로 스크립트 종료 방식과 무관하게 항상 보장)

ScheduledPostPublisher (cron AI_USER_SCHEDULED_POST_PUBLISHER_CRON, 기본 매 분)
  ├─ ScheduledPostLeaseService.claimDue()로 due 행 lease
  ├─ BackendBotClient.createPost — 진짜 글 생성
  ├─ candidates_json을 persistResponse()로 재생 (LLM 재호출 없음)
  └─ PUBLISHED로 갱신
```

`schedule_execution_paused`는 항상 `false`로 둬서 이미 만들어진 item의 게시는
낮 동안 계속된다. `ai_user_runtime.enabled`(LEGACY tick 킬스위치)는 이 파이프라인과
무관해서 건드리지 않는다.

스크립트 로그: `env/logs/nightly-ai-user-batch.log`(자체 타임스탬프) /
`env/logs/nightly-ai-user-batch.cron.log`(cron stdout/stderr).

### ActivityCurve — KST 시간대 활동 가중치

`OrchestratorProperties.ThreadPlan.kstHourlyHumanWeights`(기존 필드, 이전엔
`EffectiveExposureCalculator`의 노출시간 가중에만 쓰이고 평평한 기본값이라 사실상
미사용)를 재사용한다. 22:00 KST를 1.0으로, 02:00~05:00 KST를 0.05~0.08로 둔
시간별 가중치 — 출퇴근(07~09시)·점심(12~13시) 소피크, 22시 본피크, 새벽 저활동
구조만 반영한다.

**주의: 이 곡선은 손으로 작성한 근사값이며 실측 한국 휴대폰 사용 데이터에 기반하지
않았다.** 이 프로젝트 어디에도 그런 데이터/분석은 존재하지 않는다(2026-07-31 확인).
나중에 실제 데이터를 확보하기 전까지는 추측치로 취급할 것.

`ActivityCurve`(`ai-user/orchestrator/.../service/threadplan/ActivityCurve.java`) 제공 함수:

- `sampleFutureInstants(from, to, count, weights, minSpacing, rng)` — 곡선 가중
  샘플링 + 최소 간격 보장(양방향 보정). 새벽 배치의 글 슬롯 배정에 사용.
- `nextActiveHour(from, minWeight, weights)` — dead hour(가중치 < 임계값)에 걸리면
  다음 활성 시간대로 스냅. `ThreadPlanGenerationService.schedule()`의 댓글/대댓글
  경과-분 배열(기존 decay 구조 유지)이 새벽 트로프에 떨어지는 것 방지, 그리고
  `ThreadPlanPublisher`의 stampede 재분배에도 사용.
- `advanceByWeightedSeconds(from, targetSeconds, weights)` —
  `EffectiveExposureCalculator.weightedSeconds`의 역함수. 노출시간 예산 재계산용,
  dead-hour 회피와는 다른 목적(대기하며 누적 vs 즉시 건너뛰기)이라 혼동 금지.

### 2026-07-30~31 LEGACY → PLAN → 예약 파이프라인 전환 경위

- **2026-07-30**: PLAN 모드가 postId(VARCHAR) `Long.valueOf()` 파싱 버그로 깨져
  있어, LEGACY tick을 새벽에 압축 실행하는 임시방편을 썼다. 생성=발행이 분리되지
  않아 새 글 7개가 한꺼번에 몰리고 댓글이 하나도 안 붙는 문제로 이어졌다.
- **2026-07-31 오전**: postId 버그 수정(커밋 `1e9475cd`). **주의**: comment/reply
  ID(`post_comments.id`, `parent_comment_id`)는 실제 BIGINT라서 그쪽
  `Long.valueOf()`는 원래도 옳다 — postId만 String으로 고쳐야 하며 comment ID까지
  바꾸면 새 버그가 생긴다.
- **2026-07-31 낮**: PLAN 전환 후에도 `generateAndPublish()`는 즉시 발행이라 여전히
  "새벽에 만들면 새벽에 올라옴"이었다(사용자가 다시 발견). `generateAndHold` +
  `ai_scheduled_posts` + `ScheduledPostPublisher`로 생성/발행을 실제로 분리.
- **구현 중 발견한 추가 버그 2건**:
  1. `ScheduledPostPublisher`에 `claimDue`/`completePosted`/`releaseFailed`를
     `@Transactional`로 선언하고 같은 클래스 안에서 self-invocation으로 호출 —
     Spring 프록시 기반 AOP는 self-invocation을 가로채지 못해 트랜잭션이 조용히
     적용 안 됨(`lockDueItems`의 PESSIMISTIC_WRITE가 "no transaction in progress"로
     매 분 실패). `ThreadPlanPublisher`가 이미 lease 메서드를 별도 빈
     (`ThreadPlanItemLeaseService`)으로 분리해둔 것과 동일한 이유 — 새 코드도
     `ScheduledPostLeaseService`로 분리해 해결.
  2. 새벽 배치 트리거가 `category`를 빈 문자열/null로 넘겨 backend
     `createPost`가 `VALIDATION_ERROR`로 거부 — `ActionExecutor.topCategory()`와
     동일한 로직(persona 관심사 최고값, 없으면 "OTHER")으로 채우도록 수정.
- 실제 스모크 테스트로 생성→홀드→(강제 due 처리)→발행→댓글 재생까지 전 구간 확인
  완료(2026-07-31).

### 기존 글 재배치 (WO-RETIME-01, 2026-07-31)

전환 전 LEGACY 압축배치로 몰려 올라온 글 8개를 `env/scripts/retime-nightly-batch-posts.py`로
재배치했다(dry-run 기본, `--apply`로 실행, SQL은 `/tmp/retime-posts.sql`에 저장 —
`backdate-timeline.py`와 동일한 관례). 이미 지난 슬롯 배정 글은 `created_at`
delta-shift(글 삭제 없음), 아직 안 지난 슬롯 글은 `ai_scheduled_posts`로 이관 후
원본 삭제(발행은 `ScheduledPostPublisher`가 나중에 담당).

**실사람 데이터 제약**: 8개 중 2개 글에 실사람의 좋아요 1건·투표 1건이 섞여 있었다
(전부 봇으로 착각하면 안 됨). 이 2개는 반드시 "이미 지난 슬롯"으로만 배정해
글을 삭제하지 않고, 좋아요/투표 행의 시각도 글과 같은 delta만큼 같이 옮겼다
(`backdate-timeline.py`가 이미 쓰는 기법과 동일 — 콘텐츠·행위자는 그대로, 시각만
내부 일관성을 위해 이동).

**주의(재발 방지)**: 재배치 스크립트가 delta-shift한 댓글 결과를 검증할 때, 사전
조사 리포트가 알려준 "원래 시각"을 곧이곧대로 믿지 말 것 — 한 번은 조사 에이전트가
KST라고 보고한 시각이 실제로는 raw UTC 저장값을 그대로 옮긴 것이라(라벨링 오류)
delta 적용 결과가 이상해 보인 적이 있다. delta-shift 자체의 산술은 항상
검증 가능하다(같은 delta를 post/comment에 동일 적용하면 상대 간격은 반드시
보존된다) — 결과가 이상하면 델타 계산이 아니라 "이전 값이 무엇이었는지"에 대한
가정을 먼저 의심할 것. 또한 `mariadb -B`(batch 모드) 출력은 백슬래시/탭/개행을
자체적으로 이스케이프하므로, JSON 컬럼을 다시 읽어 검증할 땐 `--raw` 없이 조회한
결과를 그대로 `json.loads`하면 가짜 파싱 실패가 난다 — 반드시 `--raw`를 붙일 것.

### 번들 생성 지연 및 구성 최적화

실측 결과 글+최대 24개 후보를 한 번에 LLM 요청하는 구조화 생성이 5~10분 이상 걸릴
수 있음이 확인됐다. 대응책:

1. 타임아웃: `AI_USER_THREAD_PLAN_BUNDLE_TIMEOUT_MS` (기본 240000ms=240초)로 설정.
   `OrchestratorProperties.ThreadPlan.bundleTimeoutMs`에 대응.
2. 후보 풀 크기: `ai_user_generation_config.candidate_pool_size`를 24보다 작게
   (16 권장: 최상위 14 + 대댓글 2)으로 설정하면 생성 속도 개선.

## 9. 트러블슈팅

### 글이 하나도 안 올라올 때

- `.env.ai-user`의 `AI_USER_ENABLED=true`인지 먼저 확인
- PLAN 모드라면 `ai_user_generation_config.provider_*`가 낮에는 `OFF`가 정상이다(§8) —
  버그가 아니라 새벽 배치 설계다. 새 글이 전혀 없어야 정상인 건 아니고, 새 LLM
  job만 안 만들 뿐 이미 예약된 item 게시는 계속된다.
- `AI_USER_SCHEDULED_POST_PUBLISHER_ENABLED=true`인지 확인 — false면 `ai_scheduled_posts`에
  생성은 계속되지만 아무것도 발행되지 않는다
- `ai_scheduled_posts` 상태 분포 확인: `SELECT status, COUNT(*) FROM ai_scheduled_posts GROUP BY status;`
  — `PUBLISHING` 상태가 `lease_until`을 훨씬 지나서도 남아 있으면 발행 중 예외로
  lease가 안 풀린 것, orchestrator 로그에서 `Scheduled post publish failed` 검색
- LEGACY라면 `ai_user_runtime.enabled = 1`인지 확인
- orchestrator 로그에 `Daily global cap reached`가 있는지 확인

### learning이 예상치 않게 crawl할 때

- `AI_LEARNING_CRAWL_ENABLED=true`인지 확인
- 수동 실행이 아니라면 scheduler 로그에 등록 시각이 찍혔는지 확인

### 크롤이 조용히 멈췄을 때 (2026-06-24~07-30, 36일 무크롤 인시던트)

`AI_LEARNING_CRAWL_ENABLED=false`가 실수로 켜진 채 36일간 방치된 사고가 있었다.
`GET /crawl/log`는 능동 조회해야만 상태를 알 수 있어 아무도 눈치채지 못했다 —
**"조용히 멈출 수 있다"는 것 자체가 이 시스템의 구조적 위험**이다.

- 1차 확인: `.env.ai-user`의 `AI_LEARNING_CRAWL_ENABLED=true`인지
- 2차 확인: admin 대시보드의 크롤 신선도 배지(`GET /api/admin/crawl-status`,
  WO-CRAWL-01) — 24시간 내 성공 크롤이 0건이면 `stale` 경고가 뜬다. 이 배지가
  재발 방지 장치이므로, 배지 자체가 비정상(조회 오류)이면 그것부터 조사할 것
- natepan이 SUCCESS/FAILED를 같은 시각에 동시 기록하면 스케줄러 중복
  초기화(`init_scheduler()` 2회 호출 → 동시 크롤 → DB lock timeout 1205)
  의심 — `scheduler.py`의 `_scheduler` 싱글턴 가드가 이미 이를 막고 있으니,
  재발하면 그 가드가 우회됐는지부터 본다
- 후속 과제(WO-CRAWL-01 미착수분): 텔레그램 하트비트 알림 — 배지는 "들어가서
  봐야" 아는 수단이라 재발 가능성이 완전히 닫히지 않았다

### host에서 `localhost:8096`이 안 열릴 때

- compose 설계상 정상이다. orchestrator는 외부 공개 포트가 없다.

### dev에서 실사용자 로그인이 안 될 때

- 의도된 동작이다. prod mirrored user는 비식별화 + 비활성 상태로 저장된다.

### dev에서 ai_user_outbox "Unknown column" 에러

dev backend는 `ddl-auto: update` 모드이므로 JPA 엔티티에 선언되지 않은 컬럼은
자동 생성되지 않는다. `ai_user_outbox` 테이블이 backend 쓰기 전용 컬럼만 가지고
있으면 orchestrator(오케스트레이터)가 소비 전용 컬럼(published_at, lease_owner,
lease_until, attempt_count, last_error_code)을 찾지 못해 오류 난다.

**해결책**: prod의 `SHOW CREATE TABLE ai_user_outbox`와 비교해서 누락된 컬럼을
dev에 수동으로 `ALTER TABLE` 추가. 또한 `ai_user_generation_config` 테이블에서
`provider_ai_post_bundle`, `provider_human_post_plan`, `provider_human_interaction`
값이 유효한지 확인 — 유효값은 `CLAUDE`/`CODEX`/`OFF`뿐이다(`CLI`/`API` 아님).

### prod에서 `ai_user_generation_config` "Unknown column" 에러 (2026-07-31 실제 발생)

위 dev 케이스와 같은 종류의 문제가 prod에서도 실제로 터졌다: JPA 엔티티
(`AiUserGenerationConfig.java`)에 `provider_vote_like` 필드가 추가됐는데 대응
마이그레이션(`V90__ai_user_config_plan_only.sql`)이 아직 적용되지 않은 상태로
배포되면서, `ThreadPlanGenerationScheduler`를 포함해 이 테이블을 읽는 **모든**
스케줄러가 15초마다 예외를 던지며 멈췄다(orchestrator 전체 컨텐츠 파이프라인
정지). `V90`을 수동 적용(`docker exec ... mariadb < V90__*.sql`, idempotent라
안전)해서 해결.

**교훈**: 이 테이블은 backend가 소유하고 orchestrator는 읽기 전용으로 매핑한다
(§3 참조). 엔티티에 컬럼을 추가하는 커밋과 그 컬럼을 만드는 마이그레이션은
분리될 수 있지만, **마이그레이션이 실제로 적용(=backend 재시작으로 Flyway 실행,
또는 수동 적용)되기 전까지는 새 필드를 참조하는 어떤 쿼리도 prod에서 실행하면
안 된다.** 여러 세션이 동시에 이 저장소를 건드릴 때 특히 위험 — 한쪽이 엔티티만
먼저 커밋하면 다른 쪽 코드(이미 배포돼 있던 스케줄러 포함)가 즉시 깨진다.

### 댓글/대댓글 좋아요·조회수가 안 붙을 때

`PlanEngagementDispatcher`(§8, 5분 cron) 관련. `AI_USER_ENGAGEMENT_ENABLED=true`인지,
`ai_user_kill_switch`/`schedule_execution_paused`가 꺼져 있는지 확인.
`POST /admin/trigger/reconcile-engagement?dryRun=true`로 부족분이 실제로
계산되는지 먼저 확인하고, `dryRun=false`로 반영. 일부 좋아요만 반영되고 나머지가
`persona_action_log`에 `COMMENT_LIKE FAILED`(target_id가 댓글 id가 아니라
postId로 찍힘)로 남으면 봇 계정 JWT 발급 실패(`BotTokenCache`)가 원인인 경우가
많다 — `Bot login failed ... 429 Too Many Requests` 로그 확인. 리콘실러는
수렴형이라 다음 5분 주기에 저절로 재시도된다(자동 복구, 별도 조치 불필요).
