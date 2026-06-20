# Step 82 — Phase 1: 이미지 신선도 감사 Host Handoff

**날짜**: 2026-06-21  
**단계**: Wind-Down Phase 1 — Prod Image Freshness Audit  
**실행 위치**: dev/prod docker host (이 명령들은 메인 Claude 셸에서 실행 불가 — 직접 실행 필요)

---

## 배경

모든 cheap-win 코드는 HEAD(`4ffabf4a`)에 이미 포함됨. Phase 1의 핵심 질문:
**"prod 이미지가 cheap-win 커밋 이후에 빌드됐는가?"**

## 핵심 커밋 시각 (재탐색 불필요)

| 커밋 | 시각 | 내용 |
|---|---|---|
| `74e2b283` | 2026-06-17 12:02:16 +0900 | R9 Track A+B — injectTypos + CASUAL 25% (OutputSanitizer, PromptAssembler, ActionExecutor 모두) |
| `b783168d` | 2026-06-20 05:12:18 +0900 | ai-user: stabilize ai-user tests (OutputSanitizer 최신 변경) |

## 명령 1: 이미지 Created 시각 조회 (docker host에서 실행)

```bash
# dev 이미지
docker inspect againspring-llm-ai-user --format '{{.Created}}'
docker inspect againspring-ai-user-orchestrator --format '{{.Created}}'

# prod 이미지  
docker inspect againspring-llm-ai-user-prod --format '{{.Created}}'
docker inspect againspring-ai-user-orchestrator-prod --format '{{.Created}}'
```

**판정 기준**:
- 이미지 Created ≥ `b783168d` 커밋 시각 (2026-06-20 05:12:18) → **이미 반영됨 (no-op)**
- 이미지 Created < `74e2b283` 커밋 시각 (2026-06-17 12:02:16) → **미반영 → dev 재빌드 필요**
- 이미지 Created 사이 → **부분 반영, 확인 필요**

## 명령 2: .env.prod 점검 (prod host에서 실행)

```bash
grep 'AI_USER_ML_ENABLED' /home/justant/Data/Again-Spring/env/.env.prod
# 예상: AI_USER_ML_ENABLED=false  또는 없음(default false). true이면 즉시 HALT 보고.
```

## 명령 3: 미반영 시에만 — dev 재빌드 + e2e-realbe (dev host에서 순서대로)

```bash
# ① dev 재빌드 (llm-ai-user, ai-user-orchestrator만)
cd /home/justant/Data/Again-Spring/env
docker compose -f docker-compose.dev.yml --env-file .env.dev up -d --build llm-ai-user ai-user-orchestrator

# ② e2e-realbe dev:8090 전체 실행 (LLM 미호출, no-LLM fixture 사용)
cd /home/justant/Data/Again-Spring/frontend
E2E_BASE_URL=http://localhost:8090 npm run test:e2e:realbe

# 전체 PASS 확인 후 main push 진행
```

## 명령 4: prod 재빌드 (사용자의 명시적 "prod에 배포해줘" 지시 후에만)

```bash
# DB 백업 먼저
# 그 다음:
cd /home/justant/Data/Again-Spring/env
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build llm-ai-user-prod ai-user-orchestrator-prod
```

## 불변식 확인 (모든 명령 실행 전후)

- `AI_USER_ML_ENABLED` 값이 어디에도 `true`로 설정되지 않았는지 확인
- `ActionExecutor.java:427` 및 `AiUserMlClient.java:174` 코드 미변경 확인: `git diff HEAD ai-user/`

---

**작성**: Claude Code (Agent) — 2026-06-21 자동 생성
