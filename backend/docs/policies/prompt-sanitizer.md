# PromptSanitizer

LLM에 전달되기 전 **사용자 입력**을 정화. Prompt injection · 시스템 프롬프트 탈취 시도 차단.

## Source of truth

- `backend/src/main/java/com/againspring/llm/bridge/PromptSanitizer.java`
- 사용처: `ClaudeCodeBridge.invoke()` — 모든 LLM 호출이 이 단계를 통과

## 차단 패턴

```java
private static final List<Pattern> INJECTION_PATTERNS = List.of(
    Pattern.compile("(?i)ignore (previous|above|all) instructions"),
    Pattern.compile("(?i)you are now"),
    Pattern.compile("(?i)system prompt"),
    Pattern.compile("(?i)</?system>"),
    Pattern.compile("(?i)new role:"),
    Pattern.compile("(?i)forget everything"),
    Pattern.compile("(?i)disregard"),
    Pattern.compile("(?i)override")
);
```

## 동작

```mermaid
flowchart TD
    A["사용자 입력\n(raw)"] --> B{길이 > 5000자?}
    B -->|예| C["substring(0, 5000) 잘라냄"]
    B -->|아니오| D
    C --> D{INJECTION_PATTERNS\n정규식 매치?}
    D -->|매치됨| E["WARN 로그\n+ [REDACTED] 치환\ncorrelationId 기록"]
    D -->|매치 없음| F
    E --> F{특수 구분자\n[INST][/INST] 존재?}
    F -->|예| G["replaceAll 제거"]
    F -->|아니오| H
    G --> H["정화된 입력\n→ LLM 프롬프트 조립"]
```

```java
public String sanitize(String userInput, String correlationId) {
    if (userInput == null) return "";
    
    // 1. 길이 제한
    if (userInput.length() > 5000) {
        userInput = userInput.substring(0, 5000);
    }
    
    // 2. 인젝션 패턴 매치 시 [REDACTED]로 치환
    for (Pattern p : INJECTION_PATTERNS) {
        if (p.matcher(userInput).find()) {
            log.warn("Prompt injection pattern matched: corrId={}, pattern={}",
                     correlationId, p.pattern());
            userInput = p.matcher(userInput).replaceAll("[REDACTED]");
        }
    }
    
    // 3. 특수 구분자 제거
    userInput = userInput.replaceAll("\\[INST\\]", "");
    userInput = userInput.replaceAll("\\[/INST\\]", "");
    
    return userInput;
}
```

## 정책

- 매치 시 `LLMSanitizationException`을 던지지 않고 **redaction** — 정상 사용자가 우연히 패턴 단어를 쓸 수 있음
- 매치는 모두 `WARN` 로그 + `correlationId` 기록 (사후 분석)
- 5000자 초과 입력은 잘라냄 (LLM 토큰 비용·지연 방지)

## 한계

- 한국어 인젝션 시도 (예: "이전 지침 무시해줘")는 현재 패턴에 없음
- 의도적으로 우회하는 사용자는 완전히 막을 수 없음
- → **응답 단계의 KeywordGuard 후처리**가 두 번째 방어선

## 변경 시

1. `INJECTION_PATTERNS` 추가/제거 → 단위 테스트 갱신 (`PromptSanitizerTest`, 100% 커버리지 유지)
2. 길이 제한 변경 → token 비용 영향 검토
3. 한국어 패턴 추가 시 사용자 일반 입력에 false-positive 생기지 않도록 신중

## 관련

- 응답 후처리: [keyword-guard.md](./keyword-guard.md)
- 위기 키워드: `shared/docs/policies/crisis-detection.md`
- LLM 호출: `shared/docs/llm/bridge-architecture.md`
