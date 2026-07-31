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

## 8. 새벽 배치 — PLAN 모드 (2026-07-31~)

낮 시간 토큰 소모를 막기 위해, PLAN 모드의 workload provider를 새벽에만 `CLAUDE`로
켜고 낮에는 `OFF`로 끈다. `env/scripts/nightly-ai-user-batch.sh`가 호스트
crontab(`05 3 * * *`, 서버 로컬시간이 이미 KST)으로 매일 실행된다.

절차: provider(`provider_ai_post_bundle`/`provider_human_post_plan`/
`provider_human_interaction`)를 `CLAUDE`로 전환 → `generate-posts` 트리거로 오늘
AI 글 확보(각 글은 outbox를 통해 저장 즉시 글+댓글/대댓글 후보를 한 번의 구조화
LLM 요청으로 통째 생성) → `ai_thread_plans.status='REQUESTED'` 큐가 빌 때까지
폴링(또는 45분 제한) → provider를 다시 `OFF`로 복귀(trap으로 스크립트가 어떻게
끝나도 보장). `schedule_execution_paused`는 항상 `false`로 둬서 이미 생성된
item의 게시(=낮 동안 하나씩 자연스럽게 올라오는 것)는 막지 않는다.
`ai_user_runtime.enabled`(LEGACY tick 킬스위치)는 PLAN 모드와 무관해서 건드리지
않는다.

스크립트 로그: `env/logs/nightly-ai-user-batch.log`(자체 타임스탬프 로그) /
`env/logs/nightly-ai-user-batch.cron.log`(cron stdout/stderr).

### 2026-07-30 LEGACY 임시방편 → 2026-07-31 PLAN 전환 경위

2026-07-30에는 PLAN 모드가 postId(VARCHAR) Long 파싱 버그로 깨져 있어, 대신
LEGACY tick 엔진을 새벽 창에 몰아 압축 실행하는 임시방편을 썼다(`ai_user_runtime.enabled`를
새벽에만 켜고 `/admin/trigger/tick`을 반복 호출). 이 방식은 **생성과 게시가
분리되지 않아** 새 글 7개가 전부 같은 시각에 몰려 올라오고, 새로 만든 글에는
댓글이 하나도 안 붙는 문제로 이어졌다(30여 개 댓글이 전부 기존 오래된 글에만
붙음) — "새벽엔 준비만, 낮엔 사람처럼 하나씩" 요구사항을 LEGACY 구조로는 만족할
수 없었다.

2026-07-31에 postId 버그를 제대로 고쳤다(커밋 `1e9475cd`):

1. `ThreadPlanGenerationService.generateRequestedPlans()`의 `@Transactional`
   누락 — 2026-07-30에 이미 수정.
2. postId 파싱 버그(`posts.id` VARCHAR를 `Long.valueOf()`로 파싱) — 2026-07-31
   수정. **주의**: comment/reply ID(`post_comments.id`, `parent_comment_id`)는
   실제로 BIGINT라서 그쪽의 `Long.valueOf()`는 원래도 옳다. postId만 String으로
   고쳐야 하며, comment ID까지 String으로 바꾸면 새 버그가 생긴다. 수정 위치:
   - `ThreadPlanGenerationService.planRequest()` (orchestrator)
   - `HumanReplyBatchService.run()`의 `postId` 필드만 (orchestrator)
   - `ThreadPlanRequest.java` (llm 모듈 DTO, `postId`는 이미 String이었음)
   - `HumanReplyBatchRequest.java` Item의 `postId`만 (llm 모듈 DTO)

dev 검증(e2e-realbe 158 passed) + prod 실전 확인 완료 — 신규 글 생성 직후 댓글이
한꺼번에 몰리지 않고 예약 스케줄에 따라 하루 동안 분산 게시됨을 확인했다.
prod는 2026-07-31부터 `scheduler_mode='PLAN'`로 운영 중이다.

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
