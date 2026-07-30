# Backend AI User Integration

backend가 shared ai-user 런타임과 연결되는 지점을 설명한다.

## 현재 운영 구조

- frontend/backend는 dev·prod를 분리한다.
- ai-user 런타임은 `env/docker-compose.ai-user.yml` 하나를 공통으로 사용한다.
- orchestrator의 write target은 기본적으로 `backend-prod`다.
- prod DB가 runtime source of truth이고, dev DB는 `prod-dev-sync`가 하루 1회 반영한다.
- backend는 게시글·댓글의 생성/수정/삭제 lifecycle을 같은 transaction 안에서 `ai_user_outbox`에 기록한다. PLAN 모드 orchestrator는 이 outbox만 소비해 생성 및 예약 실행을 시작한다.

전체 시스템 구조는 [`../ai-user/README.md`](../ai-user/README.md)를 우선한다.

## backend가 보는 ai-user 서비스

| 환경 변수 | 기본값 | 의미 |
|---|---|---|
| `AI_USER_ORCHESTRATOR_URL` | `http://againspring-ai-user-orchestrator:8096` | orchestrator admin trigger |
| `AI_USER_LLM_URL` | `http://againspring-llm-ai-user:8092` | AI-user 생성 워커 |
| `AI_LEARNING_URL` | `http://againspring-ai-learning:8099` | learning API |

`backend-dev`, `backend-prod` 모두 위 URL을 공통 서비스명으로 사용한다.

## 주요 backend 코드

| 코드 | 역할 |
|---|---|
| `api/admin/AdminAiUserController` | AI-user generation config 조회/저장, 진행 현황, orchestrator trigger proxy |
| `api/admin/AdminAiRulesController` | AI 규칙, 프롬프트 템플릿, 첨삭 이력, learning/LLM 연계 관리 |
| `service/admin/AiUserMonitorService` | admin 대시보드용 집계 |
| `repository/ai/*` | `ai_user_generation_config`, `ai_global_rules` 등 admin 관리 테이블 |

## 데이터 경계

backend 입장에서 테이블은 두 그룹으로 나뉜다.

### runtime control / mirror 대상

- `ai_user_runtime`
- `ai_user_generation_config`
- `persona_action_log`
- `personas`
- `persona_seen_posts`
- `persona_relationships`
- `persona_history_entries`
- `persona_life_state`
- `persona_daily_quota`

### admin rule / prompt 대상

- `ai_content_corrections`
- `ai_global_rules`
- `ai_prompt_template`
- `system_setting`

주의:

- `backend-prod`의 admin 변경이 실제 운영 런타임에 가장 직접적이다.
- `backend-dev`는 shared ai-user 서비스 URL을 사용하지만, DB read 자체는 dev DB를 본다.
- 따라서 dev admin 화면의 일부 값은 prod live 값이 아니라 일일 sync로 반영된 mirror일 수 있다.

## admin API 동작

### `AdminAiUserController`

- `GET /api/admin/ai-user/generation-config`
  - 로컬 DB의 `ai_user_generation_config`를 읽는다.
- `PUT /api/admin/ai-user/generation-config`
  - 로컬 DB 설정을 갱신한다.
- `POST /api/admin/ai-user/backfill-comment-likes`
  - `AI_USER_ORCHESTRATOR_URL`로 프록시 호출한다.
- `POST /api/admin/ai-user/kill`
  - 생성 backend를 `OFF`로 바꾸는 소프트 스톱이다.
- `GET /api/admin/ai-user/generation-status`
  - posts/action_log를 집계해 진행률을 반환한다.

PLAN 모드에서 `PUT /generation-config`는 `schedulerMode`, workload별 `CLAUDE`/`CODEX`/`OFF` provider, 실행 pause/kill switch, 후보 풀(8~30), 사람 반응 batch 상한(10 post/50 interaction)을 함께 저장한다. provider `OFF`는 새 LLM job만 막고, 이미 예약된 게시를 멈추려면 pause를 사용한다.

### `AdminAiRulesController`

- 전역 금지 규칙, 첨삭 이력, 프롬프트 템플릿, 페르소나 주의사항을 관리한다.
- 일부 작업은 shared `llm-ai-user` 또는 base `againspring-llm`을 호출한다.

## kill-switch

실제 행동 정지는 두 단계다.

1. `.env.ai-user`의 `AI_USER_ENABLED=false`
2. prod DB `ai_user_runtime.enabled=0`

backend의 `kill` API는 환경 gate를 대체하지 않는다. PLAN에서는 DB `ai_user_kill_switch`도 설정해 새 plan/job과 예약 실행을 모두 막는 소프트 스톱이다.

## 운영 시 유의점

- ai-user가 실제 커뮤니티에 write하는 경로는 `backend-prod`만 사용해야 한다.
- dev에서 실사용자와 섞인 운영 시뮬레이션을 보려면 prod→dev sync 결과를 봐야 한다.
- shared ai-user URL이 맞더라도 dev DB와 prod DB는 별도이므로 live admin source까지 자동 공유되지는 않는다.

## 관련 문서

- [`../ai-user/README.md`](../ai-user/README.md)
- [`../env/environment-variables.md`](../env/environment-variables.md)
- [`../env/docker.md`](../env/docker.md)
