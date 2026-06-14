# docs/_index.md — 문서 지도 & Doc-Sync 트리거 맵

> **충돌 해결**: 코드(runtime) > authority > derived. 두 문서가 같은 사실을 다르게 서술하면
> `authority` 파일을 따른다. `derived` 파일은 독립 정의 금지 — authority 변경 시 함께 갱신.
>
> 이 파일은 `shared/docs/manifest.yaml`을 흡수했다 (2026-06-14).

---

## 모듈별 문서 인덱스

| 모듈 | 경로 | 설명 |
|---|---|---|
| 시스템 전체 | `docs/system.md` | L1 컨텍스트 + L2 토폴로지 다이어그램 (권위본) |
| FE | `docs/frontend/` | Next.js 14 — 디자인 시스템·UX 원칙·구조·테스트 |
| BE | `docs/backend/` | Spring Boot 3.3 — llm-bridge·아키텍처·테스트 |
| AI 유저 | `docs/ai-user/` | 페르소나 생성·오케스트레이션·학습·운영 |
| 공유 (API·정책·ADR) | `docs/shared/` | REST 명세·DB 스키마·정책·마케팅·ADR |
| 환경/인프라 | `docs/env/` | 배포·포트·환경변수·Docker·Cloudflare |

---

## 문서 권위 그래프 (구 manifest.yaml)

> `authority` = SSOT · `derived` = authority 기반 파생(독립 정의 금지) · `runtime` = 코드가 항상 우선

### 콘텐츠 정책

| 토픽 | authority | derived | runtime |
|---|---|---|---|
| 금지어 | `docs/shared/policies/forbidden-words.md` | `docs/frontend/policies/forbidden-words-lint.md`·`CLAUDE.md` | `frontend/lib/constants/forbiddenWords.ts`·`backend/.../safety/KeywordGuard.java` |
| 분류 카테고리 | `docs/shared/policies/categories.md` | — | `frontend/lib/constants/categories.ts` |
| 사용자 권한 | **`shared/docs/policies/user-permissions.json`** (런타임 자산·볼륨마운트) | `docs/shared/policies/user-permissions.md` | `backend/src/main/resources/user-permissions.json` (fallback) |

### 설계 시스템

| 토픽 | authority | derived | runtime |
|---|---|---|---|
| 디자인 토큰 | `docs/frontend/design/system.md` | `CLAUDE.md` (진영색 요약) | `frontend/tailwind.config.ts` |
| UX 원칙 | `docs/frontend/ux/principles.md` | `docs/frontend/ux/hax-checklist.md`·`CLAUDE.md` | — |

### API / 데이터

| 토픽 | authority | derived | runtime |
|---|---|---|---|
| REST API | `docs/shared/api/rest-spec.md` | derived: auth/user/admin/feedback.md | `backend/.../api/*Controller.java` |
| DB 스키마 | `docs/shared/api/database-schema.md` | — | `backend/src/main/resources/db/migration/V*.sql` |

### LLM

| 토픽 | authority | derived | runtime |
|---|---|---|---|
| LLM 프롬프트 | **`shared/docs/prompts/`** (런타임 자산·볼륨마운트·수정금지) | — | `backend/.../llm/prompt/PromptLoader.java` |
| LLM 브릿지 | `docs/backend/llm-bridge.md` | `CLAUDE.md` (요약) | `backend/.../llm/remote/RemoteLlmProvider.java` |

### 환경 / 인프라

| 토픽 | authority | derived | runtime |
|---|---|---|---|
| 포트·토폴로지 | `docs/system.md`·`docs/env/architecture.md` | `README.md`·`CLAUDE.md` | `env/docker-compose*.yml`·`env/nginx/*.conf` |
| 배포 절차 | `docs/env/deployment.md` | `CLAUDE.md` (3-step 요약) | — |
| 환경 변수 | `docs/env/environment-variables.md` | — | `.env.dev`·`.env.prod`·`application*.yml` |

### 마케팅 (ASM)

| 토픽 | authority | derived | runtime |
|---|---|---|---|
| ASM 전체 | `docs/shared/marketing/README.md` | `{api,architecture,platforms,social-poster}.md` | ASM 저장소 (`~/Data/Again-Spring-Marketing`) |

---

## 🚨 런타임 자산 (문서가 아님 — 이동 금지)

아래 경로는 `docs/`-이름 디렉토리 안에 있지만 **볼륨 마운트 + `application.yml` 하드코딩된
런타임 자산**이다. 이 파일들을 옮기면 dev/prod가 즉시 깨진다. **이번 재구성 범위 밖**.

| 경로 | 종류 | 마운트 / 참조 |
|---|---|---|
| `shared/docs/prompts/` | LLM 프롬프트 (read-only) | `app.prompts.path` / `PromptLoader` / `AdminAiRulesController` |
| `shared/docs/templates/first_message/*.json` | 첫 메시지 템플릿 (read-only) | `TEMPLATES_PATH` / compose `:ro` |
| `shared/docs/categories.yml` | 카테고리 마스터 (read-only) | `app.categories.path` / compose `:ro` |
| `shared/docs/policies/user-permissions.json` | 권한 설정 (read-only) | `UserPermissionsConfig` / compose `:ro` |
| `ai-user/docs/personas/profiles/` | 페르소나 코퍼스 (read-write!) | `/app/personas` 마운트 / `loadRecentBodies` 읽기·쓰기 |

이 자산들 때문에 `shared/docs/`와 `ai-user/docs/`는 이동 후에도 **남아있다**. 의도된 구조.

---

## Doc-Sync 트리거 맵 (코드 변경 → 갱신할 문서)

> SSOT Doc-Sync 게이트(CLAUDE.md 절대 규칙 #8)가 이 표를 참조한다.
> push 전 `git diff --staged --name-only`로 코드 영역을 식별하고, 아래 표의 대응 문서를 갱신한다.

| 코드 영역 (glob) | 갱신 대상 문서 |
|---|---|
| `backend/.../db/migration/V*.sql` | `docs/shared/api/database-schema.md` (ER 다이어그램 포함) |
| `backend/.../domain/**/*.java` | `docs/shared/api/database-schema.md` |
| `backend/.../api/**/*Controller.java` | `docs/shared/api/rest-spec.md` · `docs/shared/api/flows.md` |
| `env/docker-compose*` · `env/nginx/*` | `docs/system.md` · `docs/env/architecture.md` · README 포트표 |
| `domain/enums/*Status*.java` · `MarketingJob*.java` · orchestrator `ActionStatus*.java` | 해당 모듈의 stateDiagram (`docs/ai-user/orchestrator.md` 등) |
| `backend/.../safety/**` · `llm/PromptSanitizer*` · `LlmErrorSignature*` · `ContentSafetyGuard*` | `docs/shared/policies/forbidden-words.md` · `.claude/rules/llm-safety.md` |
| `shared/docs/policies/forbidden-words.md` | `.claude/rules/llm-safety.md` · `docs/frontend/policies/forbidden-words-lint.md` |
| `backend/.../llm/**` | `docs/backend/llm-bridge.md` |
| `frontend/tailwind.config.ts` · `frontend/app/globals.css` | `docs/frontend/design/system.md` |
| 환경변수 추가/변경 (`.env.*` · `application*.yml`) | `docs/env/environment-variables.md` · README |
| `shared/docs/policies/user-permissions.json` | `docs/shared/policies/user-permissions.md` · CLAUDE.md |
