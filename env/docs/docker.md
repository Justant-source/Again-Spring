# Docker 구성

## Source of truth

- `env/docker-compose.yml`
- `env/docker-compose.dev.yml`
- `env/docker-compose.prod.yml`
- `backend/Dockerfile`
- `llm-worker/Dockerfile`
- `frontend/Dockerfile`

## 3개 스택 개요

### 1. base (`docker-compose.yml`) — dev/prod 공유

MariaDB + **공유 LLM 워커**. dev와 prod가 동일 `againspring-llm` 컨테이너를 사용.

- project name: `againspring`
- 네트워크: `againspring` (bridge, `name: againspring` explicit)

| 서비스 | 컨테이너 | 이미지 | 포트 | 역할 |
|---|---|---|---|---|
| `mariadb` | `againspring-mariadb` | `mariadb:lts` | `3306:3306` | 로컬 직접 실행 전용 DB |
| `againspring-llm` | `againspring-llm` | build `../llm-worker` | internal (8090) | dev·prod 공유 LLM 워커 |

llm bind mount: `${CLAUDE_HOST_CONFIG_DIR:-/home/justant/.claude}:/root/.claude` (Claude CLI 세션 공유)

**시작 순서**: base 스택 먼저 → dev/prod 스택. `backend-dev`·`backend-prod`가 `againspring` external network를 통해 `againspring-llm:8090`에 접근.

### 2. dev (`docker-compose.dev.yml`)

`dev.againspring.net`에서 동작하는 풀 스택. 실 사용자에게 노출되는 dev 환경.

- project name: `againspring-dev`
- 네트워크: `againspring-dev` (bridge) + `againspring` (external, base 스택)

| 서비스 | 컨테이너 | 이미지 | 포트 | 의존 |
|---|---|---|---|---|
| `mariadb-dev` | `againspring-mariadb-dev` | `mariadb:lts` | `3309:3306` (호스트 접근용) | — |
| `llm-ai-user` | `againspring-llm-ai-user` | build `../ai-user/llm` | internal (8092) | — |
| `ai-user-orchestrator` | `againspring-ai-user-orchestrator` | build `../ai-user/orchestrator` | internal (8096) | `mariadb-dev` (healthy), `llm-ai-user` (healthy), `backend-dev` (started) |
| `ai-learning` | `againspring-ai-learning` | build `../ai-user/learning` | `8099:8099` | `mariadb-dev` (healthy) |
| `backend-dev` | `againspring-backend-dev` | build `../backend` | internal | `mariadb-dev` (healthy) |
| `frontend-dev` | `againspring-frontend-dev` | build `../frontend` | internal | `backend-dev` |
| `marketing-renderer-dev` | `againspring-marketing-renderer-dev` | build `../marketing/renderer` | internal (9000) | — |
| `social-poster-dev` | `againspring-social-poster-dev` | build `../marketing/social-poster` | internal (9100) | — |
| `nginx-dev` | `againspring-nginx-dev` | `nginx:alpine` | `8090:80` | `frontend-dev`, `backend-dev` |

`backend-dev`는 `againspring-dev`·`againspring` 두 네트워크에 연결 → `againspring-llm:8090` 접근.

`SPRING_PROFILES_ACTIVE=dev` 활성화 → Flyway disabled, ddl-auto=update, Swagger UI on.

**dev 전용 추가 서비스:**
- `marketing-renderer-dev` (포트 9000 내부): Node.js + Playwright + Sharp. 마케팅 콘텐츠용 PNG 렌더링.
- `social-poster-dev` (포트 9100 내부): Node.js + Playwright. X·Instagram 자동 포스팅. `src/` 디렉토리가 호스트에서 bind mount되어 nodemon으로 핫리로드.

### 3. prod (`docker-compose.prod.yml`)

`againspring.net` / `www.againspring.net`에 노출되는 운영 환경.

- project name: `againspring-prod`
- 네트워크: `againspring-prod` (bridge) + `againspring` (external, base 스택)
- 모든 서비스 `restart: always`

| 서비스 | 컨테이너 | 메모리 limit/reservation | 외부 노출 |
|---|---|---|---|
| `mariadb-prod` | `againspring-mariadb-prod` | 2G / 1G | 없음 (internal) |
| `backend-prod` | `againspring-backend-prod` | 3G / 1G | 없음 |
| `frontend-prod` | `againspring-frontend-prod` | 512M / 256M | 없음 |
| `nginx-prod` | `againspring-nginx-prod` | — | `8091:80` |

`backend-prod`는 `againspring-prod`·`againspring` 두 네트워크에 연결 → 공유 `againspring-llm:8090` 사용.

`SPRING_PROFILES_ACTIVE=prod` → Flyway 활성, ddl-auto=validate, Swagger 비활성, 모든 env 필수.

## healthcheck

MariaDB (dev/prod):
```yaml
test: ["CMD", "healthcheck.sh", "--su-mysql", "--connect", "--innodb_initialized"]
interval: 10s
timeout: 3s
retries: 6
start_period: 30s
```
backend는 `depends_on.mariadb-*.condition: service_healthy`로 DB 정상화까지 대기.

## Dockerfile 요약

### backend (`backend/Dockerfile`)

multi-stage:
1. **build**: `eclipse-temurin:21-jdk-alpine` → `./gradlew bootJar`
2. **runtime**: `eclipse-temurin:21-jre-alpine` (Node.js / Claude CLI 미포함)
3. ENTRYPOINT: `java -jar app.jar`

Claude CLI는 `llm-worker`로 이동. backend는 `RemoteLlmProvider` HTTP 클라이언트만 실행. 긴급 롤백 시 `LLM_PROVIDER=claude-code`로 전환 + Dockerfile revert.

### llm-worker (`llm-worker/Dockerfile`)

multi-stage:
1. **build**: `eclipse-temurin:21-jdk-alpine` → `./gradlew bootJar`
2. **runtime**: `eclipse-temurin:21-jre-alpine` + `nodejs npm` 설치 + `npm install -g @anthropic-ai/claude-code`
3. EXPOSE 8090, ENTRYPOINT: `java -jar app.jar`

`ClaudeCliInvoker`가 `claude --print --strict-mcp-config --no-session-persistence --model ... --system-prompt ...` ProcessBuilder 호출. `~/.claude` bind mount로 OAuth 인증 공유.

### frontend (`frontend/Dockerfile`)

multi-stage:
1. **deps**: `node:20-alpine` + `npm ci --omit=dev`
2. **build**: `node:20-alpine` + ARG `NEXT_PUBLIC_*` (build-time injection) + `npm run build`
3. **runtime**: `node:20-alpine`, non-root `nextjs:nextjs`, `npm start` (`NODE_ENV=production`)

build-time ARG: `NEXT_PUBLIC_APP_URL`, `NEXT_PUBLIC_GOOGLE_CLIENT_ID`, `NEXT_PUBLIC_KAKAO_CLIENT_ID`, `NEXT_PUBLIC_NAVER_CLIENT_ID` (Next.js는 빌드 시 정적 인라인).

## 자주 쓰는 명령

```bash
cd env

# dev 시작 (빌드 포함)
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build

# 상태
docker compose -f docker-compose.dev.yml ps

# 로그 (전체 스트리밍)
docker compose -f docker-compose.dev.yml logs -f

# 특정 서비스만
docker compose -f docker-compose.dev.yml logs -f backend-dev

# 정지
docker compose -f docker-compose.dev.yml down

# 정지 + 볼륨 삭제 (데이터 폐기)
docker compose -f docker-compose.dev.yml down -v
```

prod는 동일하지만 `-f docker-compose.prod.yml --env-file .env.prod`. **명시적 지시 시에만 실행**.

## prod AI 유저 확장 (나중에)

`ai-user-orchestrator`·`ai-learning`은 현재 dev 전용. prod에도 확장할 경우 prod 스택에 서비스 추가:
- `ai-user-orchestrator` (prod DB 연결), `AI_USER_ENABLED=false`로 시작
- `againspring-llm-ai-user`는 이미 base compose로 이동 가능
