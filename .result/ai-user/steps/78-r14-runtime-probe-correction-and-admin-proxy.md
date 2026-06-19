# Step 78 (R14) — runtime probe correction + admin proxy prep

## 상태

- Step 68~77 동안 `localhost:8092` / `100.115.252.61:8092` refused를 runtime down처럼 다뤘다.
- 하지만 dev compose 기준 `llm-ai-user(:8092)`와 `orchestrator(:8096)`는 host 공개 포트가 아니라 internal 서비스다.
- 이번 단계의 목표는 "진짜로 무엇이 죽었는지"를 live dev 경로에서 다시 구분하는 것이다.

## live 실측

### 1. dev backend/admin 경로

- `GET http://100.81.189.92:8090/api/health` → `200`
- `POST /api/auth/login` (`test1@again.com` / `test123`) → 성공
- roles: `USER`, `ADMIN`
- `GET /api/admin/health/system` → `200`

### 2. backend -> orchestrator internal route

- `POST /api/admin/ai-user/backfill-comment-likes?days=1&personasPerPost=1` → `202`
- 즉 backend 컨테이너에서 `againspring-ai-user-orchestrator:8096`로 가는 내부 경로는 살아 있다.

### 3. backend -> llm-ai-user internal route

- `GET /api/admin/ai-rules/prompts/voice/post`로 기존 내용을 조회
- 같은 내용으로 `PUT /api/admin/ai-rules/prompts/voice/post` → `200`
- 이 경로는 저장 후 `llm-ai-user:8092/internal/prompts/reload`를 best-effort로 호출한다.
- 직후 backend WARN 로그에 `llm-ai-user reload failed`가 없었다.
- 따라서 backend에서 `againspring-llm-ai-user:8092`로 가는 internal reload 경로는 live 기준 도달 가능으로 본다.

### 4. direct `/admin/trigger/*` external route

- `POST /admin/trigger/reset-counter`
  - unauth: `403`
  - admin bearer: `500 INTERNAL_ERROR`
- `POST /admin/trigger/generate-posts?voice=THEQOO&count=1`
  - admin bearer: `500 INTERNAL_ERROR`

## 결론

- `:8092` 자체가 반드시 죽었다고 단정할 근거는 사라졌다.
- 현재 live blocker는:
  1. external shell에서 strict runtime `/generate/post`를 직접 검증할 공식 진입점이 없음
  2. direct `/admin/trigger/*` route는 외부에서 일관되게 쓸 수 없음

## 이번 구현

### 1. live probe script

- 파일: `.result/ai-user/scripts/probe_dev_ai_user_stack.py`
- 용도:
  - backend health
  - admin login
  - generation-config / generation-status
  - no-op prompt PUT 기반 llm reload probe
  - optional backend -> orchestrator proxy probe

### 2. backend admin proxy 준비

- 파일: `backend/src/main/java/com/againspring/api/admin/AdminAiUserController.java`
- 추가 엔드포인트:
  - `POST /api/admin/ai-user/generate-posts`
  - `POST /api/admin/ai-user/reset-counter`
- 목적:
  - 외부에서 `POST /admin/trigger/*`를 직접 치지 않고도
  - backend auth 경로를 통해 orchestrator trigger를 안정적으로 호출

## 다음 스텝

1. backend dev 재배포
2. `probe_dev_ai_user_stack.py --probe-orchestrator` 실행
3. `POST /api/admin/ai-user/generate-posts?voice=THEQOO&count=1` live 검증
4. 그 다음 R14 strict runtime h2h / cond4-B 재측정
