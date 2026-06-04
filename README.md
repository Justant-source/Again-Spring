# 다시봄 · Again Spring

> **"다시 봄. 다시 바라봄."**

갈등 커뮤니티 플랫폼. 사용자가 갈등 사연을 올리면 **AI 배심원 9인(심리상담사 페르소나)**이 양쪽 입장을 분석해 공감 비율을 제공하고, 커뮤니티가 투표·댓글로 의견을 더합니다.

> **2026-06-02 피벗 완료**: "대화 중재자(1:1 채팅)" → "커뮤니티 광장 + AI 배심원" 모델로 전환.

---

## 핵심 플로우

```
사연 게시 (A·B 입장 작성)
    ↓
중립화 LLM (편향 제거된 요약 생성)
    ↓
AI 배심원 9인 분석 (심리상담사 페르소나, 공감 비율 투표)
    ↓
공감 비율 공개 (A측 X% : B측 Y%) + 커뮤니티 투표 · 댓글
```

---

## 📂 모노레포 구조

```
Again-Spring/
├── README.md                  # (이 파일) 프로젝트 전체 개요
├── CLAUDE.md                  # Claude Code 개발자 가이드 (작업 규칙)
│
├── frontend/                  # Next.js 14 App Router (TypeScript, Tailwind, Zustand, MSW)
│   └── docs/                  # FE 특화 문서
│
├── backend/                   # Spring Boot 3.3 (Java 21, MariaDB, LLM 브릿지)
│   └── docs/                  # BE 특화 문서
│
├── llm-worker/                # LLM 실행 전용 Spring Boot 워커 (Claude CLI, ~/.claude 마운트)
│   └── Dockerfile             # multi-stage build
│
├── shared/                    # FE/BE 공유 자원
│   ├── types/, schemas/       # 공유 타입/스키마
│   └── docs/
│       ├── api/               # REST 명세 + DB 스키마
│       ├── policies/          # 서비스 정책 (금지어, 인증, 약관 등)
│       └── prompts/           # LLM 프롬프트 (community/ 2종)
│
├── env/                       # 인프라 (Docker Compose 3-variant, nginx, Cloudflare)
│   └── docs/
│
└── marketing/                 # 마케팅 자동화 사이드카 (dev 전용, 현재 비사용)
    ├── renderer/              # 이미지 렌더링 (Playwright + Sharp, 포트 9000)
    └── social-poster/         # 소셜 포스팅 (Playwright, 포트 9100)
```

> **문서 규칙**: 상세 문서는 4개 docs 디렉토리(`shared/docs/`, `backend/docs/`, `frontend/docs/`, `env/docs/`)에만. 루트는 `README.md`·`CLAUDE.md`만.

---

## 🛠️ 기술 스택

| 계층 | 기술 | 버전 |
|---|---|---|
| **Frontend** | Next.js (App Router), TypeScript, Tailwind CSS, Zustand, MSW | 14 / 5+ / 3+ |
| **Backend** | Spring Boot, Java, Gradle (Kotlin DSL), Spring Security (JWT), Spring Data JPA | 3.3 / 21 |
| **Database** | MariaDB + Flyway (V1~V56) | 11 LTS |
| **LLM** | llm-worker (Claude CLI, `claude-haiku-4-5-20251001`, `~/.claude` 마운트) | API 키 불필요 |
| **Email** | Spring Mail (Gmail SMTP, App Password) | — |
| **OAuth** | Google OAuth 2.0 | FE-driven code exchange |
| **Infrastructure** | Docker Compose (3-variant), Cloudflare Tunnel, nginx | — |

---

## 🔌 포트 점유표

| 서비스 | 환경 | 포트 |
|---|---|---|
| MariaDB | dev (로컬/컨테이너) | 3306 |
| MariaDB | prod | 3309 |
| nginx (외부 노출) | dev | 8090 |
| nginx (외부 노출) | prod | 8091 |
| llm-ai-user | dev 컨테이너 (내부) | 8092 |
| ai-user-orchestrator | dev 컨테이너 (내부) | 8096 |
| marketing-renderer | dev 컨테이너 | 9000 |
| social-poster | dev 컨테이너 | 9100 |
| BE | 로컬 개발 | 8080 |
| FE | 로컬 개발 | 3000 |

> Cloudflare Tunnel: `dev.againspring.net → :8090` · `againspring.net → :8091`

---

## 🚀 빠른 시작

### A. 로컬 개발 (FE/BE 분리 실행)

```bash
# 1. DB 시작
cd env && docker compose up -d                              # MariaDB localhost:3306

# 2. 백엔드
cd backend && ./gradlew bootRun                             # localhost:8080

# 3. 프론트엔드
cd frontend && npm install && npm run dev                   # localhost:3000 (MSW 자동 활성)
```

### B. 통합 Dev 배포 (Docker, dev.againspring.net)

```bash
cd env
cp .env.dev.example .env.dev    # 필수 변수 입력 (MARIADB_PASSWORD, JWT_SECRET, GOOGLE_CLIENT_* 등)
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build
curl http://localhost:8090/api/health
```

### C. 헬스 체크

```bash
curl http://localhost:8080/api/health   # 로컬 BE
curl http://localhost:8090/api/health   # dev 컨테이너
```

> 상세 배포·Cloudflare Tunnel 설정: [`env/docs/deployment.md`](env/docs/deployment.md)

---

## 🧪 테스트

```bash
# 백엔드
cd backend && ./gradlew test

# 프론트엔드
cd frontend && npm run test                 # Vitest 유닛
cd frontend && npm run test:e2e:realbe      # Playwright 실서버 e2e

# 금지어/이모지 린트
cd frontend && npm run lint:words
cd frontend && npm run lint:emoji
```

---

## 📚 문서 진입점

| 영역 | 진입점 |
|---|---|
| 시스템 전체 / API / 정책 / 프롬프트 | [`shared/docs/README.md`](shared/docs/README.md) |
| 백엔드 (Spring Boot, JPA, LLM 브릿지) | [`backend/docs/README.md`](backend/docs/README.md) |
| 프론트엔드 (Next.js, MSW, UX) | [`frontend/docs/README.md`](frontend/docs/README.md) |
| 환경 / 배포 (Docker, Cloudflare) | [`env/docs/README.md`](env/docs/README.md) |
| 작업 규칙 (Claude Code 협업 가이드) | [`CLAUDE.md`](CLAUDE.md) |
| ADR (아키텍처 의사결정 기록) | [`shared/docs/ADR/README.md`](shared/docs/ADR/README.md) |

---

## ⚠️ 핵심 규칙 (빠른 참조)

1. **FE → LLM 직접 호출 금지** — 모든 LLM 요청은 BE 경유
2. **BE LLM = RemoteLlmProvider 단일** — HTTP → `againspring-llm-{dev,prod}:8090`
3. **prod 배포**: 명시적 "prod에 배포해줘" 지시 없으면 배포 금지
4. **`.env.prod` git 커밋 금지**
5. **AI 출력**: `판결/처방/승패` 표현 금지 → `공감 비율/관점` 사용
6. **marketing**: dev 전용 사이드카 (prod 미사용)

> 상세 규칙: [`CLAUDE.md`](CLAUDE.md)
