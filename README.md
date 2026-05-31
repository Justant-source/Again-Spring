# 다시봄 · Again Spring

> **"다시 봄. 다시 바라봄."**
> 싸운 두 사람 사이에서 AI 중재자가 양쪽 이야기를 중립적으로 정리해, 관계 회복을 돕는 웹앱.

본 레포는 **모노레포 구조**로 프론트엔드, 백엔드, 공유 리소스, 환경 설정을 한곳에 관리합니다.

---

## 📂 디렉토리 구조

```
Again-Spring/
├── README.md                 # (이 파일) 프로젝트 개요
├── CLAUDE.md                 # Claude Code 개발자 가이드 (작업 규칙 + 문서 맵)
│
├── frontend/                 # Next.js 14 (App Router, TypeScript, Tailwind, MSW)
│   ├── README.md             # 짧은 진입 가이드 → docs/README.md 링크
│   └── docs/                 # FE 특화 문서 (structure, architecture, policies, ui, testing)
│
├── backend/                  # Spring Boot 3.3 (Java 21, MariaDB, LLM 브릿지)
│   ├── README.md
│   └── docs/                 # BE 특화 문서 (structure, architecture, policies, llm-bridge, testing, openapi)
│
├── llm-worker/               # LLM 전용 Spring Boot 워커 (Claude CLI 실행, dev+prod)
│   └── Dockerfile            # multi-stage + Claude CLI 설치
│
├── shared/                   # FE/BE 공유 자원
│   ├── README.md
│   ├── types/, schemas/      # 공유 타입/스키마 (코드)
│   └── docs/                 # 공통 문서
│       ├── structure.md      # 모노레포 전체 구조
│       ├── architecture.md   # 시스템 아키텍처
│       ├── policies/         # 서비스 정책 (심리학 모델, 금지어, 위기 감지, 약관 등)
│       ├── api/              # REST 명세 + DB 스키마
│       └── prompts/          # LLM 프롬프트 템플릿 (BE가 런타임에 로드)
│
├── env/                      # 환경/인프라 (구 infra/)
│   ├── README.md
│   ├── docker-compose*.yml   # local / dev / prod 3-variant
│   ├── .env*.example
│   ├── nginx/
│   ├── cloudflare/
│   └── docs/                 # 환경 문서 (docker, env-vars, local-dev, deployment, cloudflare)
│
└── social-poster/            # 소셜 자동 포스팅 사이드카 (Playwright, dev 전용)
    ├── src/
    │   ├── lib/anti-bot.js   # 봇 탐지 우회 (핑거프린트, warmup, jitter)
    │   ├── lib/session.js    # storageState 관리
    │   ├── lib/x-selectors.js, ig-selectors.js
    │   └── routes/           # publish-x, publish-instagram, session-health, test-login
    ├── extract-session.js    # 브라우저 콘솔에서 세션 추출 스크립트
    └── src/seed-server.js    # 서버 headless 세션 시딩 CLI
```

> **문서 위치 규칙**: 모든 .md 문서는 위 4개 docs 디렉토리(`shared/docs/`, `backend/docs/`, `frontend/docs/`, `env/docs/`)에만 둡니다. 루트는 `README.md`와 `CLAUDE.md`만 허용. 모듈 루트의 `README.md`는 짧은 진입 가이드 역할만 합니다.

---

## 🚀 빠른 시작

### 필수 환경

- Node.js 20+ (FE 로컬 개발)
- Java 21, Gradle (BE 로컬 개발)
- Docker & Docker Compose (Dev/Prod 배포)
- 호스트에 Claude CLI 인증된 상태 (`~/.claude/` 디렉토리 — `claude` 명령으로 1회 로그인)

### A. 로컬 개발 (FE/BE 분리 실행)

```bash
# 1. DB 시작
cd env && docker compose up -d            # MariaDB 3306

# 2. 백엔드
cd backend && ./gradlew bootRun           # localhost:8080

# 3. 프론트엔드
cd frontend && npm install && npm run dev # localhost:3000 (MSW 자동 활성)
```

### B. 통합 Dev 배포 (Docker, dev.againspring.net)

```bash
cd env
cp .env.dev.example .env.dev
# .env.dev 편집: MARIADB_PASSWORD, JWT_SECRET, GOOGLE_CLIENT_*, MAIL_*, CLAUDE_HOST_CONFIG_DIR 등

docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build

curl http://localhost:8090/api/health     # nginx 경유
```

자세한 배포·포트·Cloudflare Tunnel 설정은 [`env/docs/README.md`](env/docs/README.md) 참조.

---

## 📚 문서 진입점

| 영역 | 진입점 |
|---|---|
| 시스템 전체 / API / 정책 / 프롬프트 | [`shared/docs/README.md`](shared/docs/README.md) |
| 백엔드 (Spring Boot, JPA, LLM 브릿지) | [`backend/docs/README.md`](backend/docs/README.md) |
| 프론트엔드 (Next.js, MSW, UI 핸드오프) | [`frontend/docs/README.md`](frontend/docs/README.md) |
| 환경 / 배포 (Docker, Cloudflare, env vars) | [`env/docs/README.md`](env/docs/README.md) |
| 작업 규칙 (Claude Code 협업 가이드) | [`CLAUDE.md`](CLAUDE.md) |

> 동일 주제가 여러 곳에 보이면 **`shared/docs/policies/`가 권위본**입니다. backend/frontend의 policies/는 각자의 구현 방법만 설명합니다.

---

## 🛠️ 기술 스택

| 계층 | 기술 | 버전 |
|---|---|---|
| **Frontend** | Next.js (App Router) | 14 |
| | TypeScript | 5+ |
| | Tailwind CSS | 3+ |
| | Zustand | 4+ |
| | MSW (Mock Service Worker) | 2+ |
| **Backend** | Spring Boot | 3.3 |
| | Java | 21 |
| | Gradle (Kotlin DSL) | 8+ |
| | Spring Security (JWT) | 6 |
| | Spring Data JPA + Hibernate | — |
| **Database** | MariaDB | 11 LTS |
| | Flyway (마이그레이션) | — |
| **LLM (대화 prod)** | ClaudeApiProvider (Anthropic REST API) | Haiku 4.5, 프롬프트 캐싱 3-tier |
| **LLM (대화 dev + 리포트)** | llm-worker (Claude CLI) | API 키 불필요, ~/.claude 마운트 |
| **Email** | Spring Mail (Gmail SMTP) | App Password 인증 |
| **OAuth** | Google OAuth 2.0 | FE-driven code exchange |
| **Infrastructure** | Docker Compose (멀티 컨테이너) | v2+ |
| | Cloudflare Tunnel | dev/prod 도메인 라우팅 |

---

## 📊 진행 상황

- ✅ 모노레포 구조 (frontend/, backend/, shared/, env/)
- ✅ 프론트엔드 (Next.js 14, MSW + 실제 API 연동)
- ✅ 백엔드 전체 구현 (Spring Boot 3.3 + MariaDB)
  - ✅ JWT 인증 (직접 회원가입 / 로그인 / 게스트 / Google OAuth)
  - ✅ 이메일 인증코드 (Spring Mail + Gmail SMTP)
  - ✅ 게스트 세션 지속성 (초대 URL별 동일 Guest-XXXXXX ID)
  - ✅ 세션 관리 + 중재 흐름 (State Machine)
  - ✅ **LLM 워커 분리** — 전용 llm-worker 컨테이너 (ThreadPoolExecutor 100 + Queue 500)
  - ✅ **prod LLM 분리 + 프롬프트 캐싱** — ClaudeApiProvider (Anthropic REST API, cache_control 3-tier)
  - ✅ 위기 감지 + 금지어 가드
  - ✅ 리포트 생성 (기여도, NVC — 4Horsemen 내부 점수만 보존)
  - ✅ 데이터 보존 정책 (30일 만료, 스케줄러)
  - ✅ 데드코드 정리 완료 (V1.5 폐기 코드·관계 그래프·4Horsemen UI)
  - ✅ **V15 마케팅 자동화** (dev 전용) — 사연→시뮬레이션→콘텐츠→이미지→승인 자동화
  - ✅ **소셜 자동 포스팅** — X·Instagram Playwright 자동화, 봇 차단 우회(anti-bot.js), 세션 자동 갱신
- ✅ Docker 멀티 컨테이너 배포 (MariaDB / llm-worker / Backend / Frontend / Nginx / social-poster(dev) / marketing-renderer(dev))
- ✅ Cloudflare Tunnel (dev/prod 도메인 라우팅)
- ⏳ Prod 배포 (명시적 지시 시에만)
