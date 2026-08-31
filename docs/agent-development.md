# AI Agent 개발 가이드

AI agent가 다시봄 코드를 수정할 때의 최소 작업 루프다. 상세 규칙은 `CLAUDE.md`가 라우팅하고, 문서 권위와 Doc-Sync 판단은 [`docs/_index.md`](./_index.md)를 따른다.

## 에이전트 엔트리

| 도구 | 엔트리 | 권한/규칙 |
|---|---|---|
| 공통 | [`AGENTS.md`](../AGENTS.md) → [`CLAUDE.md`](../CLAUDE.md) | 권위본은 항상 `CLAUDE.md` |
| Cursor | `.cursor/rules/*.mdc`, `.cursor/cli.json`, `.cursorignore` | 유저 `~/.cursor/cli-config.json`의 `approvalMode: unrestricted`(= `--yolo`). allow/deny는 Claude settings와 동기 |
| Codex / Claude Code | `.claude/settings.local.json`, `.claude/rules/` | 동일 절대 규칙 |

## 시작 루프

1. `CLAUDE.md`의 라우팅 표에서 작업 범위에 맞는 진입 문서만 읽는다.
2. 문서가 말하는 구조를 그대로 믿지 말고, 수정 전 `rg`로 실제 코드와 경로를 확인한다.
3. 문서 충돌 시 `코드(runtime) > docs/_index.md authority > CLAUDE.md > AGENTS.md` 순서로 판단한다.
4. 각 `docs/<module>/structure.md`의 "부재하는 것" 섹션을 확인하고 삭제된 legacy 경로를 되살리지 않는다.
5. 변경 후 `docs/_index.md`의 Doc-Sync 트리거 맵으로 함께 갱신할 문서를 결정한다.

## 작업별 진입점

| 작업 | 먼저 읽을 문서 | 실제 코드 확인 |
|---|---|---|
| 전체 구조·포트·스택 | [`docs/system.md`](./system.md) | `env/docker-compose*.yml`, `env/nginx/*.conf` |
| FE 기능·UI | [`docs/frontend/README.md`](./frontend/README.md) | `frontend/app/`, `frontend/components/`, `frontend/lib/` |
| FE 디자인·UX | [`docs/frontend/design/system.md`](./frontend/design/system.md), [`docs/frontend/ux/principles.md`](./frontend/ux/principles.md) | `frontend/tailwind.config.ts`, `frontend/app/globals.css` |
| BE 기능·API | [`docs/backend/README.md`](./backend/README.md) | `backend/src/main/java/com/againspring/` |
| API·DB 계약 | [`docs/shared/api/README.md`](./shared/50-api/README.md) | `backend/.../*Controller.java`, `backend/src/main/resources/db/migration/` |
| LLM 브릿지·프롬프트 | [`docs/backend/llm-bridge.md`](./backend/llm-bridge.md), [`docs/shared/70-policy/forbidden-words.md`](./shared/70-policy/forbidden-words.md) | `backend/.../llm/`, `docs/shared/prompts/` |
| AI-user | [`docs/ai-user/README.md`](./ai-user/README.md) | `ai-user/orchestrator/`, `ai-user/llm/`, `ai-user/learning/`, `ai-user/sync/` |
| 환경·배포 | [`docs/env/README.md`](./env/README.md) | `env/`, `.env.*.example`, `application*.yml` |

## Legacy 경로 금지

현재 문서는 루트 [`docs/`](./) 하나로 통합되어 있다. 다음 경로명은 과거 문서 위치나 삭제된 기능을 설명할 때만 언급하고, 새 링크나 구현 대상으로 사용하지 않는다.

- `frontend/docs/`, `backend/docs/`, `env/docs/`
- `app/(onboarding)/**`, `components/onboarding/**`
- `components/chat/**`, `components/result/**`
- `lib/utils/keywordGuard.ts`
- `lib/store/sessionStore.ts`, `lib/store/communityStore.ts`

## Doc-Sync 체크

코드를 바꾸면 같은 변경에서 문서도 맞춘다.

| 변경 유형 | 함께 확인할 문서 |
|---|---|
| API 컨트롤러·DTO | `docs/shared/50-api/rest-spec.md`, 필요 시 도메인별 API 문서 |
| DB migration·JPA domain | `docs/shared/50-api/database-schema.md` |
| 포트·compose·nginx | `docs/system.md`, `docs/env/architecture.md`, README 포트표 |
| 환경변수 | `docs/env/environment-variables.md`, README |
| LLM 안전·프롬프트·오류 시그니처 | `docs/shared/70-policy/forbidden-words.md`, `.claude/rules/llm-safety.md` |
| FE 디자인 토큰·global style | `docs/frontend/design/system.md` |
| FE test id·journey 변화 | `frontend/tests/e2e-realbe/support/selectors.ts`, `docs/frontend/testing.md` |

## 검증 명령

변경 범위에 맞는 최소 검증을 실행한다.

```bash
# 문서
cd frontend && npm run lint:docs

# FE
cd frontend && npm run lint:words && npm run lint:emoji && npm run test
cd frontend && npm run build

# BE
cd backend && ./gradlew test

# 실서버 e2e — dev:8090만 (prod에서 e2e 금지)
cd frontend && E2E_BASE_URL=http://localhost:8090 npm run test:e2e:realbe
```

prod 배포는 명시적 요청이 있을 때만 진행하며, `CLAUDE.md`의 순서(dev 배포·수동·e2e → 명시 지시 → 백업 → prod:8091 → push)를 따른다. **prod:8091에서 e2e·직접 반영 금지.** `prod-dev-sync`는 일일 활성.
