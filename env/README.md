# env — 배포 · 인프라

다시봄 프로젝트의 인프라 구성 (Docker Compose, 환경 변수, 배포 절차).

## 3가지 구성

| 환경 | 도메인 | Compose 파일 | 호스트 포트 |
|---|---|---|---|
| **로컬 개발** | localhost | `docker-compose.yml` | DB :3306 |
| **dev 서버** | `dev.againspring.net` | `docker-compose.dev.yml` | nginx :8090 |
| **prod 서버** | `againspring.net` | `docker-compose.prod.yml` | nginx :8091 |

## 빠른 시작

### 로컬 개발 (DB 컨테이너 + 호스트 BE/FE)

```bash
docker compose up -d
# 이제 backend/frontend를 호스트에서 직접 실행 가능
```

### dev 배포

```bash
cp .env.dev.example .env.dev
# .env.dev 편집
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build
curl http://localhost:8090/api/health
```

### prod 배포 (명시적 지시 시에만)

```bash
cp .env.prod.example .env.prod
# .env.prod 편집 (모든 값 필수)
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
curl http://localhost:8091/api/health
```

## 문서

모든 상세 문서는 `docs/` 디렉토리에 있습니다:

- [docs/README.md](./docs/README.md) — 문서 인덱스
- [docs/architecture.md](./docs/architecture.md) — 아키텍처 및 컴포넌트
- [docs/docker.md](./docs/docker.md) — Docker 구성
- [docs/deployment.md](./docs/deployment.md) — 배포 절차
- [docs/cloudflare.md](./docs/cloudflare.md) — Tunnel 설정
- [docs/local-dev.md](./docs/local-dev.md) — 로컬 실행
- [docs/environment-variables.md](./docs/environment-variables.md) — env 파일
