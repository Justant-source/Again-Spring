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
../scripts/deploy.sh dev   # 기동 + /api/health/deep 대기 + verify-deploy.sh dev (분리 불가)
```

### prod 배포 (명시적 지시 시에만)

```bash
cp .env.prod.example .env.prod
# .env.prod 편집 (모든 값 필수)
../scripts/deploy.sh prod --i-mean-it   # DB 백업 + 기동 + /api/health/deep 대기 + verify-deploy.sh prod
```

> `curl /api/health`는 liveness only(DB 미확인)라 배포 검증에 쓸모없다. 실질 검증은
> `/api/health/deep`(DB `SELECT 1`)과 `scripts/deploy.sh`가 자동 실행하는
> `scripts/verify-deploy.sh`가 맡는다. 상세: [docs/deployment.md](./docs/deployment.md).

## 문서

모든 상세 문서는 `docs/` 디렉토리에 있습니다:

- [docs/README.md](./docs/README.md) — 문서 인덱스
- [docs/architecture.md](./docs/architecture.md) — 아키텍처 및 컴포넌트
- [docs/docker.md](./docs/docker.md) — Docker 구성
- [docs/deployment.md](./docs/deployment.md) — 배포 절차
- [docs/cloudflare.md](./docs/cloudflare.md) — Tunnel 설정
- [docs/local-dev.md](./docs/local-dev.md) — 로컬 실행
- [docs/environment-variables.md](./docs/environment-variables.md) — env 파일
