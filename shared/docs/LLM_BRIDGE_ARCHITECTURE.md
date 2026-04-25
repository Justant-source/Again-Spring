# LLM 브릿지 아키텍처 — Claude Code as LLM Provider

**대상**: Claude Code (백엔드 개발자 역할)
**목적**: Spring Boot 백엔드에서 Claude Code CLI를 LLM으로 활용하는 방법
**핵심 원칙**: **API 과금 발생 금지**, Claude Pro/Max 구독의 Claude Code만 사용

---

## 🎯 설계 목표

1. **추상화**: LLM Provider를 인터페이스로 분리해 나중에 API로 교체 가능
2. **성능**: 프로세스 fork 비용 최소화 (워커 풀 패턴)
3. **안정성**: Claude Code 세션 장애 시 fallback, 재시도 로직
4. **보안**: 사용자 입력이 시스템 프롬프트 탈취 시도 차단
5. **관찰 가능성**: 호출 로그, 응답 시간, 실패율 측정

---

## ⚠️ Claude Code 브릿지의 현실적 제약

### 제약 1: Rate Limit
- Claude Pro/Max 구독의 사용량 한도를 공유함
- 대략 5시간에 수백 턴 정도 (구독 티어에 따라 다름)
- **MVP 단계 → 소규모 사용자 테스트까지만 현실적**
- 실제 서비스 런칭 시 **반드시 Claude API로 전환**

### 제약 2: 이용약관 확인
- Claude Code의 이용약관상 프로덕션 서비스 중계 용도가 허용되는지 Anthropic 공식 문서 확인 필요
- **개발/학습 용도 기준으로 설계**하고, 실제 상용화 전 Anthropic과 협의

### 제약 3: 세션 상태
- Claude Code는 CLI 세션 기반이라 context 관리가 필요
- 각 사용자 세션마다 별도 프로세스를 띄우면 메모리 급증
- **해결책: Headless 모드(`claude -p`) + 매 호출마다 프롬프트 전체 전달**

### 제약 4: 응답 지연
- 프로세스 실행 + Claude 응답 시간 합산해 3-10초 예상
- 사용자 경험상 **"중재자가 생각 중이에요" 로딩 UI** 필수
- 실시간 타이핑 효과로 체감 지연 완화

---

## 🏗️ 아키텍처 다이어그램

```
┌─────────────────┐      HTTP       ┌──────────────────────┐
│  Frontend       │ ──────────────> │  Spring Boot API     │
│  (Next.js)      │                 │                      │
└─────────────────┘ <────────────── │  ┌────────────────┐  │
                      SSE/Polling   │  │ MediationSvc   │  │
                                    │  └────────────────┘  │
                                    │         ↓            │
                                    │  ┌────────────────┐  │
                                    │  │ LLMProvider    │  │
                                    │  │ <interface>    │  │
                                    │  └────────────────┘  │
                                    │         ↓            │
                                    │  ┌────────────────┐  │
                                    │  │ ClaudeCode     │  │
                                    │  │ Bridge         │  │
                                    │  └────────────────┘  │
                                    │         ↓            │
                                    │  ┌────────────────┐  │
                                    │  │ Worker Pool    │  │
                                    │  │ (Process Mgmt) │  │
                                    │  └────────────────┘  │
                                    └──────────┬───────────┘
                                               │
                                               │ ProcessBuilder
                                               ↓
                                    ┌──────────────────────┐
                                    │  Claude CLI Process  │
                                    │  (Subprocess)        │
                                    │  $ claude -p "..."   │
                                    └──────────────────────┘
```

---

## 📐 Provider 추상화 인터페이스

### `LLMProvider` 인터페이스

```java
package com.againspring.llm;

public interface LLMProvider {
    /**
     * 단일 프롬프트에 대한 응답 생성
     */
    LLMResponse complete(LLMRequest request);
    
    /**
     * 스트리밍 응답 (SSE용)
     */
    Flux<String> stream(LLMRequest request);
    
    /**
     * Provider 상태 확인 (health check)
     */
    boolean isHealthy();
    
    /**
     * Provider 이름 (로깅/모니터링용)
     */
    String getProviderName();
}
```

### `LLMRequest` DTO

```java
@Data
@Builder
public class LLMRequest {
    private String systemPrompt;       // 시스템 프롬프트 (Gottman + NVC 원칙)
    private String userPrompt;         // 사용자 입력 또는 턴별 태스크
    private List<PromptContext> context; // RAG로 주입할 컨텍스트 (Gottman 지식)
    private Integer maxTokens;
    private Double samplingTemperature; // LLM 생성 다양성 파라미터 (0.0 ~ 1.0)
    private String sessionId;          // 추적용 ID
    private String taskType;           // "turn_3_a", "final_report" 등
    private Duration timeout;          // 기본 30초
}
```

### `LLMResponse` DTO

```java
@Data
@Builder
public class LLMResponse {
    private String content;
    private String providerName;
    private Duration elapsed;
    private Map<String, Object> metadata;
    private boolean success;
    private String errorMessage;
}
```

---

## 🔌 `ClaudeCodeBridge` 구현

### 핵심 클래스

```java
package com.againspring.llm.bridge;

@Slf4j
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "claude-code")
public class ClaudeCodeBridge implements LLMProvider {
    
    private final ClaudeCodeWorkerPool workerPool;
    private final PromptSanitizer sanitizer;
    private final LLMCallLogger logger;
    
    @Value("${llm.claude-code.binary-path:claude}")
    private String claudeBinaryPath;
    
    @Value("${llm.claude-code.default-timeout:30000}")
    private long defaultTimeoutMs;
    
    @Override
    public LLMResponse complete(LLMRequest request) {
        Instant start = Instant.now();
        
        // 1. 프롬프트 살균 (프롬프트 인젝션 방지)
        String safeUserPrompt = sanitizer.sanitize(request.getUserPrompt());
        
        // 2. 최종 프롬프트 조립
        String finalPrompt = assembleFinalPrompt(
            request.getSystemPrompt(),
            request.getContext(),
            safeUserPrompt
        );
        
        // 3. 워커에게 실행 위탁
        try {
            String output = workerPool.execute(finalPrompt, request.getTimeout());
            
            Duration elapsed = Duration.between(start, Instant.now());
            logger.logSuccess(request, output, elapsed);
            
            return LLMResponse.builder()
                .content(output)
                .providerName(getProviderName())
                .elapsed(elapsed)
                .success(true)
                .build();
                
        } catch (ClaudeCodeTimeoutException e) {
            logger.logTimeout(request);
            return errorResponse("TIMEOUT", e.getMessage(), start);
            
        } catch (ClaudeCodeRateLimitException e) {
            logger.logRateLimit(request);
            return errorResponse("RATE_LIMIT", e.getMessage(), start);
            
        } catch (Exception e) {
            logger.logError(request, e);
            return errorResponse("UNKNOWN", e.getMessage(), start);
        }
    }
    
    @Override
    public String getProviderName() {
        return "claude-code";
    }
    
    // ... 나머지 구현
}
```

---

## 🏊 Worker Pool 패턴

### 왜 워커 풀이 필요한가

**나쁜 방식** (매 요청마다 프로세스 fork):
```java
// ❌ 요청마다 Claude CLI를 새로 실행
Process p = new ProcessBuilder("claude", "-p", prompt).start();
```
- 프로세스 생성 비용 1-2초 추가
- JVM 메모리 2배 사용 (fork 시점)
- 동시 요청 수 증가 시 서버 다운

**좋은 방식** (워커 풀):
```java
// ✅ Claude CLI 프로세스를 미리 띄워놓고 재사용
// 또는 매 호출마다 -p (headless) 모드로 실행하되 동시성 제한
```

### Worker Pool 구현

```java
@Component
public class ClaudeCodeWorkerPool {
    
    @Value("${llm.claude-code.pool-size:3}")
    private int poolSize;
    
    private final Semaphore concurrencyLimit;
    private final ExecutorService executor;
    
    @PostConstruct
    public void init() {
        this.concurrencyLimit = new Semaphore(poolSize);
        this.executor = Executors.newFixedThreadPool(poolSize, 
            new ThreadFactoryBuilder().setNameFormat("claude-worker-%d").build()
        );
    }
    
    public String execute(String prompt, Duration timeout) 
            throws ClaudeCodeException {
        
        // 동시 실행 수 제한
        if (!concurrencyLimit.tryAcquire(5, TimeUnit.SECONDS)) {
            throw new ClaudeCodeRateLimitException("Pool exhausted");
        }
        
        try {
            Future<String> future = executor.submit(() -> runClaudeCommand(prompt));
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            
        } catch (TimeoutException e) {
            throw new ClaudeCodeTimeoutException("Claude Code timeout");
        } catch (Exception e) {
            throw new ClaudeCodeException("Execution failed", e);
        } finally {
            concurrencyLimit.release();
        }
    }
    
    private String runClaudeCommand(String prompt) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            "claude", "-p", prompt,
            "--output-format", "text"
        );
        pb.redirectErrorStream(false);
        
        Process process = pb.start();
        
        // stdout 읽기
        String output = new String(process.getInputStream().readAllBytes());
        
        // 종료 대기 (비정상 종료 체크)
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String err = new String(process.getErrorStream().readAllBytes());
            throw new ClaudeCodeException("Exit " + exitCode + ": " + err);
        }
        
        return output;
    }
}
```

### 풀 사이즈 결정 기준

| 환경 | 풀 사이즈 | 동시 세션 수 |
|---|---|---|
| 개발 | 1 | 1 |
| 테스트 | 2 | 2 |
| 초기 MVP | 3 | 3 |
| 소규모 베타 | 5 | 5 |

**풀 사이즈를 무작정 늘리면 안 되는 이유**:
- Claude Pro 구독의 rate limit 공유
- 서버 메모리/CPU 자원 제한
- 동시 너무 많은 요청 시 Claude 측 응답 지연

---

## 🛡️ 프롬프트 인젝션 방지

사용자가 입력한 텍스트가 시스템 프롬프트를 탈취하거나 지침을 우회하지 못하도록 **Sanitizer** 적용.

```java
@Component
public class PromptSanitizer {
    
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
        Pattern.compile("(?i)ignore (previous|above|all) instructions"),
        Pattern.compile("(?i)you are now"),
        Pattern.compile("(?i)system prompt"),
        Pattern.compile("(?i)</system>"),
        Pattern.compile("(?i)<system>"),
        Pattern.compile("(?i)new role:"),
        Pattern.compile("(?i)forget everything"),
        Pattern.compile("(?i)disregard"),
        Pattern.compile("(?i)override")
    );
    
    public String sanitize(String userInput) {
        if (userInput == null) return "";
        
        // 1. 길이 제한
        if (userInput.length() > 5000) {
            userInput = userInput.substring(0, 5000);
        }
        
        // 2. 인젝션 패턴 탐지
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(userInput).find()) {
                log.warn("Potential prompt injection detected: {}", 
                         pattern.pattern());
                userInput = pattern.matcher(userInput).replaceAll("[REDACTED]");
            }
        }
        
        // 3. 특수 구분자 제거
        userInput = userInput.replaceAll("\\[INST\\]", "");
        userInput = userInput.replaceAll("\\[/INST\\]", "");
        
        return userInput;
    }
}
```

---

## 📋 프롬프트 조립 전략

### 최종 프롬프트 구조

Claude Code CLI에 전달되는 최종 프롬프트:

```
<system>
{시스템 프롬프트 — Gottman + NVC 원칙, 금기사항}
</system>

<context>
{Gottman 이론 RAG 청크 — 관련된 것만 동적 주입}
{NVC 템플릿}
{관계 유형별 가이드}
</context>

<session_info>
Session ID: {session_id}
Turn: {turn_number}
Task: {task_type}
Conflict Category: {major} > {middle} > {minor}
</session_info>

<user_input>
{살균된 사용자 입력}
</user_input>

<task>
{턴별 태스크 지시 — shared/prompts/turns/에서 로드}
</task>
```

### Prompt Loader 구현

```java
@Component
public class PromptLoader {
    
    @Value("${app.prompts.path:/opt/againspring/shared/prompts}")
    private String promptsPath;
    
    private final Map<String, String> promptCache = new ConcurrentHashMap<>();
    
    public String loadSystemPrompt() {
        return loadFromFile("system.md");
    }
    
    public String loadTurnPrompt(int turnNumber, ParticipantRole role) {
        String filename = String.format("turns/turn_%d_%s.md", 
            turnNumber, role.name().toLowerCase());
        return loadFromFile(filename);
    }
    
    public String loadGottmanKnowledge(String topic) {
        return loadFromFile("gottman/" + topic + ".md");
    }
    
    private String loadFromFile(String filename) {
        return promptCache.computeIfAbsent(filename, key -> {
            try {
                Path path = Paths.get(promptsPath, key);
                return Files.readString(path);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load prompt: " + key, e);
            }
        });
    }
    
    // 개발용: 캐시 무효화
    public void invalidateCache() {
        promptCache.clear();
    }
}
```

---

## 🧩 MediationService에서 사용

```java
@Service
@RequiredArgsConstructor
public class MediationService {
    
    private final LLMProvider llmProvider;
    private final PromptLoader promptLoader;
    private final SessionRepository sessionRepo;
    
    public TurnResponse processTurn(ProcessTurnCommand cmd) {
        Session session = sessionRepo.findById(cmd.getSessionId())
            .orElseThrow();
        
        // 프롬프트 조립
        String systemPrompt = promptLoader.loadSystemPrompt();
        String turnTask = promptLoader.loadTurnPrompt(
            cmd.getTurnNumber(), cmd.getRole());
        
        LLMRequest request = LLMRequest.builder()
            .systemPrompt(systemPrompt)
            .userPrompt(buildUserPrompt(session, cmd, turnTask))
            .sessionId(session.getId())
            .taskType("turn_" + cmd.getTurnNumber() + "_" + cmd.getRole())
            .timeout(Duration.ofSeconds(30))
            .build();
        
        // LLM 호출
        LLMResponse response = llmProvider.complete(request);
        
        if (!response.isSuccess()) {
            // 재시도 또는 fallback 메시지
            return handleFailure(cmd, response);
        }
        
        // 응답 후처리 (금지어 검증, 출력 형식 확인)
        String cleaned = postProcess(response.getContent());
        
        // 세션에 턴 저장
        session.addTurn(new Turn(cmd, cleaned));
        sessionRepo.save(session);
        
        return new TurnResponse(cleaned);
    }
    
    private String postProcess(String rawOutput) {
        // 금지어 검증
        // 출력 포맷 파싱 (JSON 추출 등)
        // NVC 구조 검증
        return rawOutput;
    }
}
```

---

## 🔄 향후 API 전환 전략

실제 상용화 시 Claude API로 전환이 필요합니다. 이때 코드 변경 최소화를 위해:

### `ClaudeAPIProvider` (향후 구현)

```java
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "claude-api")
public class ClaudeAPIProvider implements LLMProvider {
    
    @Value("${llm.claude-api.key}")
    private String apiKey;
    
    private final WebClient webClient;
    
    @Override
    public LLMResponse complete(LLMRequest request) {
        // Anthropic SDK 또는 WebClient로 API 호출
        // ...
    }
    
    @Override
    public String getProviderName() {
        return "claude-api";
    }
}
```

### 환경변수로 전환

```yaml
# application.yml (개발)
llm:
  provider: claude-code
  claude-code:
    binary-path: /usr/local/bin/claude
    pool-size: 3

# application-prod.yml (프로덕션)
llm:
  provider: claude-api
  claude-api:
    key: ${ANTHROPIC_API_KEY}
    model: claude-sonnet-4-5
```

**Spring의 `@ConditionalOnProperty`로 어느 Provider가 주입될지 결정**. 비즈니스 로직 코드는 전혀 바뀌지 않음.

---

## 📊 모니터링 및 로깅

### 필수 로깅 항목

```java
@Component
public class LLMCallLogger {
    
    public void logSuccess(LLMRequest request, String output, Duration elapsed) {
        log.info("LLM_CALL | provider={} | session={} | task={} | " +
                 "prompt_len={} | response_len={} | elapsed_ms={}",
            request.getProviderName(),
            request.getSessionId(),
            request.getTaskType(),
            request.getUserPrompt().length(),
            output.length(),
            elapsed.toMillis()
        );
    }
    
    public void logTimeout(LLMRequest request) { /* ... */ }
    public void logRateLimit(LLMRequest request) { /* ... */ }
    public void logError(LLMRequest request, Throwable e) { /* ... */ }
}
```

### 메트릭 (Prometheus)

```java
@Component
public class LLMMetrics {
    
    private final MeterRegistry registry;
    
    public void recordCall(String provider, String taskType, 
                          Duration elapsed, boolean success) {
        registry.timer("llm.call.duration",
            "provider", provider,
            "task", taskType,
            "success", String.valueOf(success)
        ).record(elapsed);
        
        registry.counter("llm.call.total",
            "provider", provider,
            "success", String.valueOf(success)
        ).increment();
    }
}
```

---

## 🚨 Fallback 전략

Claude Code가 실패했을 때:

### 수준 1: 재시도 (최대 2회)
```java
@Retryable(
    value = {ClaudeCodeTimeoutException.class, IOException.class},
    maxAttempts = 2,
    backoff = @Backoff(delay = 2000, multiplier = 1.5)
)
public LLMResponse complete(LLMRequest request) { /* ... */ }
```

### 수준 2: 기본 응답 반환
실패 시 미리 준비된 **안전한 기본 응답** 반환:
```java
private LLMResponse getFallbackResponse(String taskType) {
    // 턴별 기본 응답
    Map<String, String> fallbacks = Map.of(
        "turn_3_a", "지금 답변이 어렵네요. 잠시 후 다시 시도해주세요.",
        "final_report", "분석 중 오류가 발생했어요. 고객센터로 문의해주세요."
    );
    return LLMResponse.builder()
        .content(fallbacks.getOrDefault(taskType, "처리 중입니다..."))
        .success(false)
        .build();
}
```

### 수준 3: 세션 일시 정지
사용자에게 "잠시 후 다시 시도" 알림 표시, 세션 상태를 `paused`로 변경.

---

## 🧪 테스트 전략

### 단위 테스트: Mock Provider

```java
@Configuration
@Profile("test")
public class TestLLMConfig {
    @Bean
    public LLMProvider mockLLMProvider() {
        return new MockLLMProvider();
    }
}

public class MockLLMProvider implements LLMProvider {
    @Override
    public LLMResponse complete(LLMRequest request) {
        // 고정 응답 반환
        return LLMResponse.builder()
            .content(loadFixture(request.getTaskType()))
            .success(true)
            .build();
    }
}
```

### 통합 테스트: 실제 Claude Code 호출

```java
@SpringBootTest
@ActiveProfiles("integration")
public class ClaudeCodeBridgeIntegrationTest {
    
    @Autowired
    private LLMProvider llmProvider;
    
    @Test
    @EnabledIfEnvironmentVariable(named = "CLAUDE_CODE_AVAILABLE", matches = "true")
    public void testRealClaudeCall() {
        LLMRequest request = LLMRequest.builder()
            .systemPrompt("당신은 중재자입니다.")
            .userPrompt("안녕하세요")
            .timeout(Duration.ofSeconds(30))
            .build();
        
        LLMResponse response = llmProvider.complete(request);
        
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getContent()).isNotBlank();
    }
}
```

---

## 🔒 보안 체크리스트

- [ ] 사용자 입력 Sanitizer 필수 적용
- [ ] 프롬프트에 API 키·비밀번호·개인정보 포함 금지
- [ ] Claude CLI 실행 시 셸 인젝션 방지 (ProcessBuilder 사용, `bash -c` 금지)
- [ ] stdout/stderr 로그에 민감 정보 마스킹
- [ ] Claude Code 실행 권한 최소화 (전용 user로 실행)
- [ ] 세션 원문 DB 저장 시 암호화 (30일 TTL)

---

## 📁 관련 파일 구조

```
backend/src/main/java/com/againspring/llm/
├── LLMProvider.java              # 인터페이스
├── LLMRequest.java
├── LLMResponse.java
├── bridge/
│   ├── ClaudeCodeBridge.java     # 메인 브릿지
│   ├── ClaudeCodeWorkerPool.java
│   ├── PromptSanitizer.java
│   └── exception/
│       ├── ClaudeCodeException.java
│       ├── ClaudeCodeTimeoutException.java
│       └── ClaudeCodeRateLimitException.java
├── api/
│   └── ClaudeAPIProvider.java    # 향후 구현 (주석 처리)
├── prompt/
│   ├── PromptLoader.java
│   └── PromptAssembler.java
├── monitoring/
│   ├── LLMCallLogger.java
│   └── LLMMetrics.java
└── fallback/
    └── FallbackResponses.java
```

---

## ✅ Claude Code 구현 체크리스트

### Phase A: 기본 구조
- [ ] `LLMProvider` 인터페이스 정의
- [ ] `LLMRequest`, `LLMResponse` DTO 작성
- [ ] 예외 클래스 3종 작성

### Phase B: 워커 풀
- [ ] `ClaudeCodeWorkerPool` 구현
- [ ] `Semaphore` 기반 동시성 제어
- [ ] 타임아웃 처리
- [ ] ProcessBuilder 실행 및 결과 수집

### Phase C: 브릿지 본체
- [ ] `ClaudeCodeBridge` 클래스 구현
- [ ] `@ConditionalOnProperty` 설정
- [ ] `PromptSanitizer` 인젝션 방지

### Phase D: 프롬프트 관리
- [ ] `PromptLoader` 구현 (파일 캐싱)
- [ ] `PromptAssembler` 구현 (레이어 조립)
- [ ] `shared/prompts/` 구조 생성

### Phase E: 모니터링
- [ ] `LLMCallLogger` 구현
- [ ] Micrometer 메트릭 추가 (선택)

### Phase F: 테스트
- [ ] `MockLLMProvider` 작성
- [ ] 단위 테스트 작성
- [ ] 통합 테스트 (실제 Claude Code 호출)

### Phase G: Fallback
- [ ] 재시도 로직 (`@Retryable`)
- [ ] Fallback 응답 준비
- [ ] 실패 시 사용자 알림 처리

---

**끝.**
