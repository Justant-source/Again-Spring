# 모노레포 구조

```mermaid
flowchart LR
    Root[Again-Spring/]
    Root --> FE[frontend/<br/>Next.js 14]
    Root --> BE[backend/<br/>Spring Boot 3.3]
    Root --> Shared[shared/<br/>공통 자산]
    Root --> Env[env/<br/>인프라/배포]

    FE --> FE_docs[docs/<br/>structure, architecture,<br/>policies, ui, testing]
    BE --> BE_docs[docs/<br/>structure, architecture,<br/>api, policies, llm-bridge, testing]
    Shared --> S_docs[docs/<br/>structure, architecture,<br/>policies, api, prompts]
    Shared --> S_types[types/, schemas/]
    Env --> E_docs[docs/<br/>structure, architecture,<br/>docker, env-vars, local-dev,<br/>deployment, cloudflare]
    Env --> E_compose[compose files,<br/>nginx/, cloudflare/, .env.*]
```

### 상세 구조:

```
Again-Spring/
├── README.md                       # 프로젝트 소개 (루트)
├── CLAUDE.md                       # Claude Code 작업 규칙 (루트)
│
├── env/                            # 환경 · 인프라 · 배포
│   ├── docker-compose.yml          # local: MariaDB only
│   ├── docker-compose.dev.yml      # dev: name=againspring-dev
│   ├── docker-compose.prod.yml     # prod: name=againspring-prod
│   ├── .env.{example,dev.example,prod.example}
│   ├── nginx/{dev,prod}.conf       # 리버스 프록시 (8090/8091)
│   ├── cloudflare/                 # Tunnel 자산
│   └── docs/                       # ← env 관련 모든 문서
│
├── llm-worker/                     # LLM 전용 Spring Boot 워커 (Claude CLI 실행)
│   ├── build.gradle.kts
│   ├── Dockerfile                  # multi-stage + Claude CLI 설치
│   └── src/main/java/com/againspring/llmworker/
│       ├── controller/             # InvocationController (4 endpoints)
│       ├── pool/                   # LlmWorkerPool (ThreadPoolExecutor 100 + Queue 500)
│       ├── service/                # ClaudeCliInvoker (ProcessBuilder)
│       ├── health/                 # ClaudeCliHealthIndicator
│       ├── dto/                    # 요청/응답 DTO
│       └── exception/              # LlmException 계층
│
├── backend/                        # Spring Boot 3.3 + Java 21 + MariaDB
│   ├── build.gradle.kts
│   ├── Dockerfile                  # multi-stage (Node.js/Claude CLI 미포함 — llm-worker로 이동)
│   ├── src/main/java/com/againspring/
│   │   ├── api/                    # REST Controllers + DTO (community, admin)
│   │   ├── service/                # 비즈니스 로직 (community, crisis, retention 등)
│   │   ├── domain/                 # JPA Entity + Enum (community, notification 등)
│   │   ├── repository/             # Spring Data JPA (community, notification)
│   │   ├── llm/remote/             # RemoteLlmProvider (HTTP → llm-worker)
│   │   ├── llm/fallback/           # 로컬 fallback (개발 전용)
│   │   ├── llm/monitoring/         # LLM 호출 지표
│   │   ├── llm/prompt/             # PromptLoader
│   │   ├── llm/PromptSanitizer.java# 사용자 입력 검증 + <user_input> 태그
│   │   ├── safety/                 # KeywordGuard, CrisisDetector, RatioEnforcer
│   │   ├── security/               # JWT + Spring Security + Rate limit
│   │   ├── config/                 # OpenAPI, CORS, Async, Scheduling
│   │   └── common/                 # 예외 + 공통 DTO
│   ├── src/main/resources/
│   │   ├── application{,-dev,-prod,-test}.yml
│   │   └── safety/                 # 금지어/위험 키워드 yml
│   ├── docs/                       # ← BE 특화 문서
│   └── src/main/resources/db/migration/V*.sql  # Flyway V1~V56 (마이그레이션 권위본)
│
├── frontend/                       # Next.js 14 + React 18 + Tailwind
│   ├── package.json                # scripts: dev, build, lint, lint:words
│   ├── Dockerfile                  # multi-stage, non-root, NEXT_PUBLIC_* ARG
│   ├── app/                        # App Router (auth, community, dashboard)
│   ├── components/                 # community/c3, auth, shared
│   ├── lib/
│   │   ├── api/                    # axios + Bearer interceptor
│   │   ├── auth/                   # OAuth helper
│   │   ├── store/                  # Zustand (user, session) + persist
│   │   ├── constants/              # 금지어, 카테고리, MBTI 매핑 등
│   │   ├── types/                  # TypeScript 타입
│   │   └── utils/                  # keywordGuard, ratio, styleCalculator
│   ├── mocks/                      # MSW (browser worker + handlers + fixtures)
│   ├── scripts/check-forbidden-words.js
│   └── docs/                       # ← FE 특화 문서
│
├── shared/                         # FE+BE 공유 자산
│   ├── schemas/openapi.yaml        # OpenAPI 정의 (Swagger 보조)
│   ├── types/                      # TS 공통 타입 (common, user, session, report)
│   └── docs/                       # ← FE+BE 공통 문서
│       ├── prompts/                # ← BE PromptLoader 런타임 로딩 (NOT docs)
│       │   └── community/{jury_persona.md, neutralize.md}
│       ├── policies/               # 서비스 정책 (금지어, 보존, 권한 등)
│       ├── api/                    # API 명세 (auth, user, feedback, admin, 스키마)
│       └── ADR/                    # 아키텍처 결정 기록 (7개)
│
└── .gitignore                      # env/.env.{dev,prod} 보호
```

## 4-분할 구조 (격리 원칙)

다시봄은 4개의 독립된 도메인으로 분할되어 있습니다:

| 폴더 | 책임 |
|---|---|
| **`env/`** | 컨테이너 정의 + 환경변수 + 도메인 라우팅 — 운영·배포 |
| **`backend/`** | API + 비즈니스 + DB + 프롬프트 어셈블 + 보안 — JVM 프로세스 |
| **`llm-worker/`** | Claude CLI 실행 전용 워커 — 100풀 + 큐500, HTTP API |
| **`frontend/`** | UI + 라우팅 + 상태 + axios — Next.js 프로세스 |
| **`shared/docs/`** | 양쪽이 합의한 정책/명세/아키텍처 — **유일한 공유 문서** |
| **`shared/docs/prompts/`** | LLM 시스템·턴 프롬프트 — BE가 시작 시 로드 (런타임 자산) |

## 문서 4분할 규칙

루트 `README.md` / `CLAUDE.md` 외 모든 .md는 다음 4곳에서만 관리합니다:

- `env/docs/` — 환경/설치/배포
- `backend/docs/` — BE 특화
- `frontend/docs/` — FE 특화
- `shared/docs/` — 공통

예외: `shared/prompts/**.md`는 docs가 아니라 BE 런타임이 읽는 자산.

## 작업 규칙

| 작업 종류 | 작업 위치 | 참고 docs |
|---|---|---|
| API 추가/변경 | `backend/src/.../api/`, `frontend/lib/api/` | `shared/docs/api/rest-spec.md` → 해당 도메인 `.md` |
| DB 스키마 변경 | `backend/src/main/resources/db/migration/V{n+1}__*.sql` | `shared/docs/api/database-schema.md` |
| 프롬프트 변경 | `shared/docs/prompts/*.md` (런타임 자산) | `shared/docs/prompts/README.md` |
| LLM 브릿지 코드 | `backend/.../llm/remote/`, `llm/PromptSanitizer.java` | [`backend/docs/llm-bridge.md`](../backend/docs/llm-bridge.md) |
| 정책 검증 (금지어/위험) | `frontend/lib/constants/`, `backend/.../safety/` | `shared/docs/policies/forbidden-words.md` |
| 도커 / nginx / 배포 | `env/` | `env/docs/` |
