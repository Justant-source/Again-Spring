# 로컬 개발 실행

호스트에서 직접 BE · FE를 띄우고, DB만 컨테이너로 띄우는 표준 흐름.

## Source of truth

- `env/docker-compose.yml` (DB 단독)
- `backend/build.gradle.kts`, `backend/src/main/resources/application-dev.yml`
- `frontend/package.json`

## 사전 요구

- Java 21 (Eclipse Temurin 권장)
- Node.js 20+
- Docker (MariaDB 컨테이너용)
- Claude Code CLI (`claude` 명령) — 호스트에 1회 로그인 완료. 미설치 시 LLM 호출은 `MockLLMProvider`로 수동 전환 필요.

## 1. DB 시작

```bash
cd env
docker compose up -d              # mariadb only — host:3306
docker compose logs -f mariadb    # 부팅 로그 (healthcheck 통과 확인)
```

기본 자격증명 (`docker-compose.yml` 기본값):
- root: `changeme`
- user: `againspring` / `changeme`
- database: `againspring`

## 2. 백엔드

```bash
cd backend
./gradlew bootRun                 # localhost:8080
```

dev 프로파일이 자동 적용되어:
- Flyway disabled, ddl-auto=update (스키마 자동 진화)
- Swagger UI: http://localhost:8080/swagger-ui.html
- 로그 레벨: `com.againspring=DEBUG`

기본 환경변수가 application.yml에 inline됨 (DB_URL, DB_USER, DB_PASSWORD 모두 위 docker-compose 기본값과 일치). 별도 export 불필요.

다른 값이 필요하면 IDE 실행 구성에 환경변수 주입 또는 다음과 같이:

```bash
DB_PASSWORD=mypwd \
JWT_SECRET=local-dev-secret-32-chars-or-more-xxxxxxxxxxx \
CLAUDE_BIN=/usr/local/bin/claude \
./gradlew bootRun
```

## 3. 프론트엔드

```bash
cd frontend
npm install                       # 최초 1회
npm run dev                       # localhost:3000 (MSW 자동 활성)
```

기본 동작:
- Next.js dev 서버: http://localhost:3000
- MSW (`mocks/browser.ts`)가 클라이언트 사이드에서 핸들러 등록 → BE 미기동 상태로도 UI 흐름 검증 가능
- TypeScript strict mode + ESLint 활성

## 4. 헬스 체크

```bash
curl http://localhost:8080/api/health        # BE liveness
curl http://localhost:8080/actuator/health   # Spring Actuator
curl http://localhost:3000                   # FE
```

## 일반 개발 명령

### 백엔드

```bash
cd backend
./gradlew test                    # 전체 테스트
./gradlew test --tests "*Sanitizer*"
./gradlew bootJar                 # 배포용 jar
```

### 프론트엔드

```bash
cd frontend
npm run dev                       # 개발 서버
npm run build                     # 프로덕션 빌드
npm run start                     # 프로덕션 서버
npm run lint                      # ESLint
```

## DB 접속 (외부 클라이언트)

```bash
# CLI
mariadb -h localhost -P 3306 -u againspring -p againspring
# pwd: changeme

# DBeaver / IntelliJ DataGrip
host: localhost / port: 3306 / db: againspring / user: againspring / pwd: changeme
```

dev 서버 컨테이너의 DB는 호스트 포트 `3309`에서 접근 가능 (`docker-compose.dev.yml`).

## 트러블슈팅

| 증상 | 원인 / 조치 |
|---|---|
| `bootRun` 시작 시 DB connection refused | `docker compose up -d` 먼저 실행 + healthcheck 대기 |
| Flyway migration 충돌 | dev 프로파일은 Flyway disabled. prod 마이그레이션 검증은 별도 통합 테스트 사용 |
| `claude` 명령 없음 → LLM 호출 timeout | `npm install -g @anthropic-ai/claude-code` + `claude` 1회 로그인. 또는 `LLM_PROVIDER=mock`으로 환경변수 변경 |
| 포트 3306 충돌 | 이미 다른 mysql/mariadb 가동 중 — `docker ps`로 확인 후 정지 |
| Frontend가 BE에 못 붙음 | MSW가 dev 모드에서 자동 활성. BE를 같이 띄울 거면 `mocks/browser.ts` 우회 또는 MSW 핸들러 미정의 경로만 BE로 fallthrough |
