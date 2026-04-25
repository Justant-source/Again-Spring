# 다시봄 · Again Spring

> **"다시 봄. 다시 바라봄."**  
> 싸운 두 사람 사이에서 AI 중재자가 양쪽 이야기를 중립적으로 정리해, 관계 회복을 돕는 웹앱.

본 레포는 **모노레포 구조**로 프론트엔드, 백엔드, 공유 리소스, 인프라를 한곳에 관리합니다.

---

## 📂 디렉토리 구조

```
Again-Spring/
├── frontend/              # Next.js 14 (App Router, TypeScript, Tailwind, MSW)
├── backend/               # Spring Boot 3.3 (Java 21, MariaDB, LLM 브릿지)
├── shared/                # FE/BE 공유 타입, 프롬프트, 스키마, 문서
│   └── docs/              # API 명세, 시스템 프롬프트, 정책 문서
├── infra/                 # Docker Compose (MariaDB)
└── .request/design/       # 디자인 핸드오프 에셋 (참조용)
```

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
cd infra && docker compose up -d           # MariaDB 3306

# 2. 백엔드
cd backend && ./gradlew bootRun            # localhost:8080

# 3. 프론트엔드
cd frontend && npm install && npm run dev  # localhost:3000 (MSW 자동 활성)
```

dev 프로파일은 기본 환경변수가 자동 적용됩니다. 실제 LLM 호출이 필요하면 호스트의 `claude` CLI가 PATH에 있어야 합니다.

### B. 통합 Dev 배포 (Docker, dev.againspring.net)

```bash
cd infra
cp .env.dev.example .env.dev
# .env.dev 편집: MARIADB_PASSWORD, JWT_SECRET, GOOGLE_CLIENT_*, MAIL_*, CLAUDE_HOST_CONFIG_DIR 등

docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build

curl http://localhost:8090/api/health      # nginx 경유
```

자세한 배포/포트/Cloudflare Tunnel 설정은 `infra/README.md`, `CLAUDE.md` 참조.

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
| **LLM** | Claude Code CLI (Haiku 4.5) | API 키 불필요, 호스트 ~/.claude 마운트 |
| **Email** | Spring Mail (Gmail SMTP) | App Password 인증 |
| **OAuth** | Google OAuth 2.0 | FE-driven code exchange |
| **Infrastructure** | Docker Compose (멀티 컨테이너) | v2+ |
| | Cloudflare Tunnel | dev/prod 도메인 라우팅 |

---

## 🗂️ 백엔드 주요 모듈

```
backend/src/main/java/com/againspring/
├── api/               # REST 컨트롤러 + DTO
├── domain/            # JPA 엔티티 (User, Session, Turn, Report, relationship/*)
├── repository/        # JpaRepository 인터페이스
├── service/           # 비즈니스 로직 (Mediation, Report, Session, User, Graph...)
├── llm/               # LLM 브릿지 (ClaudeCodeBridge, PromptSanitizer, FallbackResponses)
├── safety/            # 위기 감지 + 금지어 가드 (CrisisDetector, KeywordGuard)
├── security/          # JWT 인증 (JwtService, JwtAuthFilter, SecurityConfig)
└── config/            # 설정 (CORS, JPA Auditing, Scheduling, OpenAPI...)
```

### DB 스키마 (MariaDB)

| 테이블 | 설명 |
|---|---|
| `users` | 회원 정보, 온보딩 답변(JSON), 소통 스타일 |
| `sessions` | 중재 세션, 초대 토큰, 현재 턴 상태 |
| `turns` | 세션별 대화 턴 (사용자 입력 + AI 응답) |
| `reports` | 세션 완료 후 분석 리포트 (기여도, NVC, 4Horsemen) |
| `user_relationships` | 두 사용자 간 관계 유형 + 상태 |
| `conflict_history` | 세션별 갈등 이력 |
| `temperature_history` | 관계 온도 이력 |
| `llm_call_logs` | LLM 호출 감사 로그 |

**추가 테이블** (V2/V3 마이그레이션):

| 테이블 | 설명 |
|---|---|
| `guest_sessions` | 초대 토큰별 Guest ID 일관성 (재방문 동일 ID 보장) |
| `email_verifications` | 회원가입 이메일 인증코드 (10분 만료) |

> 마이그레이션 파일: `backend/src/main/resources/db/migration/V1__init.sql` ~ `V3__add_email_verification.sql`

---

## 📚 문서

| 파일 | 내용 |
|---|---|
| `shared/docs/API_SPEC.md` | REST API 전체 명세 |
| `shared/docs/SYSTEM_PROMPTS.md` | Gottman/NVC 기반 LLM 프롬프트 |
| `shared/docs/FORBIDDEN_WORDS.md` | 금지어 · 위기 키워드 정책 |
| `shared/docs/CATEGORIES.md` | 갈등/관계 카테고리 정의 |
| `shared/docs/RATIO_CALCULATION.md` | 화해 기여도 계산 규칙 |
| `shared/docs/LLM_BRIDGE_ARCHITECTURE.md` | Claude Code 연동 설계 |
| `shared/docs/ONBOARDING_MAPPING.md` | 온보딩 Q&A → 소통 스타일 매핑 |
| `shared/docs/DATABASE_SCHEMA.md` | DB 스키마 상세 설명 |
| `shared/docs/MOCK_SCENARIOS.md` | FE MSW 목업 시나리오 |
| `shared/docs/TERMS_OF_SERVICE.md` | 서비스 이용약관 |
| `.request/design/` | UI 디자인 핸드오프 에셋 |
| `CLAUDE.md` | Claude Code 개발자 가이드 |

---

## 🔑 Git 설정

```bash
git config user.name "justant"
git config user.email "suhday@naver.com"
```

**커밋 컨벤션**:

```
feat(backend): 세션 완료 이벤트 처리 구현
fix(frontend): 금지어 스캐너 오탐 수정
docs: README 스택 정보 업데이트
test(integration): 중재 API 시나리오 추가
```

---

## 📊 진행 상황

- ✅ 모노레포 구조 (frontend/, backend/, shared/, infra/)
- ✅ 프론트엔드 (Next.js 14, MSW + 실제 API 연동)
- ✅ 백엔드 전체 구현 (Spring Boot 3.3 + MariaDB)
  - ✅ JWT 인증 (직접 회원가입 / 로그인 / 게스트 / Google OAuth)
  - ✅ 이메일 인증코드 (Spring Mail + Gmail SMTP)
  - ✅ 게스트 세션 지속성 (초대 URL별 동일 Guest-XXXXXX ID)
  - ✅ 세션 관리 + 중재 흐름 (State Machine)
  - ✅ **LLM 브릿지 — Claude Haiku 4.5 (API 키 불필요, 호스트 ~/.claude 마운트)**
  - ✅ 위기 감지 + 금지어 가드
  - ✅ 리포트 생성 (기여도, NVC, 4Horsemen)
  - ✅ 관계 그래프 (MariaDB 관계 테이블)
  - ✅ 데이터 보존 정책 (30일 만료, 스케줄러)
- ✅ Docker 멀티 컨테이너 배포 (MariaDB / BE / FE / Nginx)
- ✅ Cloudflare Tunnel (dev/prod 도메인 라우팅)
- ⏳ Prod 배포 (명시적 지시 시에만)
