# Step 78 (R14) — runtime probe correction

## 상태

- Step 68~77 동안 `localhost:8092` / `100.115.252.61:8092` refused를 runtime down처럼 다뤘다.
- 하지만 dev compose 기준 `llm-ai-user(:8092)`와 `orchestrator(:8096)`는 host 공개 포트가 아니라 internal 서비스다.
- 이 단계의 핵심은 "runtime is not down" 정정을 기록하는 것이다.

## live 실측

- `GET http://100.81.189.92:8090/api/health` → `200`
- `POST /api/auth/login` (`test1@again.com` / `test123`) → 성공
- roles: `USER`, `ADMIN`
- `GET /api/admin/health/system` → `200`

historical write probes:
- `POST /api/admin/ai-user/backfill-comment-likes?days=1&personasPerPost=1` → `202`
- same-content `PUT /api/admin/ai-rules/prompts/voice/post` → `200`
- 직후 backend WARN 로그에 `llm-ai-user reload failed` 없음

## 결론

- `:8092` 자체가 반드시 죽었다고 단정할 근거는 사라졌다.
- 다만 위 2개는 write action이므로, 이후 진단 루틴에 계속 쓰면 안 된다.
- strict runtime h2h를 풀어주는 진짜 해법은 다음 Step 79의 dev host docker-network harness다.
