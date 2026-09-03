# Docker 구성

## Source of truth

- `env/docker-compose.yml`
- `env/docker-compose.dev.yml`
- `env/docker-compose.prod.yml`
- `env/docker-compose.ai-user.yml`
- `backend/Dockerfile`
- `llm-worker/Dockerfile`
- `frontend/Dockerfile`

## 4개 스택 개요

### 1. base (`docker-compose.yml`)

dev·prod 공통 기반 스택이다.

- project name: `againspring`
- 네트워크: `againspring`

| 서비스 | 컨테이너 | 포트 | 역할 |
|---|---|---|---|
| `mariadb` | `againspring-mariadb` | `3306:3306` | 로컬 직접 실행 전용 DB |
| `againspring-llm` | `againspring-llm` | internal `8090` | dev·prod 공유 LLM 워커 |

### 2. dev (`docker-compose.dev.yml`)

dev 웹/DB 스택이다. ai-user 런타임은 포함하지 않는다.

- project name: `againspring-dev`
- 네트워크: `againspring-dev` + external `againspring`

| 서비스 | 컨테이너 | 포트 | 역할 |
|---|---|---|---|
| `mariadb-dev` | `againspring-mariadb-dev` | `3309:3306` | dev DB |
| `backend-dev` | `againspring-backend-dev` | internal `8080` | dev API |
| `frontend-dev` | `againspring-frontend-dev` | internal `3000` | dev UI |
| `nginx-dev` | `againspring-nginx-dev` | `8090:80` | dev 진입점 |

`backend-dev`는 다음 shared 서비스 URL을 기본으로 가진다.

- `AI_LEARNING_URL=http://againspring-ai-learning:8099`
- `AI_USER_LLM_URL=http://againspring-llm-ai-user:8092`
- `AI_USER_ORCHESTRATOR_URL=http://againspring-ai-user-orchestrator:8096`

### 3. prod (`docker-compose.prod.yml`)

prod 웹/DB 스택이다. ai-user 런타임은 포함하지 않는다.

- project name: `againspring-prod`
- 네트워크: `againspring-prod` + external `againspring`

| 서비스 | 컨테이너 | 포트 | 역할 |
|---|---|---|---|
| `mariadb-prod` | `againspring-mariadb-prod` | internal only | prod DB |
| `backend-prod` | `againspring-backend-prod` | internal `8080` | prod API |
| `frontend-prod` | `againspring-frontend-prod` | internal `3000` | prod UI |
| `nginx-prod` | `againspring-nginx-prod` | `8091:80` | prod 진입점 |

`backend-prod`도 dev와 동일한 shared ai-user URL을 사용한다.

### 4. shared ai-user (`docker-compose.ai-user.yml`)

공통 ai-user 런타임이다.

- project name: `againspring-ai-user`
- 네트워크:
  - `againspring`
  - `againspring-prod`
  - `againspring-dev` (`prod-dev-sync`만 사용)

| 서비스 | 컨테이너 | 포트 | 역할 |
|---|---|---|---|
| `llm-ai-user` | `againspring-llm-ai-user` | internal `8092` | AI-user 생성 워커 |
| `ai-learning` | `againspring-ai-learning` | `8099:8099` | learning API + scheduler |
| `ai-user-orchestrator` | `againspring-ai-user-orchestrator` | internal `8096` | prod 행동 오케스트레이션 |
| `prod-dev-sync` | `againspring-prod-dev-sync` | none | prod→dev 일일 비식별 동기화 |

운영 원칙:

- orchestrator 기본 대상은 `backend-prod`와 `mariadb-prod`
- orchestrator는 `AI_USER_ENV`로 자기 환경을 검증하고 불일치 시 기동하지 않는다
- dev DB 직접 쓰기는 `prod-dev-sync`를 통한 동기화만 허용

## 시작 순서

```bash
cd env
docker compose up -d --build
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
docker compose -f docker-compose.ai-user.yml --env-file .env.ai-user up -d --build
```

shared ai-user를 쓰려면 dev/prod 네트워크가 먼저 존재해야 한다.

## healthcheck

MariaDB:

```yaml
test: ["CMD", "healthcheck.sh", "--su-mysql", "--connect", "--innodb_initialized"]
interval: 10s
timeout: 3s
retries: 6
start_period: 30s
```

추가 healthcheck:

- `againspring-llm` → `/actuator/health`
- `againspring-llm-ai-user` → `/actuator/health`
- `againspring-ai-user-orchestrator` → `/actuator/health`
- `againspring-ai-learning` → `/health`

## Dockerfile 요약

### backend (`backend/Dockerfile`)

- build: `eclipse-temurin:21-jdk-alpine`
- runtime: `eclipse-temurin:21-jre-alpine`
- 역할: API 서버, DB/LLM 클라이언트

### llm-worker (`llm-worker/Dockerfile`)

- runtime에 `nodejs`, `npm`, `@anthropic-ai/claude-code` 포함
- `CLAUDE_HOST_CONFIG_DIR:/home/justant/.claude` bind mount 사용

### frontend (`frontend/Dockerfile`)

- Next.js production build
- `NEXT_PUBLIC_*` 값은 빌드 시 정적 인라인

## 자주 쓰는 명령

```bash
cd env

bash ./rebuild-stacks.sh ai-user
bash ./rebuild-stacks.sh --build-only ai-user
bash ./rebuild-stacks.sh base dev prod ai-user

docker compose up -d --build
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
docker compose -f docker-compose.ai-user.yml --env-file .env.ai-user up -d --build

docker compose -f docker-compose.ai-user.yml --env-file .env.ai-user ps
docker compose -f docker-compose.ai-user.yml --env-file .env.ai-user logs -f ai-user-orchestrator
docker compose -f docker-compose.ai-user.yml --env-file .env.ai-user down
```

스크립트 규칙:

- 기본 대상: `ai-user`
- 기본 동작: `up -d --build`
- `--build-only`는 빌드만 수행하고, real env 파일이 없으면 `*.example`을 사용한다.
