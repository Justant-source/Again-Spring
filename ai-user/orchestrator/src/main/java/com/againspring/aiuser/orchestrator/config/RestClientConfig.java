package com.againspring.aiuser.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

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
}
