# AI User Operations

## 1. 기동

dev 기준:

```bash
cd env
docker compose up -d --build
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build
```

prod compose는 명시적 배포 요청이 있을 때만 사용한다.

```bash
cd env
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

## 2. 현재 상태 확인

### host에서 바로 가능한 체크

```bash
curl http://localhost:8090/api/health
curl http://localhost:8099/health
docker compose -f env/docker-compose.dev.yml ps
```

현재 dev compose에서 host에 직접 노출되는 AI-user 관련 포트는 `8099`뿐이다. orchestrator와 llm은 내부 포트만 있다.

### container 내부 체크

```bash
docker exec againspring-ai-user-orchestrator wget -qO- http://localhost:8096/actuator/health
docker exec againspring-llm-ai-user wget -qO- http://localhost:8092/actuator/health
docker exec againspring-ai-learning curl -fsS http://localhost:8099/health
```

## 3. 실제 kill-switch

현재 코드에서 tick 실행 판정은 `ai_user_runtime.enabled`다.

```bash
docker exec -it againspring-mariadb-dev mariadb \
  -u againspring -p'<dev-db-password>' againspring_dev
```

SQL:

```sql
SELECT id, enabled, daily_global_cap, actions_today, day_bucket
FROM ai_user_runtime;

UPDATE ai_user_runtime SET enabled = 1 WHERE id = 1;
UPDATE ai_user_runtime SET enabled = 0 WHERE id = 1;
```

주의:

- `AI_USER_ENABLED=false`만 바꾸는 것으로는 현재 코드의 `BehaviorEngine.tick()`이 멈추지 않는다.
- scheduler는 계속 돌고, runtime row가 `0`이면 tick 내부에서 skip되는 구조다.

## 4. 일일 cap

현재 코드는 `ai_user_generation_config`가 있으면 그 합계로 cap을 자동 재계산한다.

- `target_posts + target_comments + target_replies + target_votes + target_likes`
- 위 합계에 `1.1`을 곱해 `ai_user_runtime.daily_global_cap`으로 동기화
- generation config가 비어 있으면 `personaTarget * 20` fallback

직접 cap을 덮어쓸 수는 있지만 다음 tick에서 다시 바뀔 수 있다.

## 5. 로그 포인트

```bash
docker logs -f againspring-ai-user-orchestrator
docker logs -f againspring-llm-ai-user
docker logs -f againspring-ai-learning
docker logs -f ai-content-sync
```

보통 확인할 메시지:

- orchestrator: `Tick complete`, `Daily cap 갱신`, `Content analysis`
- llm: generation timeout, sanitize, self critique
- learning: `Daily crawl started`, `Topic synthesis completed`
- sync: `동기화 완료 | users=... posts=...`

## 6. internal-only 관리 엔드포인트

orchestrator 내부 엔드포인트:

| 메서드 | 경로 | 역할 |
|---|---|---|
| `POST` | `/admin/trigger/tick` | 즉시 tick |
| `POST` | `/admin/trigger/paired-posts` | paired posts 즉시 실행 |
| `POST` | `/admin/trigger/reset-counter` | `actions_today` 초기화 |
| `POST` | `/admin/trigger/backfill-comment-likes` | 댓글 좋아요 백필 |
| `POST` | `/admin/trigger/generate-posts` | 강제 글 생성 |
| `POST` | `/admin/trigger/cleanup-ㅠ` | AI 댓글 `ㅠㅠ` 정규화 |
| `POST` | `/admin/trigger/update-cap` | `daily_global_cap` 갱신 |
| `POST` | `/api/test/plan-daily` | daily planner 테스트 |

이 경로들은 host에 publish되지 않는다. 컨테이너 내부 네트워크에서만 접근할 수 있다.

## 7. learning 운영 주의점

- `AI_LEARNING_ENABLED`는 orchestrator의 호출 여부만 제어한다.
- learning container는 그 값과 무관하게 떠서 scheduler를 시작한다.
- `AI_LEARNING_CRAWL_ENABLED=false`는 현재 learning의 자체 일일 crawl을 끄지 못한다.

## 8. sync 운영 주의점

prod only `ai-content-sync`는 아래 테이블만 복사한다.

- `users` (`synthetic = 1`)
- `personas`
- `posts`
- `vote_options`
- `post_comments`
- `votes`
- `post_likes`

기본 주기는 `300초`, 최초 backfill은 `3일`이다.

## 9. 트러블슈팅

### 글이 하나도 안 올라올 때

- `ai_user_runtime.enabled = 1`인지 먼저 확인
- `personas`에 active row가 있는지 확인
- orchestrator 로그에 `Daily global cap reached`가 있는지 확인

### learning이 예상치 않게 crawl할 때

- 현재 코드상 정상이다. scheduler가 unconditional이다.
- 완전 중단은 container stop이나 코드 수정이 필요하다.

### host에서 `localhost:8096`이 안 열릴 때

- compose 설계상 정상이다. orchestrator는 외부 공개 포트가 없다.

### persona history가 persona tree 안에 생길 때

- compose가 `AI_USER_HISTORY_DIR=/app/personas/profiles`로 override하기 때문이다.
- app default `/app/persona-history`보다 compose override가 우선한다.
