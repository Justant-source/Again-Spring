# 시스템 아키텍처

## 한 장 다이어그램

```mermaid
flowchart TB
    Browser[Browser SPA<br/>Next.js 14]
    subgraph Backend["Spring Boot 3.3"]
        API[REST Controller<br/>/api/sessions/{id}/messages]
        Auth[Security / JWT]
        ChatService[ChatService<br/>Solo/Duo 모드]
        LLM[ClaudeCodeBridge<br/>Semaphore 3, 60s timeout]
        Safety[PromptSanitizer<br/>KeywordGuard<br/>CrisisDetector]
        Retention[RetentionScheduler<br/>30d cron]
    end
    DB[(MariaDB 11<br/>V7: messages 테이블)]
    Claude[Claude CLI<br/>Haiku 4.5]
    Prompts["shared/docs/prompts/<br/>system + gottman + nvc + relations<br/>+ chat/(solo|duo)_chat.md"]

    Browser -->|HTTPS + JWT| API
    API --> Auth
    Auth --> ChatService
    ChatService --> Safety
    Safety --> LLM
    LLM -->|--print --model| Claude
    LLM -.loads.-> Prompts
    ChatService --> DB
    Retention -.purge expired.-> DB
```

### 이전 ASCII 버전 참조:

```
사용자 브라우저
      │ HTTPS
      ▼
┌─────────────────────┐
│  Cloudflare Tunnel  │  dev.againspring.net  → :8090
│   (cloudflared)     │  againspring.net      → :8091
└─────────┬───────────┘  www.againspring.net  → :8091
          │ HTTP (호스트)
          ▼
┌─────────────────────┐
│  nginx 컨테이너      │  /api/, /actuator/, /swagger-ui/  → backend:8080
│  (dev/prod)         │  /                                 → frontend:3000
└──┬──────────────┬───┘
   │              │
   ▼              ▼
┌────────────┐  ┌──────────────────┐
│ frontend   │  │  backend         │
│ Next.js 14 │  │  Spring Boot 3.3 │ ───── ProcessBuilder ─────► claude CLI
│ :3000      │  │  :8080           │                              (CLI 자체 인증)
└──────┬─────┘  └─────────┬────────┘                                   │
       │ axios            │ Spring Data JPA                            ▼
       │ Bearer JWT       ▼                                  Anthropic Claude API
       └──────► /api/...  ┌──────────────────┐               (Haiku 4.5 default)
                          │ MariaDB 11       │
                          │ Flyway V1~V5     │
                          │ :3306 (local)    │
                          │ :3309 (dev)      │
                          │ internal (prod)  │
                          └──────────────────┘
```

## 흐름별 설명

### 1) HTTP 요청 흐름

1. 브라우저 → `https://dev.againspring.net/api/sessions/{id}/turns`
2. Cloudflare Tunnel → 호스트 `localhost:8090`
3. `againspring-nginx-dev` (`env/nginx/dev.conf`):
   - `/api/` 매치 → `http://againspring-backend-dev:8080`
   - 타임아웃 60s, `X-Forwarded-*` 헤더 주입
4. `MediationController.progressTurn(...)` 진입
5. `MediationService` → `ClaudeCodeBridge.invoke(...)` → `claude --print --model claude-haiku-4-5-20251001 "..."`
6. 응답 직렬화 후 nginx 통해 브라우저 반환

### 2) 인증 흐름

| 단계 | 처리 |
|---|---|
| 회원가입 | `POST /api/auth/send-verification` → 이메일 코드 → `POST /api/auth/signup` |
| 로그인 | `POST /api/auth/login` → access token (JWT 24h) |
| OAuth | `POST /api/auth/oauth2/{provider}` (Google · Kakao · Naver) → `OAuthProviderService` 가 code → token → userinfo |
| 게스트 | `POST /api/auth/guest` → 임시 토큰 (1h) |
| 인증 검증 | 모든 보호 API: `JwtAuthFilter` → `JwtService` 검증 + `RevokedTokenRepository` 블랙리스트 확인 |
| 로그아웃 | `LogoutService` → JTI를 `revoked_tokens` 추가 → `RevokedTokenCleanupScheduler`가 만료된 항목 일일 정리 |

FE의 axios 인터셉터(`frontend/lib/api/client.ts`)가 `localStorage.again-spring-token`을 `Authorization: Bearer ...` 헤더로 자동 주입.

### 3) 세션 진행 (State Machine)

```
                              [V1.5 Solo-First]

[CREATED]
   │
   ├──(default)──► [SOLO_MODE] ──3 turns──► [COMPLETED] ──/report──► [REPORTED]
   │                    ▲
   │                    │ (24h timeout fallback)
   │                    │
   └──(opt-in)──► [WAITING_B] ──/sessions/join/{token}──► [B_JOINED] ──turn 1~6──► [COMPLETED]

   * 위기 키워드 감지 시 어디서든 → [TERMINATED]
```

전이는 `service/SessionStateMachine`이 단일 진실로 강제. 위험 키워드 감지 시 `CrisisDetector` 발동 → `CrisisDetectedEvent` → `SessionStatus.TERMINATED`.

### 4) LLM 호출 흐름

1. `MediationService.processTurn` 진입
2. `PromptAssembler`가 다음 레이어를 조립 (`shared/docs/prompts/`에서 로드):
   - `system.md` (정체성/금기/말투)
   - `gottman/*.md` (관련 컨텍스트만)
   - `nvc/four_steps.md`
   - `relations/{relationType}.md`
   - `turns/turn_{n}_{role}.md`
3. `PromptSanitizer` → 사용자 입력에서 prompt-injection 패턴 차단
4. `ClaudeCodeBridge.invoke` → `ClaudeCodeWorkerPool`에 위탁
5. WorkerPool: `Semaphore(3)` 제한으로 동시 3개까지, 60s 타임아웃
6. `ProcessBuilder("claude", "--print", "--model", model, prompt).start()` → stdout 읽기
7. 응답을 `ReportResponseParser` / `TurnResponseParser`로 구조화
8. `LLMCallLogger`가 `llm_call_logs`에 기록 (correlation_id, latency, outcome)
9. 실패 시 `FallbackResponses`의 안전 기본값 반환

상세는 [llm/bridge-architecture.md](./llm/bridge-architecture.md) 및 [prompts/README.md](./prompts/README.md) 참조.

### 5) 데이터 보존

- 사용자 입력 원문(`turns.content`, `mediator_message`, `mediator_summary_for_opponent`) → 30일 후 `RetentionScheduler`(매일 03:00 UTC)가 NULL 처리
- 리포트(`reports`) → 영구 보관 (요약·기여도·NVC만 남김, 원문 없음)
- 자세한 정책: [policies/data-retention.md](./policies/data-retention.md)

## 컴포넌트 책임 요약

| 컴포넌트 | 책임 | 상호작용 |
|---|---|---|
| Cloudflare Tunnel | 외부 도메인 → 호스트 포트 | nginx 8090/8091 |
| nginx | 경로 분기 + 헤더 주입 | backend, frontend |
| Next.js (FE) | UI · 라우팅 · 상태 · MSW (개발) | axios → BE |
| Spring Boot (BE) | API · 도메인 · 보안 · LLM 호출 | MariaDB, claude CLI |
| MariaDB | 영속 저장소 (12 테이블) | BE만 |
| claude CLI | LLM 응답 생성 | BE의 ProcessBuilder |

## 비기능 요구사항 위치

| 요구사항 | 구현 위치 |
|---|---|
| 입력 보안 | `safety/KeywordGuard`, `llm/bridge/PromptSanitizer` |
| 위기 대응 | `safety/CrisisDetector` → `CrisisDetectedEvent` |
| 비율 균형 | `safety/RatioEnforcer` |
| Rate limiting | `security/RateLimitFilter` (bucket4j) |
| 감사 로그 | `safety/SafetyAuditLogger`, `llm/monitoring/LLMCallLogger`, `service/retention/AccessLogService` |
| 데이터 만료 | `service/retention/RetentionScheduler` (cron `0 0 3 * * *`) |
| 토큰 정리 | `RevokedTokenCleanupScheduler` (cron `0 0 4 * * *`) |
