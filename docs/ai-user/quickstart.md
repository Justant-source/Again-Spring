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

## 4. 실제 tick 켜기

하드 게이트와 DB runtime row를 둘 다 맞춰야 한다.

```bash
grep '^AI_USER_ENABLED=' /home/justant/Data/Again-Spring/env/.env.ai-user

docker exec -it againspring-mariadb-prod mariadb \
  -u againspring -p'<prod-db-password>' againspring \
  -e "UPDATE ai_user_runtime SET enabled = 1 WHERE id = 1;"
```

상태 확인:

```bash
docker exec -it againspring-mariadb-prod mariadb \
  -u againspring -p'<prod-db-password>' againspring \
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
- `AI_LEARNING_CRAWL_ENABLED=false`면 learning의 일일 작업이 등록되지 않는다.
- learning API만 host에서 직접 테스트 가능하다.
