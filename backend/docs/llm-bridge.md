# LLM 브릿지 — BE 구현 가이드

다시봄은 LLM 호출을 전용 `againspring-llm` 워커 컨테이너로 분리. backend는 프롬프트를 어셈블해 HTTP로 전송, 워커가 Claude CLI를 spawn.

## 소스 코드 위치

### backend (프롬프트 어셈블 + HTTP 클라이언트)
- `backend/.../llm/remote/RemoteLlmProvider.java` — LLMProvider 구현 (`llm.provider=remote`, 기본값)
- `backend/.../llm/remote/RemoteCancelableInvocation.java` — 원격 취소 프록시 (long-poll 포함)
- `backend/.../llm/bridge/PromptSanitizer.java` — 사용자 입력 검증 (backend 유지)
- `backend/.../llm/bridge/ClaudeCodeBridge.java` — in-process fallback (`llm.provider=claude-code`)
- `backend/.../llm/bridge/ClaudeCodeWorkerPool.java` — Semaphore 풀 (fallback용)
- `backend/.../llm/monitoring/LLMCallLogger.java` — 호출 감사 로깅
- `backend/.../llm/fallback/FallbackResponses.java` — 실패 시 안전 응답

### llm-worker (Claude CLI 실행 전용 — `againspring-llm-dev/prod` 컨테이너)
- `llm-worker/.../pool/LlmWorkerPool.java` — 100 풀 + bounded LinkedBlockingQueue(500)
- `llm-worker/.../service/ClaudeCliInvoker.java` — ProcessBuilder + `--strict-mcp-config --no-session-persistence`
- `llm-worker/.../controller/InvocationController.java` — HTTP API (4종 엔드포인트)
- `llm-worker/.../health/ClaudeCliHealthIndicator.java` — `claude --version` 헬스체크

---

## 설계 원칙

1. **API 과금 없음** — Claude Pro/Max 구독의 Claude Code CLI만 사용, `ANTHROPIC_API_KEY` 불필요
2. **추상화** — `LLMProvider` 인터페이스로 provider 교체 가능 (`remote` / `claude-code` / `mock`)
3. **동시성 100 + 대기 큐** — `ThreadPoolExecutor(100) + LinkedBlockingQueue(500)`: 초과 요청 최대 500개 대기 후 순차 처리
4. **타임아웃** — 120초 기본 (대기 큐 30초 + 실행 90초); 실행 중 타임아웃 시 `destroyForcibly()`
5. **Fallback** — 실패 시 `FallbackResponses` 안전 응답 반환 + `isFallback: true` 플래그

---

## 호출 흐름

```
CancelableChatService / ReportGenerationService
   │
   │  ① PromptAssembler: system+gottman+nvc+relations+turn 레이어 조립
   ▼
PromptSanitizer.sanitize(userInput) — injection 패턴 차단
   │
   │  ② backend가 완성 프롬프트 HTTP로 전송
   ▼
RemoteLlmProvider (backend — HTTP 클라이언트)
   ├── POST /v1/invoke          (동기: 리포트 Sonnet, invoke(prompt,model))
   └── POST /v1/invocations     (비동기 채팅: 202 + invocationId)
         │
         │  ③ againspring-llm 워커 수신
         ▼
LlmWorkerPool (llm-worker — ThreadPoolExecutor 100 + LinkedBlockingQueue 500)
   │
   │  ④ 큐 대기 → 픽업 시 대기시간 체크 (>30s → CAPACITY)
   │  ⑤ ClaudeCliInvoker.invoke(prompt, model)
   ▼
ProcessBuilder("claude", "--print", "--strict-mcp-config", "--no-session-persistence",
               "--model", model, "--system-prompt", sysPart, userPart)
   │
   │  ⑥ stdout 읽기, exitCode 검사
   ▼
LLMResponse → HTTP 응답 / long-poll → LLMCallLogger → DB 저장 → API 응답
```

---

## 핵심 클래스

### `LLMProvider` (인터페이스)

```java
public interface LLMProvider {
  LLMResponse invoke(LLMRequest request) throws LLMException;
  CompletableFuture<LLMResponse> invokeAsync(LLMRequest request);
  String invoke(String prompt, String model) throws Exception;
  CancelableInvocation invokeCancelable(String prompt, String model, String sessionId);
  String getProviderName();
  boolean isHealthy();
}
```

### `RemoteLlmProvider` (기본값 — `llm.provider=remote`)

```java
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "remote")
public class RemoteLlmProvider implements LLMProvider { ... }
```

- `invoke(LLMRequest)` / `invoke(String,model)` → `POST http://againspring-llm/v1/invoke`
- `invokeCancelable()` → `POST /v1/invocations` → `RemoteCancelableInvocation` 반환
- `isHealthy()` → `GET /actuator/health` on worker
- 공유 `ScheduledExecutorService poller`가 `GET /v1/invocations/{id}/result?waitMs=25000` long-poll

### `ClaudeCodeBridge` (긴급 fallback — `llm.provider=claude-code`)

```java
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "claude-code", matchIfMissing = true)
public class ClaudeCodeBridge implements LLMProvider { ... }
```

`@ConditionalOnProperty`로 `llm.provider=mock`일 때는 `MockLLMProvider`가 빈으로 등록됨 (테스트 프로파일). `claude-code`는 긴급 롤백용으로 코드만 유지.

### `PromptSanitizer`

사용자 입력에서 prompt-injection 패턴 차단:

```java
private static final List<Pattern> INJECTION_PATTERNS = List.of(
  Pattern.compile("(?i)ignore (previous|above|all) instructions"),
  Pattern.compile("(?i)you are now"),
  Pattern.compile("(?i)system prompt"),
  Pattern.compile("(?i)</?system>"),
  Pattern.compile("(?i)forget everything"),
  Pattern.compile("(?i)disregard"),
  Pattern.compile("(?i)override")
);
```

추가 검증:
- 입력 길이 제한 (5000자)
- `[INST]`, `[/INST]` 등 특수 구분자 제거
- 매칭 시 `[REDACTED]`로 치환 + WARN 로그

### `RemoteCancelableInvocation`

```java
public class RemoteCancelableInvocation extends CancelableInvocation {
  // cancel() override: markCanceled() + 비동기 DELETE /v1/invocations/{id} + completeExceptionally
  // applyResult(): DONE/CANCELED/FAILED → resultFuture 완료
  // isPollingActive(): pollingActive && !resultFuture.isDone()
}
```

`CancelableInvocation`을 상속하므로 `CancelableChatService` **무수정** 유지.

### `PromptAssembler` + `PromptLoader`

- `PromptLoader`는 시작 시 `shared/docs/prompts/**.md`를 메모리 캐시 (`@PostConstruct`)
- `PromptAssembler.assemble(turn, role, conflictType, relationType)`이 다음을 합성:
  ```
  system.md
  + gottman/conflict_<TYPE>.md (조건부)
  + gottman/principles.md
  + nvc/framework.md
  + relations/<RELATION_TYPE>.md
  + turns/turn_<n>_<role>.md
  ```
- `POST /api/admin/prompts/reload`로 재시작 없이 캐시 무효화

### `FallbackResponses`

LLM 실패 시 반환할 안전 기본 응답:

```java
fallbacks.put("turn_3_a", "지금 답변이 어렵네요. 잠시 후 다시 시도해주세요.");
fallbacks.put("final_report", "분석 중 오류가 발생했어요. 고객센터로 문의해주세요.");
// ...
```

응답에 `isFallback: true` 표시 → FE는 사용자에게 재시도 옵션 표시.

---

## Claude Code CLI 호출 (llm-worker 내)

```bash
claude --print --strict-mcp-config --no-session-persistence \
  --model claude-haiku-4-5-20251001 \
  --system-prompt "<system_part>" "<user_part>"
```

- `--print`: 비대화형 단일 응답 모드
- `--strict-mcp-config`: MCP 서버 비활성 (host `~/.claude`에 Drive/Gmail MCP 존재 — 차단)
- `--no-session-persistence`: 세션 저장 스킵 (컨텍스트 격리 + ~100ms 단축)
- `--system-prompt`: 프롬프트를 system/user 두 파트로 분리 전달 — `<conversation_history>` 구분자 기준 split
- 프롬프트는 **인자**로 전달 (stdin 미사용 — ProcessBuilder가 안전 처리)
- ⚠️ `--bare` 금지: OAuth 인증(`~/.claude`) 무력화 — API 키 없으면 완전 실패

---

## 설정

### backend `application.yml`

```yaml
app:
  prompts:
    path: ${PROMPTS_PATH:./shared/docs/prompts}

llm:
  provider: ${LLM_PROVIDER:remote}           # 'remote' (기본) | 'claude-code' (긴급 롤백) | 'mock' (테스트)
  remote:
    base-url: ${LLM_WORKER_URL:http://againspring-llm-dev:8090}
    connect-timeout-ms: 5000
    read-timeout-ms: 130000                  # 워커 exec timeout(120s) 초과
    default-timeout-ms: 120000
    poll-wait-ms: 25000                      # long-poll 단위
  claude-code:                               # 긴급 롤백 전용 (미선택 시 빈으로 등록 안됨)
    binary-path: ${CLAUDE_BIN:claude}
    model: ${CLAUDE_MODEL:claude-haiku-4-5-20251001}
```

### llm-worker `application.yml`

```yaml
server:
  port: 8090
llm:
  worker:
    pool-size: ${LLM_POOL_SIZE:100}
    queue-capacity: ${LLM_QUEUE_CAPACITY:500}
    queue-wait-timeout-ms: ${LLM_QUEUE_WAIT_TIMEOUT_MS:30000}
    default-timeout-ms: ${LLM_DEFAULT_TIMEOUT_MS:120000}
    claude-binary-path: ${CLAUDE_BIN:claude}
    claude-model: ${CLAUDE_MODEL:claude-haiku-4-5-20251001}
    report-model: ${REPORT_LLM_MODEL:claude-sonnet-4-6}
```

---

## 인증 (호스트 ~/.claude 마운트)

API 키 미사용. `~/.claude` 볼륨은 **backend가 아닌 llm-worker 컨테이너**에 마운트:

```yaml
# docker-compose.dev.yml
againspring-llm-dev:
  volumes:
    - ${CLAUDE_HOST_CONFIG_DIR:-/home/justant/.claude}:/root/.claude
```

**설정 절차:**
1. 호스트에서 `claude` 명령으로 로그인 → `~/.claude/` 디렉토리 생성
2. `againspring-llm-dev/prod` 컨테이너가 동일 세션 공유
3. `ANTHROPIC_API_KEY` 불필요

**세션 만료 시:**
- 호스트에서 `claude` 재로그인
- `docker compose restart againspring-llm-dev` (또는 prod)

---

## 모니터링

`LLMCallLogger.logCall`이 `llm_call_logs` 테이블에 다음 기록:

| 필드 | 설명 |
|---|---|
| `correlation_id` | 호출 추적 (요청 헤더 X-Request-ID와 연동) |
| `provider` | `remote` |
| `session_id`, `turn_number` | 세션 컨텍스트 |
| `tokens_used` | Bridge가 추정 (`length / 4`) |
| `latency_ms` | invoke 시작 → 응답까지 경과 시간 |
| `outcome` | `success` / `fallback` / `timeout` / `error` |
| `error_code` | `LLMTimeoutException` / `LLMCapacityException` / 기타 |

**참고**: 프롬프트/응답 본문은 저장하지 않음 — 분량과 결과만 기록.

---

## 보안 체크리스트

- [x] 사용자 입력 PromptSanitizer 통과 강제
- [x] ProcessBuilder 사용 (셸 인젝션 방지) — `bash -c` 절대 금지
- [x] stderr 발췌(500자)만 저장 — 민감 정보 차단
- [x] `revokedTokens` 검사 후 인증된 사용자만 호출
- [x] 길이 제한 (입력 5000자)
- [x] 동시 처리 제한 (워커 ThreadPoolExecutor 100 + 대기 큐 500)
- [x] 응답 후처리 단계에서 KeywordGuard 재검사

---

## 트러블슈팅

| 증상 | 원인 | 조치 |
|---|---|---|
| `LLMTimeoutException` 빈발 | Claude Pro rate limit 또는 응답 지연 | `/v1/metrics` 확인; throttled↑ → 구독 한도; 워커 재시작 |
| 워커 `CAPACITY` 429 | 큐 500 포화 (극단적 버스트) | LLM_QUEUE_CAPACITY 상향 또는 요청 소스 조사 |
| `claude` 명령 없음 | 워커 이미지 빌드 실패 | 워커 Dockerfile `npm i -g @anthropic-ai/claude-code` 재빌드 |
| 401/세션 만료 | `~/.claude` 토큰 만료 | 호스트 `claude` 재로그인 → `docker compose restart againspring-llm-dev` |
| stdout 비어있음 | 프롬프트가 비기술 조언으로 거부 | system prompt가 NVC 재구성 형태로 프레임돼 있는지 확인 |
| 응답에 금지어 | LLM이 우회 시도 | KeywordGuard 응답 후처리에서 차단 — 정상 동작 |
| backend 워커 연결 실패 | 워커 미시작 또는 네트워크 분리 | `docker compose ps` → llm-dev healthy 여부 확인 |

### 긴급 롤백 (remote → claude-code 전환)

1. `.env.dev`에서 `LLM_PROVIDER=claude-code` 변경
2. backend 이미지를 Node+claude 포함 버전으로 재빌드 (git revert `backend/Dockerfile`)
3. `docker compose -f docker-compose.dev.yml up -d --build backend-dev`

---

## 향후 API 전환 계획

API 키 기반 전환 시:

```java
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "claude-api")
public class ClaudeAPIProvider implements LLMProvider {
  @Value("${llm.claude-api.key}") private String apiKey;
  // Anthropic SDK 사용
}
```

환경변수 변경만으로 전환 — 비즈니스 로직 코드 무수정.

---

## 프롬프트 파일 위치

정책 및 프롬프트 원본은 `shared/docs/prompts/` 하위:

- `system.md` — 시스템 역할 정의 (사용자 프로필 우선순위 포함)
- `gottman/*.md` — Gottman 부부 치료 체계
- `nvc/*.md` — 비폭력 대화 (NVC) 프레임워크
- `relations/*.md` — 관계 유형별 고정 콘텍스트
- `chat/{solo,duo}_chat.md`, `chat/{solo,duo}_report.md`, `chat/_response_instructions.md` — V1.5 카톡식
- `profiles/profile_template.md` — 사용자 프로필 블록 가이드
- `turns/*.md` — 레거시 6턴 모델 (V1.5 이후 미사용)

상세 레이어 설계: `shared/docs/prompts/README.md`.

---

## LLM 호출 취소 메커니즘

### 흐름 (remote 모드)

1. `POST /messages` → `CancelableChatService.acceptUserMessage()` (<100ms) → 사용자 메시지만 DB 저장
2. `beginInvocation()` → `RemoteLlmProvider.invokeCancelable()` → `POST /v1/invocations` → `invocationId`
3. `RemoteLlmProvider.poller`가 `GET /v1/invocations/{id}/result?waitMs=25000` long-poll
4. 새 메시지 도착 → `prevInvocation.cancel()` → `RemoteCancelableInvocation.cancel()` → `DELETE /v1/invocations/{id}` (워커 `destroyForcibly`) + `resultFuture.completeExceptionally`
5. FE `GET /messages?since=` (3초 주기) → 완료된 mediator 응답 수신

### 취소 클래스 계층

```
CancelableInvocation (base)
  └── RemoteCancelableInvocation (remote 모드)
        cancel() → markCanceled() + DELETE /v1/invocations/{id} (best-effort) + completeExceptionally
```

`CancelableChatService`는 `CancelableInvocation` API만 사용 → **무수정**.

---

## V1.5 응답 형식 (chat 흐름 전용)

V1.5 카톡식 응답은 본문 + 메타 블록의 두 파트로 구성:

```
[한국어 응답 텍스트, 1~3문장]

<turn_meta>
{
  "horsemen": {"criticism": 0.0, "contempt": 0.0, "defensiveness": 0.0, "stonewalling": 0.0},
  "nvc_completion": {"observation": false, "feeling": false, "need": false, "request": false}
}
</turn_meta>
```

`ChatTurnMetaParser` (`service/parser/ChatTurnMetaParser.java`):
- 본문과 `<turn_meta>` JSON 블록을 분리
- 강도값은 `[0,1]`로 클램핑
- `<mediator_response>...</mediator_response>` 래퍼가 있으면 풀어서 본문만 추출
- 메타 누락·malformed JSON은 graceful fallback (본문만 사용)

추출된 메타는 `Session.horsemenHistory` / `Session.nvcCompletionHistory`에 append되고, 다음 턴 프롬프트의 `<psychology_feedback>` / `<duo_balance>` 블록 입력으로 사용됨.

**리포트 흐름**(`ReportGenerationService`)은 별도의 JSON 응답 형식을 사용 — `ReportResponseParser` 참조.

---

**마지막 업데이트**: 2026-05-17
