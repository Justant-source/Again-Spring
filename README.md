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

- Node.js 20+ (FE)
- Java 21, Gradle (BE)
- Docker & Docker Compose (인프라)
- Claude CLI (BE LLM 통합용)

### 1. 인프라 (DB 먼저 시작)

```bash
cd infra
docker compose up -d
# MariaDB: localhost:3306 (DB: againspring)
```

### 2. 백엔드

```bash
cd backend
./gradlew bootRun   # localhost:8080
```

필수 환경변수:

```bash
DB_URL=jdbc:mariadb://localhost:3306/againspring?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC
DB_USER=againspring
DB_PASSWORD=changeme
JWT_SECRET=dev_secret_key_change_in_prod
CLAUDE_BIN=/path/to/claude
```

> dev 프로파일(`spring.profiles.active=dev`)은 위 기본값을 자동 적용합니다.

### 3. 프론트엔드

```bash
cd frontend
npm install
npm run dev         # localhost:3000
```

MSW(Mock Service Worker)가 `/api/*` 요청을 자동으로 가로챕니다. FE 독립 테스트 가능.

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
| **LLM** | Claude Code CLI (subprocess pool) | Latest |
| **Infrastructure** | Docker Compose | v2+ |
| | Cloudflare Tunnel | (배포 시) |

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

> 마이그레이션 파일: `backend/src/main/resources/db/migration/V1__init.sql`

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
- ✅ 프론트엔드 MSW 프로토타입 (Next.js 14)
- ✅ 백엔드 전체 구현 (Spring Boot 3.3 + MariaDB)
  - ✅ JWT 인증 (회원가입/로그인/게스트)
  - ✅ 세션 관리 + 중재 흐름 (State Machine)
  - ✅ LLM 브릿지 (Claude Code CLI, Semaphore 풀)
  - ✅ 위기 감지 + 금지어 가드
  - ✅ 리포트 생성 (기여도, NVC, 4Horsemen)
  - ✅ 관계 그래프 (MariaDB 관계 테이블)
  - ✅ 데이터 보존 정책 (30일 만료, 스케줄러)
- ⏳ FE-BE API 통합 테스트
- ⏳ 배포 (Cloudflare Tunnel + 홈서버)
