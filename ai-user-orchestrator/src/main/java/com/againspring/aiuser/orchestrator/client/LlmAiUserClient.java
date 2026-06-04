package com.againspring.aiuser.orchestrator.client;

import com.againspring.aiuser.orchestrator.client.dto.GenDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Slf4j
@Component
public class LlmAiUserClient {

    private final RestClient restClient;

    public LlmAiUserClient(@Qualifier("llmAiUserRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public Optional<String> generatePost(GenDto.PostRequest req) {
        return generate("/generate/post", req);
    }

    public Optional<String> generateComment(GenDto.CommentRequest req) {
        return generate("/generate/comment", req);
    }

    public Optional<String> generateReply(GenDto.ReplyRequest req) {
        return generate("/generate/reply", req);
    }

    private Optional<String> generate(String path, Object req) {
        try {
            GenDto.Response resp = restClient.post()
                .uri(path)
                .body(req)
                .retrieve()
                .body(GenDto.Response.class);
            if (resp != null && resp.isSuccess()) {
                return Optional.of(resp.getText());
            }
            if (resp != null && resp.getError() != null) {
                log.warn("Gen error on {}: type={} msg={}", path, resp.getErrorType(), resp.getError());
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("LlmAiUser call failed on {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }
}
