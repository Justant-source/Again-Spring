package com.againspring.aiuser.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Configuration
public class RestClientConfig {

    @Bean("backendRestClient")
    public RestClient backendRestClient(OrchestratorProperties props) {
        return RestClient.builder()
            .baseUrl(props.getBackendBaseUrl())
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .build();
    }

    @Bean("llmAiUserRestClient")
    public RestClient llmAiUserRestClient(OrchestratorProperties props) {
        return RestClient.builder()
            .baseUrl(props.getLlmAiUserUrl())
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .build();
    }

    /** 보조 백엔드 RestClient. secondaryBackendBaseUrl이 비어있으면 Optional.empty() */
    @Bean("secondaryBackendRestClient")
    public Optional<RestClient> secondaryBackendRestClient(OrchestratorProperties props) {
        String url = props.getSecondaryBackendBaseUrl();
        if (url == null || url.isBlank()) return Optional.empty();
        RestClient rc = RestClient.builder()
            .baseUrl(url)
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .build();
        return Optional.of(rc);
    }
}
