# 다시봄 인프라

## 환경 구분

| 환경 | 도메인 | compose 파일 | 호스트 포트 |
|---|---|---|---|
| 로컬 개발 | localhost | `docker-compose.yml` | DB 3306 |
| 서버 dev | `dev.againspring.net` | `docker-compose.dev.yml` | nginx 8090, db 3309 |
| 서버 prod | `againspring.net` | `docker-compose.prod.yml` | nginx 8091 |

## 로컬 개발 (DB만)

```bash
cd infra
docker compose up -d        # MariaDB 3306
docker compose logs -f
docker compose down
```

## 서버 dev 배포

```bash
# 최초 1회: env 파일 준비
cp .env.dev.example .env.dev
# .env.dev 편집

# 빌드 & 실행
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build

# 상태 확인
docker compose -f docker-compose.dev.yml ps
curl http://localhost:8090/api/health

# 로그
docker compose -f docker-compose.dev.yml logs -f

# 중단
docker compose -f docker-compose.dev.yml down
```

## 서버 prod 배포 (명시적 지시 시에만)

> **주의**: dev 검증 → commit & push to main → prod 순서 준수

```bash
# 최초 1회: env 파일 준비
cp .env.prod.example .env.prod
# .env.prod 편집 (기본값 없음, 전부 필수)

# 빌드 & 실행
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build

# 상태 확인
docker compose -f docker-compose.prod.yml ps
curl http://localhost:8091/api/health
```

## Cloudflare Tunnel

`cloudflare/tunnel.md` 참조.

## Claude Code CLI 인증 (LLM 브릿지)

backend 컨테이너는 호스트의 `~/.claude` 디렉토리를 `/root/.claude`로 마운트해 Claude CLI 자체 인증을 공유합니다. **API 키 불필요**.

**전제조건**:
1. 호스트에 `claude` 명령으로 1회 로그인 완료 (`~/.claude/` 디렉토리 존재)
2. `.env.dev` / `.env.prod`에 `CLAUDE_HOST_CONFIG_DIR=/home/<user>/.claude` 입력

호출 형태: `claude --print --model claude-haiku-4-5-20251001 "<prompt>"`

호스트 세션 만료 시: 호스트에서 `claude` 다시 실행해 재로그인 후 컨테이너 재시작.

## 포트 현황

| 포트 | 서비스 |
|---|---|
| 3306 | againspring-mariadb (로컬 dev) |
| 3308 | greenforest-mysql-dev |
| 3309 | againspring-mariadb-dev |
| 8080 | greenforest-nginx-dev |
| 8090 | againspring-nginx-dev |
| 8091 | againspring-nginx-prod |
