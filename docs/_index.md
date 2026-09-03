---
title: docs — 문서 지도 & Doc-Sync 트리거 맵
last_updated: 2026-09-02
---

# docs/_index.md — 문서 지도 & Doc-Sync 트리거 맵

> **SSOT 해결 규칙**: 충돌 시 **코드(runtime) > 이 문서 > 다른 문서** 순으로 우선한다.
> 새 컨텍스트를 시작할 때 이 파일을 첫 번째로 읽는다.

## §1. 계층 인덱스 (대분류 × 계층)

| 계층 | backend/ | frontend/ | shared/ | ai-user/ | env/ | shared/marketing/ |
|---|---|---|---|---|---|---|
| 10-context | `10-context.md` 🏛 | `10-context.md` | `10-context.md` 🏛 | `10-context.md` | `10-context.md` | `10-context.md` |
| 20-containers | — | — | `20-containers.md` 🏛 | `20-containers.md` | `20-containers/` (3) | `20-containers.md` |
| 30-components | `30-components/` (3) | `30-components/` (4) | — | `30-components/` (4) | — | `30-components.md` |
| 40-data | `40-data.md` | — | — | `40-data/` (1) | `40-data.md` | `40-data/` (2) |
| 50-api | `50-api.md` 🏛 | — | `50-api/` (6) 🏛 | — | — | `50-api.md` |
| 60-runtime | `60-runtime/` (3) | `60-runtime/` (6) | `60-runtime.md` | `60-runtime/` (3) | `60-runtime/` (4) | `60-runtime.md` |
| 70-policy | `70-policy.md` | `70-policy/` (7) | `70-policy/` (9) | `70-policy/` (2) | — | `70-policy/` (7) |
| 90-adr | — | — | `90-adr/` (7) 🏛 | — | — | — |

루트: `docs/_index.md` · `docs/agent-development.md` · `docs/ai-user/history.md` · `docs/_active/` (계층 밖).
런타임 JSON: `docs/shared/policies/user-permissions.json` (이동 금지).

**`docs/_active/`** — 진행 중인 다단계 작업 트랙의 ROADMAP + 상태를 담는다. 파일 하나 = 트랙 하나
(등록·규칙은 `docs/_active/README.md` 참고). `.result/` `.temp/` `.request/`는 gitignore 대상이라
계획·상태 문서를 그 안에만 두면 유실될 수 있다 — 실제로 `.result/ai-user-v2/`가 이렇게 소실됐다.
그래서 이런 문서를 gitignore 경로에 두거나 그 경로를 권위본으로 지목하는 것은 금지이며, 위반 시
`scripts/lint_docs.py`의 `no-gitignored-authority` 검사가 커밋을 막는다. 트랙이 완료되면 해당 모듈의
history/README로 핵심을 요약 승격한 뒤 `_active/`에서 그 파일을 삭제한다 — 이 디렉토리는 항상
"지금 진행 중인 것"만 담는다.

🏛 = 그 주제의 전역 권위본. `(n)` = 디렉터리 안 본문 파일 수(README 제외). ADR 본문은 0000–0006 일곱 개(+ README).

## §2. 작업별 진입 문서

| 작업 | 1차 진입(이것만 읽기) | 2차(필요 시) | 실제 코드 확인 |
|---|---|---|---|
| AI agent 작업 루프 | `docs/agent-development.md` | — | — |
| 시스템 전체 그림 파악 | `docs/shared/10-context.md` + `docs/shared/20-containers.md` | — | — |
| 진행 중 작업 트랙 확인/등록 | `docs/_active/README.md` | — | — |
| FE 기능/UI | `docs/frontend/10-context.md` | `docs/frontend/30-components/` | `frontend/app/` · `frontend/components/` |
| FE 디자인 | `docs/frontend/70-policy/design-system.md` | — | `frontend/tailwind.config.ts` |
| FE 테스트/e2e | `docs/frontend/70-policy/testing.md` | `docs/frontend/60-runtime/flows/` | `frontend/tests/` |
| BE 기능/API | `docs/backend/10-context.md` | `docs/backend/30-components/` | `backend/src/` |
| BE DB 스키마 | `docs/backend/40-data.md` | — | `backend/src/main/resources/db/migration/` |
| LLM 브릿지 | `docs/backend/30-components/llm-bridge.md` | `docs/ai-user/30-components/llm.md` | `backend/src/main/java/com/againspring/llm/` |
| AI 유저 생성·오케스트레이션 | `docs/ai-user/10-context.md` | `docs/ai-user/30-components/orchestrator.md` | `ai-user/orchestrator/` |
| AI 유저 학습 | `docs/ai-user/30-components/learning.md` | `docs/ai-user/70-policy/llm-call-budget.md` | `ai-user/learning/` |
| API 명세 | `docs/shared/50-api/rest-spec.md` | — | `backend/src/main/java/com/againspring/api/` |
| 정책 (인증·권한) | `docs/shared/70-policy/auth.md` | `docs/shared/70-policy/user-permissions.md` | `docs/shared/policies/user-permissions.json` |
| 환경/배포 | `docs/env/60-runtime/deployment.md` | `docs/env/20-containers/architecture.md` | `env/docker-compose*.yml` |
| 마케팅 | `docs/shared/marketing/10-context.md` | `docs/shared/marketing/70-policy/` | ASM: (별도 저장소) |
| Justant-Bot (X 선댓글·대댓글) | `docs/shared/marketing/70-policy/justant-bot-x-ops.md` | `docs/shared/prompts/marketing/x-outbound-reply.md` · `x-outbound-donts.md` | `backend/src/main/java/com/againspring/marketing/XCommentComposer.java` |

## §3. 🚨 런타임 자산 (이동 금지)

| 경로 | 용도 | 마운트 | 변경 시 조치 |
|---|---|---|---|
| `docs/shared/prompts/` | LLM 프롬프트 | 컨테이너 `/app/shared/docs/prompts` (backend) | backend restart |
| `docs/shared/categories.yml` | 분류 정의 | 컨테이너 categories.yml (backend) | backend restart |
| `docs/shared/policies/user-permissions.json` | 권한 정책 | 컨테이너 policies/ (backend) | backend restart |
| `docs/shared/policies/llm-error-signatures.json` | LLM 오류·거절·누출 시그니처 SSOT | 컨테이너 `/app/shared/docs/policies/` (backend·llm-ai-user·orchestrator·ai-learning) | 각 컨테이너 restart |
| `docs/shared/templates/` | 마케팅 템플릿 | 컨테이너 templates/ (ASM) | ASM restart |

정책 **문서**는 `docs/shared/70-policy/` 에 있다. JSON 자산은 `docs/shared/policies/` 에 남긴다.

## §4. 문서 권위 그래프

```
코드(runtime) > docs/_index.md > AGENTS.md > CLAUDE.md > .cursor/rules/*.mdc
```

- **코드**: 유일한 진실의 원천. 문서가 코드와 어긋나면 코드를 믿는다.
- **docs/_index.md**: 트리거 맵·인덱스. 2차 권위본.
- **AGENTS.md**: AI 에이전트 작업 가이드 정본 (재편 후).
- **CLAUDE.md**: AGENTS.md 포인터.
- **.cursor/rules/*.mdc**: IDE 안내. 가장 낮은 우선순위.

## §5. Doc-Sync 트리거 맵

경로는 저장소 루트 기준. 축약 금지. 코드에 없는 glob은 넣지 않는다.

| # | 코드 영역 (glob) | 갱신 대상 문서 | 등급 |
|---|---|---|---|
| 1 | `backend/src/main/resources/db/migration/V*.sql` | `docs/backend/40-data.md` | M |
| 2 | `backend/src/main/java/com/againspring/llm/**` | `docs/backend/30-components/llm-bridge.md` | M |
| 3 | `backend/src/main/java/com/againspring/service/community/**` | `docs/backend/30-components/architecture.md` | M |
| 4 | `backend/src/main/java/com/againspring/api/**` | `docs/shared/50-api/rest-spec.md` | M |
| 5 | `backend/src/main/java/com/againspring/api/AuthController.java` | `docs/shared/70-policy/auth.md` | M |
| 6 | `backend/src/main/resources/policies/user-permissions.json` | `docs/shared/70-policy/user-permissions.md` | M |
| 7 | `frontend/components/**` | `docs/frontend/30-components/` | M |
| 8 | `frontend/app/**` | `docs/frontend/10-context.md` | C |
| 9 | `frontend/design/**` | `docs/frontend/70-policy/design-system.md` | M |
| 10 | `frontend/lib/constants/metaphors.ts` | `docs/frontend/70-policy/illustration-metaphor.md` | M |
| 11 | `frontend/tests/e2e-realbe/**` | `docs/frontend/70-policy/testing.md` | M |
| 12 | `ai-user/orchestrator/**` | `docs/ai-user/30-components/orchestrator.md` | M |
| 13 | `ai-user/learning/**` | `docs/ai-user/30-components/learning.md` | M |
| 14 | `env/docker-compose*.yml` | `docs/env/20-containers/architecture.md` | M |
| 15 | `backend/src/main/java/com/againspring/safety/KeywordGuard.java` | `docs/frontend/60-runtime/flows/08-crisis.md` | M |
| 16 | `backend/src/main/java/com/againspring/llm/prompt/PromptLoader.java` | `docs/backend/30-components/llm-bridge.md` | M |
| 17 | `backend/src/main/java/com/againspring/llm/remote/RemoteLlmProvider.java` | `docs/backend/30-components/llm-bridge.md` | M |
| 18 | `backend/src/main/java/com/againspring/marketing/**` | `docs/shared/marketing/70-policy/justant-bot-x-ops.md` | M |

등급: **M**=필수 · **C**=조건부.

## §6. Code → Docs 역인덱스

| 코드 경로 접두 | 소유 모듈 | 먼저 읽을 문서 | 권위본 |
|---|---|---|---|
| `backend/src/main/java/com/againspring/llm/` | backend | `docs/backend/30-components/llm-bridge.md` | 🏛 |
| `backend/src/main/resources/db/migration/` | backend | `docs/backend/40-data.md` | 🏛 |
| `frontend/components/` | frontend | `docs/frontend/30-components/` | 🏛 |
| `frontend/app/` | frontend | `docs/frontend/10-context.md` | |
| `ai-user/orchestrator/` | ai-user | `docs/ai-user/30-components/orchestrator.md` | |
| `ai-user/learning/` | ai-user | `docs/ai-user/30-components/learning.md` | |
| `env/docker-compose*.yml` | env | `docs/env/20-containers/architecture.md` | 🏛 |
| `docs/shared/policies/` | shared | `docs/shared/70-policy/` | 🏛 |
| `backend/src/main/java/com/againspring/marketing/` | marketing | `docs/shared/marketing/70-policy/justant-bot-x-ops.md` | 🏛 |
