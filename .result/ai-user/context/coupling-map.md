# 현 ai-user DB 결합 사실 — orchestrator를 옮기지 않는 이유

> 이 문서는 "왜 ASM처럼 통째 이전하지 않는가"를 정리한다. (탐사 2026-06-15)

## 핵심 발견: ai-user는 커뮤니티 DB에 직접 SQL로 결합

ASM은 자체 DB + REST only. ai-user는 다름:

| 서비스 | DB 결합 방식 | 심각도 |
|---|---|---|
| **orchestrator** | JPA/Hibernate + Flyway 마이그레이션 → 커뮤니티 DB의 `flyway_schema_history_aiuser`. `AiUserSeedLoader`가 `users` 테이블에 `INSERT IGNORE` 직접 실행. | 🔴 높음 |
| **llm** | `ApiKeyProvider`가 `system_setting` 조회. `PromptAssembler`가 `ai_prompt_template` 조회. (env 폴백 있음) | 🟡 중간 |
| **learning** | `example_bank` 테이블을 커뮤니티 DB에 직접 기록. | 🟠 중간 |
| **sync** | prod + dev MariaDB 양쪽에 직접 pymysql 접속. | 🔴 높음 |

## 결론: 신규 ML 서비스만 신설 (통째 이전 불가)

orchestrator를 WSL로 옮기려면:
1. `users` 테이블 INSERT를 REST API로 재설계, 또는
2. Tailscale 너머로 원격 SQL 연결 (지연·보안 취약)
3. `ai-content-sync`는 두 Docker 네트워크에 동시 접속 필요 (단일 호스트 전제)

→ **ASM 패턴(신규 자체 서비스)으로 GPU 기능만 분리** 가 가장 안전하고 빠름.

## 순수 HTTP 경계 (이전 가능한 것들)

- `BackendBotClient` (게시글/댓글/투표 작성): 순수 REST, base URL env로 변경 가능
- `AiLearningClient` (RAG 검색): 순수 REST, base URL env로 변경 가능
- `LlmAiUserClient` (텍스트 생성): 순수 REST, base URL env로 변경 가능

→ **신규 `AiUserMlClient`** (Step 5)는 이 패턴 그대로 복제.

## 하드코딩된 동일 호스트 가정 (항목별)

```
1. againspring-mariadb-prod:3306   → orchestrator/llm/learning/sync DB_URL 기본값
2. againspring-mariadb-dev:3306    → sync DEV_DB_HOST
3. againspring-backend-prod:8080   → orchestrator BACKEND_BASE_URL
4. againspring-backend-dev:8080    → orchestrator AI_USER_SECONDARY_BACKEND_URL
5. againspring-llm-ai-user-prod:8092 → orchestrator LLM_AI_USER_URL
6. againspring-ai-learning-prod:8099 → orchestrator AI_LEARNING_BASE_URL
7. /home/justant/.claude 바인드마운트 → llm 컨테이너 (Claude CLI 인증)
8. ../ai-user/docs/personas/:rw    → orchestrator (페르소나 생성·이력 기록)
```
