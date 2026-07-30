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

## 8. 트러블슈팅

### 글이 하나도 안 올라올 때

- `.env.ai-user`의 `AI_USER_ENABLED=true`인지 먼저 확인
- prod DB `ai_user_runtime.enabled = 1`인지 확인
- orchestrator 로그에 `Daily global cap reached`가 있는지 확인

### learning이 예상치 않게 crawl할 때

- `AI_LEARNING_CRAWL_ENABLED=true`인지 확인
- 수동 실행이 아니라면 scheduler 로그에 등록 시각이 찍혔는지 확인

### host에서 `localhost:8096`이 안 열릴 때

- compose 설계상 정상이다. orchestrator는 외부 공개 포트가 없다.

### dev에서 실사용자 로그인이 안 될 때

- 의도된 동작이다. prod mirrored user는 비식별화 + 비활성 상태로 저장된다.
