package com.againspring.llm.remote;

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
 */
@Slf4j
@Primary
@Component("remoteLlmProvider")
public class RemoteLlmProvider implements LLMProvider {

    private final RestClient restClient;
    private final long defaultTimeoutMs;
    private final String defaultModel;

    public RemoteLlmProvider(
            @Value("${llm.remote.base-url:http://againspring-llm-dev:8090}") String workerBaseUrl,
            @Value("${llm.remote.default-timeout-ms:120000}") long defaultTimeoutMs,
            @Value("${llm.claude-code.model:claude-haiku-4-5-20251001}") String defaultModel) {
        this.defaultTimeoutMs = defaultTimeoutMs;
        this.defaultModel = defaultModel;
        this.restClient = RestClient.builder().baseUrl(workerBaseUrl).build();
    }

    @Override
    public String invoke(String prompt, String model) throws Exception {
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
    public String getProviderName() { return "remote"; }

    @Override
    public boolean isHealthy() {
        try {
            restClient.get().uri("/actuator/health").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
