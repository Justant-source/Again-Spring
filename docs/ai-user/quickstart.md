# AI User Quickstart

공통 ai-user 스택을 최소한으로 띄우는 절차다.

## 1. 스택 기동

```bash
cd /home/justant/Data/Again-Spring/env
docker compose up -d --build
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
bash ./rebuild-stacks.sh ai-user
```

## 2. 살아 있는지 확인

host에서 바로 확인:

```bash
curl http://localhost:8090/api/health
curl http://localhost:8091/api/health
curl http://localhost:8099/health
docker compose -f docker-compose.ai-user.yml --env-file .env.ai-user ps
```

container 내부 확인:

```bash
docker exec againspring-ai-user-orchestrator wget -qO- http://localhost:8096/actuator/health
docker exec againspring-llm-ai-user wget -qO- http://localhost:8092/actuator/health
```

## 3. persona corpus 확인

```bash
find /home/justant/Data/Again-Spring/ai-user/docs/personas/profiles -mindepth 1 -maxdepth 1 -type d | wc -l
```

현재 저장소 스냅샷은 `115`개 디렉토리다. compose target `50`과 다를 수 있다.

## 4. PLAN 실행 활성화 전 확인

배포 직후에는 PLAN workload provider를 `OFF`로 두는 것이 안전하다. 실제 콘텐츠 생성은 운영 화면에서 `scheduler_mode=PLAN`, 필요한 workload provider(`CLAUDE` 또는 `CODEX`), publisher/batch gate를 명시적으로 설정하고 승인한 뒤에만 활성화한다.

하드 게이트와 DB runtime row는 실제 예약 게시에도 모두 적용된다.

```bash
grep '^AI_USER_ENABLED=' /home/justant/Data/Again-Spring/env/.env.ai-user

docker exec -it againspring-mariadb-prod mariadb \
  -u againspring -p'<prod-db-password>' againspring_prod \
  -e "UPDATE ai_user_runtime SET enabled = 1 WHERE id = 1;"
```

상태 확인:

```bash
docker exec -it againspring-mariadb-prod mariadb \
  -u againspring -p'<prod-db-password>' againspring_prod \
  -e "SELECT id, enabled, daily_global_cap, actions_today FROM ai_user_runtime;"
```

## 5. 로그 보기

```bash
docker logs -f againspring-ai-user-orchestrator
docker logs -f againspring-llm-ai-user
docker logs -f againspring-ai-learning
docker logs -f againspring-prod-dev-sync
```

## 6. 현재 구조에서 자주 헷갈리는 점

- ai-user 런타임은 dev/prod가 따로 아니라 공통 스택 하나다.
- `AI_USER_ENABLED=false`면 scheduler 자체가 skip된다.
- PLAN은 `AI_USER_THREAD_PLAN_ENABLED`, publisher/batch gate 및 admin provider가 함께 켜져야 동작한다.
- Codex/Claude는 API key가 아니라 호스트 로그인 세션 mount를 사용한다. 실제 생성 smoke test는 운영 승인된 1회 요청으로만 수행한다.
- `PAIRED_POST_ENABLED=false`가 기본이다.
- `AI_LEARNING_CRAWL_ENABLED=false`면 learning의 일일 작업이 등록되지 않는다.
- learning API만 host에서 직접 테스트 가능하다.
