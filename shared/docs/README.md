# shared/docs — FE+BE 공통

다시봄(Again Spring) 프로젝트의 FE와 BE가 공유해야 하는 명세, 정책, 아키텍처 문서.

## 문서 인덱스

### 입문
- [structure.md](./structure.md) — 모노레포 4-분할 구조 (env/ backend/ frontend/ shared/)
- [architecture.md](./architecture.md) — 시스템 전체 아키텍처 (브라우저↔Tunnel↔Nginx↔FE↔BE↔DB↔LLM)

### 운영
- [admin-dashboard.md](./admin-dashboard.md) — 관리자 대시보드 기능·운영 가이드 (KPI·의견함·사용자·위기 모니터링)

### [policies/](./policies/)
서비스의 행동 규칙. 코드 변경 전에 읽어야 함.

- [policies/README.md](./policies/README.md) — 정책 문서 인덱스
- 주요: [psychology-model.md](./policies/psychology-model.md), [forbidden-words.md](./policies/forbidden-words.md), [crisis-detection.md](./policies/crisis-detection.md)

### [api/](./api/)
FE↔BE 간의 계약 (15개 컨트롤러·57개 엔드포인트). 변경 시 양쪽 모두 영향.

- [api/README.md](./api/README.md) — API 문서 인덱스 (8개 도메인 파일 링크)
- [api/rest-spec.md](./api/rest-spec.md) — 공통 규약·에러코드·전체 엔드포인트 마스터 표
- [api/auth.md](./api/auth.md) — 인증 API (AuthController + OAuth2Controller)
- [api/session-chat.md](./api/session-chat.md) — 세션·채팅 API
- [api/report.md](./api/report.md) — 리포트 API
- [api/user.md](./api/user.md) — 사용자 API
- [api/feedback.md](./api/feedback.md) — 피드백 API
- [api/admin.md](./api/admin.md) — 관리자 API (17개 엔드포인트)
- [api/database-schema.md](./api/database-schema.md) — MariaDB 11 스키마 (Flyway V1~V24)

### [prompts/](./prompts/)
LLM 프롬프트 레이어 구조 (시스템 / Gottman / NVC / 관계 / 턴).

- [prompts/README.md](./prompts/README.md) — 프롬프트 아키텍처 개요
- `prompts/system.md`, `prompts/gottman/`, `prompts/nvc/`, `prompts/relations/`, `prompts/turns/`

## 다른 docs와의 관계

| 위치 | 다루는 범위 |
|---|---|
| **`shared/docs/`** (여기) | FE+BE 공통 (정책 · API · 프롬프트 · 아키텍처) — **유일한 공유 문서 위치** |
| `env/docs/` | 환경 · 도커 · Cloudflare · 배포 · env vars · 로컬 실행 |
| `backend/docs/` | BE 내부 (패키지, JPA, 보안 컴포넌트, 테스트) |
| `frontend/docs/` | FE 내부 (App Router, Zustand, MSW, 디자인 핸드오프) |

## Source of truth

이 문서는 코드와 다를 수 있습니다. 코드가 옳습니다. 차이를 발견하면 문서를 갱신하세요.

- API 실제: `backend/src/main/java/com/againspring/api/*Controller.java`
- DB 실제: `backend/src/main/resources/db/migration/V*.sql`
- LLM 실제: `backend/src/main/java/com/againspring/llm/bridge/ClaudeCodeBridge.java`
- 프롬프트 실제: `shared/docs/prompts/**.md` (런타임 로딩 자산, 문서 아님)
- 금지어 실제: `frontend/lib/constants/forbiddenWords.ts`, `backend/.../safety/KeywordGuard.java`
