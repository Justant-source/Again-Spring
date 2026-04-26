# env/ 디렉토리 구조

```
env/
├── docker-compose.yml          # 로컬 개발용 — MariaDB 단독 (host:3306)
├── docker-compose.dev.yml      # 서버 dev 스택 — name: againspring-dev
├── docker-compose.prod.yml     # 서버 prod 스택 — name: againspring-prod
│
├── .env.example                # 로컬용 env 템플릿 (DB + JWT만)
├── .env.dev.example            # dev 서버 env 템플릿
├── .env.prod.example           # prod 서버 env 템플릿
├── .env.dev                    # 실제 dev secrets (gitignored)
├── .env.prod                   # 실제 prod secrets (gitignored, 호스트에서만 생성)
│
├── nginx/
│   ├── dev.conf                # dev nginx — server_name: dev.againspring.net
│   └── prod.conf               # prod nginx — server_name: againspring.net, www.againspring.net (CF real-ip 처리)
│
├── cloudflare/                 # Cloudflare Tunnel 관련 자산 보관 (현재는 docs로 흡수됨)
│
├── README.md                   # 빠른 시작 (docs/README.md로 점진 통합)
└── docs/                       # 환경/설치/배포 문서
```

## 컨테이너 ↔ compose 매핑

| compose 파일 | project name | container_name(s) |
|---|---|---|
| `docker-compose.yml` | `againspring` | `againspring-mariadb` |
| `docker-compose.dev.yml` | `againspring-dev` | `againspring-{mariadb,backend,frontend,nginx}-dev` |
| `docker-compose.prod.yml` | `againspring-prod` | `againspring-{mariadb,backend,frontend,nginx}-prod` |

`name:` 필드는 각 compose 파일 상단에 명시 — 디렉토리명에 의존하지 않음.

## 호스트 포트 점유

| 포트 | 환경 | 서비스 |
|---|---|---|
| 3306 | local | `againspring-mariadb` (MariaDB) |
| 3309 | dev | `againspring-mariadb-dev` |
| 8090 | dev | `againspring-nginx-dev` (Cloudflare Tunnel target) |
| 8091 | prod | `againspring-nginx-prod` (Cloudflare Tunnel target) |

prod MariaDB는 외부 포트 노출 없음 (internal only).

## 빌드 컨텍스트

dev/prod compose 모두:
- `backend-{dev,prod}.build.context: ../backend` → `backend/Dockerfile`
- `frontend-{dev,prod}.build.context: ../frontend` → `frontend/Dockerfile`

상위 디렉토리(`env/` → `..` = 프로젝트 루트)를 컨텍스트로 사용. 디렉토리 리네임 시 이 상대 경로가 자동으로 유효.

## 볼륨

| compose | volume |
|---|---|
| local | `mariadb_data` (실제 이름: `againspring_mariadb_data`) |
| dev | `mariadb_dev_data` (실제 이름: `againspring-dev_mariadb_dev_data`) |
| prod | `mariadb_prod_data` (실제 이름: `againspring-prod_mariadb_prod_data`) |

backend 컨테이너는 호스트의 `~/.claude` 디렉토리를 `/root/.claude`로 bind mount (`CLAUDE_HOST_CONFIG_DIR` 환경변수). LLM CLI 인증 공유 목적.
