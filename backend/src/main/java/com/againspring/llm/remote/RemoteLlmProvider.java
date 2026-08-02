package com.againspring.llm.remote;

import com.againspring.common.exception.BusinessException;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.remote.dto.WorkerInvokeRequest;
import com.againspring.llm.remote.dto.WorkerInvokeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

/**
 * LLM Provider — againspring-llm 워커 HTTP 브릿지.
 * 커뮤니티 배심원(JuryService) + 사연 중립화(PostComposeService) 전용.
 *
 * {@code llm.enabled=false} (server-dev L3)이면 워커 호출 없이 명시 거절(X1).
 */
@Slf4j
@Primary
@Component("remoteLlmProvider")
public class RemoteLlmProvider implements LLMProvider {

    private static final String DISABLED_MESSAGE =
            "dev 환경에서는 LLM을 사용하지 않습니다. AI 생성은 prod에서만 수행되고, 결과는 prod→dev sync로 반영됩니다.";

    private final RestClient restClient;
    private final long defaultTimeoutMs;
    private final String defaultModel;
    private final boolean enabled;

    public RemoteLlmProvider(
            @Value("${llm.remote.base-url:http://againspring-llm:8090}") String workerBaseUrl,
            @Value("${llm.remote.default-timeout-ms:120000}") long defaultTimeoutMs,
            @Value("${llm.claude-code.model:claude-haiku-4-5-20251001}") String defaultModel,
            @Value("${llm.enabled:true}") boolean enabled) {
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.defaultModel = defaultModel;
        this.enabled = enabled;
        this.restClient = RestClient.builder().baseUrl(workerBaseUrl).build();
    }

    @Override
    public String invoke(String prompt, String model) throws Exception {
        if (!enabled) {
            log.warn("[remote-llm] blocked: llm.enabled=false");
            throw new BusinessException("LLM_DISABLED", DISABLED_MESSAGE, 501);
        }
        String correlationId = UUID.randomUUID().toString();
        try {
            WorkerInvokeRequest req = WorkerInvokeRequest.builder()
                    .prompt(prompt)
                    .model(model != null ? model : defaultModel)
                    .timeoutMs(defaultTimeoutMs)
                    .build();
            WorkerInvokeResponse resp = restClient.post()
                    .uri("/v1/invoke")
                    .body(req)
                    .retrieve()
                    .body(WorkerInvokeResponse.class);
            return resp != null ? resp.getText() : "";
        } catch (Exception e) {
            log.error("[remote-llm] invoke failed correlationId={}: {}", correlationId, e.getMessage());
            throw e;
        }
    }

    @Override
    public String getProviderName() { return enabled ? "remote" : "disabled"; }

    @Override
    public boolean isHealthy() {
        if (!enabled) {
            return false;
        }
        try {
            restClient.get().uri("/actuator/health").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
