# shared/docs — FE+BE 공통

다시봄(Again Spring) 프로젝트의 FE와 BE가 공유해야 하는 명세, 정책, 아키텍처 문서.

## 📋 아키텍처 결정 기록 (ADR)

2026-06-02 커뮤니티 광장 피벗 관련 주요 결정사항:

- [**ADR/README.md**](./ADR/README.md) — ADR 인덱스 및 작성법
- [**ADR-0001**](./ADR/0001-pivot-to-community-plaza.md) — 🟥 **커뮤니티 광장 피벗** (1:1 중재 채팅 → 공개 게시글 + AI 배심원)
- [ADR-0002](./ADR/0002-psychology-model-repurposed-for-jurors.md) — 심리학 모델 배심원 페르소나화 (Gottman/NVC/EFT)
- [ADR-0003](./ADR/0003-llm-consolidated-to-claude-code-cli.md) — LLM 통합 (Claude Code CLI 단일 경로)
- [ADR-0004](./ADR/0004-onboarding-mbti-hidden-not-removed.md) — 온보딩/MBTI 숨김 (삭제 아님)
- [ADR-0005](./ADR/0005-marketing-automation-retained-unchanged.md) — V15 마케팅 자동화 유지 (격리됨)
- [ADR-0006](./ADR/0006-legacy-deletion-and-git-recovery.md) — 🔴 **삭제 원장 및 복구 경로** (60+ 파일, 8 테이블)

## 문서 인덱스

### 입문
- [structure.md](./structure.md) — 모노레포 4-분할 구조 (env/ backend/ frontend/ shared/)
- [architecture.md](./architecture.md) — 시스템 전체 아키텍처 (브라우저↔Tunnel↔Nginx↔FE↔BE↔DB↔LLM, 커뮤니티 광장 기준)

### 운영

### [policies/](./policies/)
서비스의 행동 규칙. 코드 변경 전에 읽어야 함.

- [policies/README.md](./policies/README.md) — 정책 문서 인덱스
- 주요: [psychology-model.md](./policies/psychology-model.md), [forbidden-words.md](./policies/forbidden-words.md)

### [api/](./api/)
FE↔BE 간의 계약 (커뮤니티 광장 기준). 변경 시 양쪽 모두 영향.

- [api/README.md](./api/README.md) — API 문서 인덱스 (도메인별 파일 링크)
- [api/rest-spec.md](./api/rest-spec.md) — 공통 규약·에러코드·전체 엔드포인트 마스터 표
- [api/auth.md](./api/auth.md) — 인증 API (AuthController + OAuth2Controller)
- [api/user.md](./api/user.md) — 사용자 API (프로필·비밀번호·탈퇴)
- [api/feedback.md](./api/feedback.md) — 피드백 API
- [api/admin.md](./api/admin.md) — 관리자 API (대시보드, 모니터링, 마케팅)
- [api/database-schema.md](./api/database-schema.md) — MariaDB 11 스키마 (Flyway V1~V56)

### [prompts/](./prompts/)
LLM 배심원 프롬프트 (커뮤니티 광장).

- [prompts/community/](./prompts/community/) — **배심원 페르소나** (jury_persona.md), **중립화** (neutralize.md)

## 다른 docs와의 관계

| 위치 | 다루는 범위 |
|---|---|
| **`shared/docs/`** (여기) | FE+BE 공통 (정책 · API · 프롬프트 · 아키텍처) — **유일한 공유 문서 위치** |
| `env/docs/` | 환경 · 도커 · Cloudflare · 배포 · env vars · 로컬 실행 |
| `backend/docs/` | BE 내부 (패키지, JPA, LLM 브릿지, 보안 컴포넌트, 테스트) |
| `frontend/docs/` | FE 내부 (App Router, Zustand, MSW, UX 원칙, 디자인 핸드오프) |

## Source of truth

이 문서는 코드와 다를 수 있습니다. 코드가 옳습니다. 차이를 발견하면 문서를 갱신하세요.

- API 실제: `backend/src/main/java/com/againspring/api/*Controller.java`
- DB 실제: `backend/src/main/resources/db/migration/V*.sql`
- LLM 실제: `backend/src/main/java/com/againspring/llm/remote/RemoteLlmProvider.java` (HTTP → `againspring-llm-{dev,prod}:8090/v1/invoke`)
- 프롬프트 실제: `shared/docs/prompts/community/**.md` (BE 런타임 로딩 자산)
- 금지어 실제: `frontend/lib/constants/forbiddenWords.ts`, `backend/.../safety/KeywordGuard.java`
