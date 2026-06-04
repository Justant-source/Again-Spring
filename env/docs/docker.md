# Docker 구성

## Source of truth

- `env/docker-compose.yml`
- `env/docker-compose.dev.yml`
- `env/docker-compose.prod.yml`
- `backend/Dockerfile`
- `llm-worker/Dockerfile`
- `frontend/Dockerfile`

## 3개 스택 개요

### 1. local (`docker-compose.yml`)

MariaDB 단독. 로컬 머신에서 `./gradlew bootRun` + `npm run dev`로 BE/FE를 직접 띄울 때 DB만 컨테이너로 사용.

- 서비스: `mariadb` (image: `mariadb:lts`)
- 컨테이너: `againspring-mariadb`
- 포트: `3306:3306`
- 볼륨: `mariadb_data`
- 네트워크: `againspring`
- env 기본값: `MARIADB_ROOT_PASSWORD=changeme`, `MARIADB_DATABASE=againspring`, `MARIADB_USER=againspring`, `MARIADB_PASSWORD=changeme`

### 2. dev (`docker-compose.dev.yml`)

`dev.againspring.net`에서 동작하는 풀 스택. 실 사용자에게 노출되는 dev 환경.

- project name: `againspring-dev`
- 네트워크: `againspring-dev` (bridge)

| 서비스 | 컨테이너 | 이미지 | 포트 | 의존 |
|---|---|---|---|---|
| `mariadb-dev` | `againspring-mariadb-dev` | `mariadb:lts` | `3309:3306` (호스트 접근용) | — |
| `llm-dev` | `againspring-llm-dev` | build `../llm-worker` | internal (8090) | — |
| `llm-ai-user-dev` | `againspring-llm-ai-user-dev` | build `../llm-ai-user` | internal (8092) | — |
| `backend-dev` | `againspring-backend-dev` | build `../backend` | internal | `mariadb-dev` (healthy), `llm-dev` (healthy) |
| `ai-user-orchestrator-dev` | `againspring-ai-user-orchestrator-dev` | build `../ai-user-orchestrator` | internal (8096) | `mariadb-dev` (healthy), `llm-ai-user-dev` (healthy), `backend-dev` (started) |
| `frontend-dev` | `againspring-frontend-dev` | build `../frontend` | internal | `backend-dev` |
| `marketing-renderer-dev` | `againspring-marketing-renderer-dev` | build `../marketing/renderer` | internal (9000) | `backend-dev` |
| `social-poster-dev` | `againspring-social-poster-dev` | build `../marketing/social-poster` | internal (9100) | `backend-dev` |
| `nginx-dev` | `againspring-nginx-dev` | `nginx:alpine` | `8090:80` | `frontend-dev`, `backend-dev` |

llm-dev bind mount: `${CLAUDE_HOST_CONFIG_DIR:-/home/justant/.claude}:/root/.claude` (Claude CLI 세션 공유 — backend가 아닌 llm-worker에 마운트)

`llm-ai-user-dev` also shares the same `~/.claude` bind mount — both workers run `--no-session-persistence`, low concurrent volume in dev is acceptable.

`SPRING_PROFILES_ACTIVE=dev` 활성화 → Flyway disabled, ddl-auto=update, Swagger UI on.

**dev 전용 추가 서비스:**
- `marketing-renderer-dev` (포트 9000 내부): Node.js + Playwright + Sharp. 마케팅 콘텐츠용 PNG 렌더링. 엔드포인트: `/render`, `/render-chat`, `/render-quote`, `/render-card-news`, `/render-report-summary`, `/render-metaphor-card`.
- `social-poster-dev` (포트 9100 내부): Node.js + Playwright. X·Instagram 자동 포스팅. `src/` 디렉토리가 호스트에서 bind mount되어 nodemon으로 핫리로드. 셀렉터 파일 수정 → `docker compose restart`만으로 반영.

### 3. prod (`docker-compose.prod.yml`)

`againspring.net` / `www.againspring.net`에 노출되는 운영 환경.

- project name: `againspring-prod`
- 네트워크: `againspring-prod` (bridge)
- 모든 서비스 `restart: always`

| 서비스 | 컨테이너 | 메모리 limit/reservation | 외부 노출 |
|---|---|---|---|
| `mariadb-prod` | `againspring-mariadb-prod` | 2G / 1G | 없음 (internal) |
| `llm-prod` | `againspring-llm-prod` | 4G / 2G | 없음 (internal) |
| `backend-prod` | `againspring-backend-prod` | 3G / 1G | 없음 |
| `frontend-prod` | `againspring-frontend-prod` | 512M / 256M | 없음 |
| `nginx-prod` | `againspring-nginx-prod` | — | `8091:80` |

llm-prod bind mount: `${CLAUDE_HOST_CONFIG_DIR:-/root/.claude}:/root/.claude` (4G 한도: 100 동시 Node CLI 메모리)

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

## prod 미러링 (나중에)

`docker-compose.prod.yml`에 두 블록을 `-prod` 접미사로 복사:
- `llm-ai-user-prod`: 동일 패턴, `AI_USER_ENABLED=false`로 시작, `CLAUDE_HOST_CONFIG_DIR` prod 경로
- `ai-user-orchestrator-prod`: 동일 패턴, `AI_USER_ENABLED=false`로 시작
