package com.againspring.llm.monitoring;

import com.againspring.domain.relationship.LlmCallLog;
import com.againspring.llm.LLMRequest;
import com.againspring.llm.LLMResponse;
import com.againspring.repository.LlmCallLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LLM 호출 메트릭 기록 — SLF4J 로그 + MariaDB 영구저장.
 * 프롬프트 내용·사용자 입력은 절대 기록하지 않으며, 메트릭·길이만 기록.
 *
 * cache_read_tokens / cache_creation_tokens 는 claude-api provider 경로에서만 채워짐.
 * CLI(remote) 경로는 NULL 허용.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LLMCallLogger {

    private final LlmCallLogRepository llmCallLogRepository;

    /**
     * LLM 호출 결과 기록.
     * response != null → 성공(또는 fallback), error != null → 실패.
     */
    public void logCall(LLMRequest request, LLMResponse response, Throwable error) {
        if (response != null) {
            int layerCount  = request.getLayers()    != null ? request.getLayers().size()   : 0;
            int inputLength = request.getUserInput() != null ? request.getUserInput().length() : 0;
            int outputLength = response.getRawText() != null ? response.getRawText().length() : 0;

            log.info("LLM_CALL provider={} correlationId={} tokens={} latencyMs={} " +
                            "layers={} inputLen={} outputLen={} fallback={} " +
                            "inputTokens={} outputTokens={} cacheRead={} cacheCreation={}",
                    response.getProvider(),
                    response.getCorrelationId(),
                    response.getTokensUsed(),
                    response.getLatencyMs(),
                    layerCount,
                    inputLength,
                    outputLength,
                    response.isFallback(),
                    response.getInputTokens(),
                    response.getOutputTokens(),
                    response.getCacheReadTokens(),
                    response.getCacheCreationTokens());

            persistSuccessLog(request, response, inputLength, outputLength);

        } else if (error != null) {
            String errorCode = error.getClass().getSimpleName();
            int inputLength = request.getUserInput() != null ? request.getUserInput().length() : 0;

            log.warn("LLM_CALL_FAILED correlationId={} errorCode={} errorMsg={} inputLen={}",
                    request.getCorrelationId(),
                    errorCode,
                    error.getMessage(),
                    inputLength);

            persistErrorLog(request, errorCode, inputLength);
        }
    }

    // -------------------------------------------------------------------------

    private void persistSuccessLog(LLMRequest request, LLMResponse response,
                                   int inputLength, int outputLength) {
        try {
            LlmCallLog entity = LlmCallLog.builder()
                    .correlationId(response.getCorrelationId())
                    .provider(response.getProvider())
                    .sessionId(extractSessionId(request))
                    .tokensUsed(response.getTokensUsed())
                    .latencyMs(response.getLatencyMs())
                    .inputLength(inputLength)
                    .outputLength(outputLength)
                    .outcome(response.isFallback() ? "fallback" : "success")
                    .cacheReadTokens(response.getCacheReadTokens())
                    .cacheCreationTokens(response.getCacheCreationTokens())
                    .inputTokens(response.getInputTokens())
                    .outputTokens(response.getOutputTokens())
                    .build();
            llmCallLogRepository.save(entity);
        } catch (Exception e) {
            log.warn("[LLMCallLogger] DB 저장 실패 (서비스 영향 없음): {}", e.getMessage());
        }
    }

    private void persistErrorLog(LLMRequest request, String errorCode, int inputLength) {
        try {
            LlmCallLog entity = LlmCallLog.builder()
                    .correlationId(request.getCorrelationId())
                    .provider(extractProvider(request))
                    .sessionId(extractSessionId(request))
                    .inputLength(inputLength)
                    .outcome("error")
                    .errorCode(errorCode)
                    .build();
            llmCallLogRepository.save(entity);
        } catch (Exception e) {
            log.warn("[LLMCallLogger] DB 저장 실패 (서비스 영향 없음): {}", e.getMessage());
        }
    }

    private String extractSessionId(LLMRequest request) {
        if (request == null || request.getMetadata() == null) return null;
        Object v = request.getMetadata().get("sessionId");
        return v != null ? v.toString() : null;
    }

    private String extractProvider(LLMRequest request) {
        if (request == null || request.getMetadata() == null) return null;
        Object v = request.getMetadata().get("provider");
        return v != null ? v.toString() : null;
    }
}
