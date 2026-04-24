package com.againspring.llm.monitoring;

import com.againspring.llm.LLMRequest;
import com.againspring.llm.LLMResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Logs LLM invocations in structured format.
 * Never logs prompt content or user input—only metrics and lengths.
 */
@Slf4j
@Component
public class LLMCallLogger {

    /**
     * Log a successful LLM call.
     * Format: correlationId, provider, tokensUsed, latencyMs, layerCount, inputLength, outputLength, fallback, errorCode
     */
    public void logCall(LLMRequest request, LLMResponse response, Throwable error) {
        if (response != null) {
            // Success case
            int layerCount = request.getLayers() != null ? request.getLayers().size() : 0;
            int inputLength = request.getUserInput() != null ? request.getUserInput().length() : 0;
            int outputLength = response.getRawText() != null ? response.getRawText().length() : 0;

            log.info("LLM_CALL provider={} correlationId={} tokens={} latencyMs={} " +
                            "layers={} inputLen={} outputLen={} fallback={}",
                    response.getProvider(),
                    response.getCorrelationId(),
                    response.getTokensUsed(),
                    response.getLatencyMs(),
                    layerCount,
                    inputLength,
                    outputLength,
                    response.isFallback());
        } else if (error != null) {
            // Error case
            String errorCode = error.getClass().getSimpleName();
            int inputLength = request.getUserInput() != null ? request.getUserInput().length() : 0;

            log.warn("LLM_CALL_FAILED correlationId={} errorCode={} errorMsg={} inputLen={}",
                    request.getCorrelationId(),
                    errorCode,
                    error.getMessage(),
                    inputLength);
        }
    }
}
