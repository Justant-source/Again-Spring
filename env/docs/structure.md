# env/ 디렉토리 구조

```
env/
├── docker-compose.yml          # base 스택 — MariaDB + 공유 againspring-llm (name: againspring)
├── docker-compose.dev.yml      # 서버 dev 스택 — name: againspring-dev (againspring network: external)
├── docker-compose.prod.yml     # 서버 prod 스택 — name: againspring-prod (againspring network: external)
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
| `docker-compose.yml` (base) | `againspring` | `againspring-mariadb`, **`againspring-llm`** (dev·prod 공유) |
| `docker-compose.dev.yml` | `againspring-dev` | `againspring-{mariadb,llm-ai-user,ai-user-orchestrator,ai-learning,marketing-renderer,social-poster,backend,frontend,nginx}-dev` |
| `docker-compose.prod.yml` | `againspring-prod` | `againspring-{mariadb,backend,frontend,nginx}-prod` |

`name:` 필드는 각 compose 파일 상단에 명시 — 디렉토리명에 의존하지 않음.

**llm-worker 컨테이너명**: `againspring-llm` (base compose — dev·prod 공유, `network name: againspring`)

**시작 순서**: `docker compose up -d` (base) → dev/prod 스택 순으로 기동해야 `againspring-llm` 먼저 준비됨.

## 호스트 포트 점유

| 포트 | 환경 | 서비스 |
|---|---|---|
| 3306 | local | `againspring-mariadb` (MariaDB) |
| 3309 | dev | `againspring-mariadb-dev` |
| 8090 | dev | `againspring-nginx-dev` (Cloudflare Tunnel target) |
| 8091 | prod | `againspring-nginx-prod` (Cloudflare Tunnel target) |
| 8092 | dev (internal) | `againspring-llm-ai-user` (Haiku 본문 생성 워커) |
| 8096 | dev (internal) | `againspring-ai-user-orchestrator` (AI 유저 오케스트레이터) |
| 9000 | dev (internal) | `againspring-marketing-renderer-dev` (Playwright 렌더러) |
| 9100 | dev (internal) | `againspring-social-poster-dev` (소셜 포스팅) |

prod MariaDB는 외부 포트 노출 없음 (internal only). dev의 9000, 9100도 내부 네트워크에서만 접근.

## 빌드 컨텍스트

dev/prod compose 모두:
- `backend-{dev,prod}.build.context: ../backend` → `backend/Dockerfile`
- `frontend-{dev,prod}.build.context: ../frontend` → `frontend/Dockerfile`

상위 디렉토리(`env/` → `..` = 프로젝트 루트)를 컨텍스트로 사용. 디렉토리 리네임 시 이 상대 경로가 자동으로 유효.

## 볼륨

| compose | volume | 목적 |
|---|---|---|
| local | `mariadb_data` (실제 이름: `againspring_mariadb_data`) | 로컬 DB 영속성 |
| dev | `mariadb_dev_data` (실제 이름: `againspring-dev_mariadb_dev_data`) | dev DB 영속성 |
| dev | `marketing_assets_dev` | 마케팅 렌더링 결과물 임시 저장 |
| prod | `mariadb_prod_data` (실제 이름: `againspring-prod_mariadb_prod_data`) | prod DB 영속성 |

**bind mount** (named volume 아님):
- **llm-worker 컨테이너** (llm / llm): 호스트의 `${CLAUDE_HOST_CONFIG_DIR}`을 `/root/.claude`로 마운트. Claude CLI 인증 세션 공유 목적.
  - dev: 기본값 `/home/justant/.claude`
  - prod: 기본값 `/root/.claude`
- **social-poster-dev**: 호스트의 `../marketing/social-poster/src`을 `/app/src`로 마운트. 핫리로드용.
