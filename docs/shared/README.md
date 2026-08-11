# shared/docs — FE+BE 공통

다시봄(Again Spring) 프로젝트의 FE와 BE가 공유해야 하는 명세, 정책, 아키텍처 문서.

## 📋 아키텍처 결정 기록 (ADR)

- [**ADR/README.md**](./adr/README.md) — ADR 인덱스 및 작성법
- [**ADR-0001**](./adr/0001-pivot-to-community-plaza.md) — 커뮤니티 광장 피벗 (역사)
- [ADR-0002](./adr/0002-psychology-model-repurposed-for-jurors.md) — 심리학 모델 결정 (역사)
- [ADR-0003](./adr/0003-llm-consolidated-to-claude-code-cli.md) — LLM 통합 (Claude Code CLI 단일 경로)
- [ADR-0004](./adr/0004-onboarding-mbti-hidden-not-removed.md) — 온보딩/MBTI 숨김 (삭제 아님)
- [ADR-0005](./adr/0005-marketing-automation-retained-unchanged.md) — 마케팅 자동화 → Again-Spring-Marketing(ASM) 분리 (2026-06-09 superseded)
- [ADR-0006](./adr/0006-legacy-deletion-and-git-recovery.md) — 삭제 원장 및 복구 경로

## 문서 인덱스

### 입문
- [docs/_index.md](../../docs/_index.md) — **문서 권위 그래프 SSOT + Doc-Sync 트리거맵**
- [structure.md](./structure.md) — 모노레포 구조
- [architecture.md](./architecture.md) — 시스템 아키텍처 (광장 사연 + 공감 투표 + AI-user)

### 운영

### [policies/](./policies/)
서비스의 행동 규칙. 코드 변경 전에 읽어야 함.

- [policies/README.md](./policies/README.md) — 정책 문서 인덱스
- 주요: [psychology-model.md](./policies/psychology-model.md), [forbidden-words.md](./policies/forbidden-words.md)

### [api/](./api/)
FE↔BE 간의 계약. 변경 시 양쪽 모두 영향.

- [api/README.md](./api/README.md) — API 문서 인덱스
- [api/rest-spec.md](./api/rest-spec.md) — 공통 규약·에러코드·전체 엔드포인트 마스터 표
- [api/auth.md](./api/auth.md) — 인증 API
- [api/user.md](./api/user.md) — 사용자 API
- [api/feedback.md](./api/feedback.md) — 피드백 API
- [api/admin.md](./api/admin.md) — 관리자 API
- [api/database-schema.md](./api/database-schema.md) — MariaDB 스키마

### [prompts/](./prompts/)
BE 런타임에 마운트되는 프롬프트 자산.

- [prompts/community/](./prompts/community/) — `post_tonalization.md` (`TonalizationService` / 파트너 초대 답변)

## 런타임 자산

`docs/shared/` 아래의 일부 파일은 **BE 런타임이 직접 로딩하는 자산**이다 (볼륨 마운트).
경로를 변경하면 docker-compose도 함께 갱신해야 한다.

| 파일 | 용도 | 참조 키 |
|---|---|---|
| `prompts/community/` | 톤 정규화 등 community 프롬프트 | `app.prompts.path` |
| `templates/first_message/*.json` | 첫 메시지 템플릿 | `TEMPLATES_PATH` |
| `categories.yml` | 카테고리 마스터 | `app.categories.path` |
| `policies/user-permissions.json` | 사용자 권한 설정 | `UserPermissionsConfig` |

→ 상세: `docs/_index.md` 런타임 자산 섹션

## 타입 / 스키마

- [`types/`](./types/) — TypeScript 공유 타입 (Session, User, Report 등)
- [`api/openapi.yaml`](./api/openapi.yaml) — OpenAPI 정의 (Swagger 보조)

## 다른 docs와의 관계

| 위치 | 다루는 범위 |
|---|---|
| **`docs/shared/`** (여기) | FE+BE 공통 (정책 · API · 프롬프트 · 아키텍처) — **유일한 공유 문서 위치** |
| `docs/env/` | 환경 · 도커 · Cloudflare · 배포 · env vars · 로컬 실행 |
| `docs/backend/` | BE 내부 (패키지, JPA, LLM 브릿지, 보안 컴포넌트, 테스트) |
| `docs/frontend/` | FE 내부 (App Router, Zustand, MSW, UX 원칙, 디자인 핸드오프) |

## Source of truth

이 문서는 코드와 다를 수 있습니다. 코드가 옳습니다. 차이를 발견하면 문서를 갱신하세요.

- API 실제: `backend/src/main/java/com/againspring/api/*Controller.java`
- DB 실제: `backend/src/main/resources/db/migration/V*.sql`
- LLM 실제: `backend/src/main/java/com/againspring/llm/remote/RemoteLlmProvider.java` (HTTP → `againspring-llm-{dev,prod}:8090/v1/invoke`)
- 프롬프트 실제: `docs/shared/prompts/community/**.md` (BE 런타임 로딩 자산)
- 금지어 실제: `frontend/lib/constants/forbiddenWords.ts`, `backend/.../safety/KeywordGuard.java`
