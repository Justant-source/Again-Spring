# docs/env — 배포 · 인프라 · 환경

다시봄(Again Spring) 인프라 배포 관련 모든 문서의 인덱스입니다.

## 문서 목록

| 문서 | 내용 | 대상 |
|---|---|---|
| [architecture.md](./architecture.md) | 배포 아키텍처 · 컴포넌트 · 통신 흐름 | 아키텍트, 전체 팀 |
| [docker.md](./docker.md) | 4가지 compose 구성 (base/dev/prod/ai-user) | 배포 담당자 |
| [environment-variables.md](./environment-variables.md) | `.env.*` 파일 항목 및 설정 | 배포 담당자 |
| [local-dev.md](./local-dev.md) | 로컬에서 BE · FE · DB 실행 | 개발자 |
| [deployment.md](./deployment.md) | dev → main → prod 배포 절차 | 배포 담당자 |
| [cloudflare.md](./cloudflare.md) | Cloudflare Tunnel 설정 및 운영 | 네트워크 관리자 |
| [structure.md](./structure.md) | `env/` 디렉토리 구조 | 전체 팀 |

## Source of truth

코드가 문서보다 우선. 다음 파일 변경 시 관련 문서 갱신:

- `env/docker-compose*.yml` → docker.md
- `env/.env*.example` → environment-variables.md
- `env/nginx/*.conf` → architecture.md, cloudflare.md
- `backend/Dockerfile`, `frontend/Dockerfile` → docker.md

## 다른 문서와의 관계

- **API / DB / LLM**: `docs/shared/` 참조
- **백엔드 코드**: `docs/backend/` 참조
- **프론트엔드 코드**: `docs/frontend/` 참조
- **배포/환경**: 이 디렉토리 (`docs/env/`)

## 빠른 시작

```bash
# 로컬 개발
cd env && docker compose up -d               # DB + 공유 LLM 워커
cd ../backend && ./gradlew bootRun           # BE :8080
cd ../frontend && npm run dev                # FE :3000

# dev 배포
cd env
docker compose up -d --build
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build

# prod 배포
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build

# shared ai-user 배포
bash ./rebuild-stacks.sh ai-user
```

자세한 절차는 [deployment.md](./deployment.md)를 참조하세요.
