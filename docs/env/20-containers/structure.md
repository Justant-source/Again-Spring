# env/ 디렉토리 구조

```
env/
├── docker-compose.yml          # base 스택
├── docker-compose.dev.yml      # dev 웹/DB 스택
├── docker-compose.prod.yml     # prod 웹/DB 스택
├── docker-compose.ai-user.yml  # 공통 ai-user 스택
├── rebuild-stacks.sh           # compose 재빌드/재기동 스크립트
│
├── .env.example
├── .env.dev.example
├── .env.prod.example
├── .env.ai-user.example
├── .env.dev
├── .env.prod
├── .env.ai-user
│
├── nginx/
│   ├── dev.conf
│   └── prod.conf
│
├── cloudflare/
├── README.md
└── docs/
```

## compose ↔ 컨테이너 매핑

| compose 파일 | project name | 주요 컨테이너 |
|---|---|---|
| `docker-compose.yml` | `againspring` | `againspring-mariadb`, `againspring-llm` |
| `docker-compose.dev.yml` | `againspring-dev` | `againspring-{mariadb,backend,frontend,nginx}-dev` |
| `docker-compose.prod.yml` | `againspring-prod` | `againspring-{mariadb,backend,frontend,nginx}-prod` |
| `docker-compose.ai-user.yml` | `againspring-ai-user` | `againspring-{llm-ai-user,ai-learning,ai-user-orchestrator,prod-dev-sync}` |

`name:` 필드가 각 compose에 명시돼 있으므로 디렉토리명에 의존하지 않는다.

## 시작 순서

```bash
docker compose up -d
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
docker compose -f docker-compose.ai-user.yml --env-file .env.ai-user up -d
```

shared ai-user는 `againspring-dev`, `againspring-prod` 네트워크를 외부 네트워크로 참조하므로 dev/prod 스택이 먼저 떠 있어야 한다.

## 호스트 포트

| 포트 | 서비스 |
|---|---|
| `3306` | `againspring-mariadb` |
| `3309` | `againspring-mariadb-dev` |
| `8090` | `againspring-nginx-dev` |
| `8091` | `againspring-nginx-prod` |
| `8099` | `againspring-ai-learning` |

내부 전용 포트:

- `againspring-llm:8090`
- `againspring-llm-ai-user:8092`
- `againspring-ai-user-orchestrator:8096`
- `againspring-backend-dev:8080`
- `againspring-backend-prod:8080`

## 빌드 컨텍스트

| 서비스군 | 컨텍스트 |
|---|---|
| backend | `../backend` |
| frontend | `../frontend` |
| base llm | `../llm-worker` |
| ai-user llm | `../ai-user/llm` |
| ai-user orchestrator | `../ai-user/orchestrator` |
| ai-user learning | `../ai-user/learning` |
| ai-user sync | `../ai-user/sync` |

## 볼륨

| compose | volume | 목적 |
|---|---|---|
| base | `mariadb_data` | 로컬 DB 영속성 |
| dev | `mariadb_dev_data` | dev DB 영속성 |
| prod | `mariadb_prod_data` | prod DB 영속성 |

bind mount:

- `${CLAUDE_HOST_CONFIG_DIR}:/root/.claude`
  - `againspring-llm`
  - `againspring-llm-ai-user`
- `../ai-user/docs/personas:/app/personas:rw`
  - `againspring-ai-user-orchestrator`

## 네트워크

| 네트워크 | 생성 주체 | 설명 |
|---|---|---|
| `againspring` | base compose | base 공유 네트워크 |
| `againspring-dev` | dev compose | dev 전용 네트워크 |
| `againspring-prod` | prod compose | prod 전용 네트워크 |

`docker-compose.ai-user.yml`은 세 네트워크를 외부 네트워크로 참조한다.

## 재빌드 스크립트

```bash
cd env
bash ./rebuild-stacks.sh ai-user
bash ./rebuild-stacks.sh --build-only ai-user
bash ./rebuild-stacks.sh base dev prod ai-user
```

- 기본 스택은 `ai-user`
- 기본 모드는 `up -d --build`
- `--build-only`일 때만 real env 파일이 없으면 `*.example`을 fallback으로 사용한다.
