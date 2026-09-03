# ADR-0007: LLM Provider Abstraction, Environment-Aware Orchestrator, Stateless Worker

**Date**: 2026-09-03
**Status**: ✅ Accepted
**Deciders**: 운영자(Justant) + 2026-09-03 결함 2 감사 세션
**Related ADRs**: [ADR-0003](./0003-llm-consolidated-to-claude-code-cli.md) (supersedes), [ADR-0001](./0001-pivot-to-community-plaza.md)

## Context

2026-09-03 감사에서 AI-user 파이프라인의 구조 결함이 확인됐다.

- **prod가 유일한 실행 환경**: `scripts/deploy.sh`·`verify-deploy.sh`·e2e 어느 것도 orchestrator→llm-ai-user→backend 경로를 dev에서 돌리지 않았다. 이 경로가 처음 실행되는 순간은 03:05 KST prod 크론이었다.
- **격리가 양방향으로 샜다**: dev/prod 공용 `llm-ai-user`가 prod DB에 붙어 `ai_prompt_template`을 쓰고 `system_setting`에서 키를 읽었다. `prod-dev-sync`는 실사용자 사연 원문(`body_raw`·`invite_token`·PRIVATE 초안·soft-deleted 행 포함)을 5분마다 dev로 복사했고, 설정 테이블 4종을 덮어 dev 튜닝을 지웠다.
- **orchestrator가 자기 환경을 몰랐다**: `SPRING_PROFILES_ACTIVE`는 프로필 파일이 없어 무효였고, backend URL 기본값이 prod였다.
- **ADR-0003이 현행과 어긋났다**: "API 키 제거·CLI 단일화"라 했지만 `ClaudeApiInvoker`(clcocloud 프록시)가 부활해 일부 경로의 기본값이었고, 문자열 `backend=CLI|API`와 enum `LlmProvider{CLAUDE,CODEX}` 두 라우팅이 공존했다.
- **운영 취약**: CLI stderr를 버려 세션 만료가 `exit code 1`로만 보였고, 600초 타임아웃이 CLI 프로세스를 죽이지 않아 풀 슬롯이 영구 소진됐으며, `llm_generation_gate` 자동 hold는 문서에만 있었다.

전제: dev는 잘 쓰지 않으며 상시 리소스를 늘리지 않는다. Claude CLI가 main이지만 Codex·다른 LLM으로 언제든 바꿀 수 있어야 한다.

## Decision

1. **단일 provider enum** `LlmProvider {CLAUDE, CODEX, API, STUB}`. 모든 워커 요청은 `provider` 필드 하나로 실행 백엔드를 고른다(구 `backend` 값 호환 파싱). `InvokerRouter.routeProvider`만 남긴다. `STUB`은 LLM을 호출하지 않는 픽스처 재생 provider다.
2. **무상태 워커**: `llm-ai-user`는 DB에 접속하지 않는다. 프롬프트 오버라이드는 orchestrator가 `ai_prompt_template`을 5분 캐시로 읽어 요청 `promptOverrides`로 싣고, API 키·base URL은 env로만 받는다. 그래서 dev/prod가 워커 하나를 안전하게 공유한다.
3. **환경 인지 orchestrator**: `AI_USER_ENV=prod|dev` 필수. 기동 시 DB·backend 호스트명이 환경과 다르면 기동을 거부한다. backend URL 기본값은 없다.
4. **콘텐츠 테이블 직접 쓰기 금지**: orchestrator는 `users`·`posts`·`post_comments`에 직접 쓰지 않는다. synthetic 계정 upsert·비밀번호 회전·조회수 보정은 backend `/api/internal/ai-user/*`(내부 토큰)로만. soft-deleted 계정은 부활시키지 않는다. 스키마 소유 표는 `docs/backend/40-data.md`, DB 계정 `ai_user_orch`는 소유 테이블만 쓴다.
5. **sync 비식별**: 실사용자 posts/post_comments는 제목·본문을 플레이스홀더로 바꾸고 PRIVATE·DRAFT·삭제 행을 제외하며 `invite_token`을 복사하지 않는다. 설정 테이블 4종은 동기화하지 않는다. 콘텐츠 주기는 1시간.
6. **dev canary 게이트**: `scripts/deploy.sh dev --ai-user-canary`가 `ai-user-orchestrator-dev`를 일시 기동해 `STUB`으로 generate→hold→publish 1사이클을 backend-dev에 실증한다. 상시 컨테이너 0.
7. **운영 가시성**: CLI stderr 꼬리를 오류에 싣고 인증 실패를 `AUTH_ERROR`로 분류한다. 풀 타임아웃은 프로세스 트리를 종료한다. 워커 `GET /v1/providers/status`를 orchestrator `LlmAvailabilityGate`가 5분마다 읽어 `llm_generation_gate`를 `auto:` 접두로 hold/resume한다(수동 hold는 건드리지 않는다).
8. **`AI_USER_SECONDARY_BACKEND_URL` 미러 삭제**, `SPRING_PROFILES_ACTIVE` 제거, dead env(`AI_USER_MODE`·`AI_USER_CLAUDE_POOL_SIZE`·`AI_USER_CODEX_POOL_SIZE`) 제거.

## Rationale

| 대안 | 기각 이유 |
|---|---|
| dev 전용 워커·orchestrator 상시 기동 | 리소스 전제 위반. 무상태 워커 + 일시 기동 canary로 같은 격리를 얻는다. |
| 워커가 `ai_prompt_template`을 계속 읽되 dev/prod DB를 요청별로 고름 | 워커에 환경 분기가 생기고 두 DB 커넥션 풀을 든다. 페이로드 첨부가 더 단순하고 커넥션 −3. |
| base `againspring-llm`(llm-worker)까지 같은 provider 인터페이스로 통합 | 마케팅 전용·이미 ProcessTerminator 보유. 별도 ADR 사안. |
| sync에서 실사용자 글을 아예 제외 | dev 피드가 비어 e2e가 깨진다. 마스킹 유지가 더 낫다. |
| `AI_USER_SECONDARY_BACKEND_URL`을 dev 전용으로 제한 | sync와 이중 경로·dedup 없음. 삭제가 정답. |

## Consequences

- ✅ dev 배포 게이트(절대 규칙 #4)가 AI-user 경로를 덮는다.
- ✅ 워커 교체(Codex·API·미래 provider)는 `LlmProvider` 값 추가 + `Invoker` 구현 하나로 끝난다. orchestrator·backend는 무관.
- ✅ dev DB에 실사용자 원문이 남지 않는다. 기존 잔재는 `ai-user/tools/scrub_dev_real_user_content.py`로 정리했다.
- ❌ admin이 프롬프트를 고치면 반영까지 최대 5분(옛 즉시 reload 폐기).
- ❌ `publish-scheduled-post?force=true`는 dev canary 전용 — prod에서 부르면 QuietHours를 무시하고 게시된다. 문서·Javadoc 경고로만 막는다.
- ❌ `ai_user_orch` DB 계정은 orchestrator Flyway가 새 테이블을 만들 때 DB 레벨 `CREATE`가 추가로 필요하다(기동 시 Flyway 오류로 드러난다).
- ⚠️ prod 반영은 이 ADR 채택과 별개로 사용자 명시 지시 시 `env/rebuild-stacks.sh ai-user` + `scripts/deploy.sh prod --i-mean-it` + `create-ai-user-db-account.sql`(prod) 순.

## Related Assets

- `ai-user/llm/.../service/{LlmProvider,InvokerRouter,StubInvoker,CliAuthFailureDetector,ProviderHealthRegistry}.java`, `pool/{ExecutionSlot,ProcessTerminator}.java`
- `ai-user/orchestrator/.../config/EnvironmentGuard.java`, `client/BackendInternalClient.java`, `service/llm/{LlmAvailabilityGate,PromptTemplateCache}.java`
- `backend/.../api/internal/AiUserInternalController.java`, `service/ai/{SyntheticUserService,SyntheticViewReconcileService}.java`
- `ai-user/sync/sync.py`, `ai-user/tools/scrub_dev_real_user_content.py`
- `scripts/ai-user-canary.sh`, `scripts/deploy.sh`, `env/scripts/sql/create-ai-user-db-account.sql`
- 문서: `docs/ai-user/30-components/{llm,orchestrator}.md`, `docs/backend/40-data.md`(소유 표), `docs/env/40-data.md`, `docs/env/60-runtime/deployment.md`
