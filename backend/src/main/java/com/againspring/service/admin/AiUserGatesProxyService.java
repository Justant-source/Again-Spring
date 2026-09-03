package com.againspring.service.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Proxies admin "생성/발행이 왜 막혀있는지" 조회를 ai-user orchestrator에 위임한다.
 * orchestrator가 env/yml/DB/LLM 게이트를 한 번에 해석해 반환한 JSON을 그대로 통과시킨다
 * (키: generationAllowed/publishingAllowed/reasons/gates[]/stale). orchestrator는 Docker
 * 내부망 전용이라 공개 ADMIN JWT는 이 backend 프록시에서 검사한다.
 */
@Slf4j
@Service
public class AiUserGatesProxyService {

    private final String orchestratorUrl;
    private final RestClient restClient;

    @Autowired
    public AiUserGatesProxyService(
            @Value("${ai.user.orchestrator-url:http://againspring-ai-user-orchestrator:8096}") String orchestratorUrl) {
        this(orchestratorUrl, RestClient.create());
    }

    /** Test-friendly constructor. */
    AiUserGatesProxyService(String orchestratorUrl, RestClient restClient) {
        this.orchestratorUrl = orchestratorUrl;
        this.restClient = restClient;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> effectiveGates() {
        try {
            Map<?, ?> body = restClient.get()
                    .uri(orchestratorUrl + "/admin/trigger/effective-gates")
                    .retrieve()
                    .body(Map.class);
            if (body == null) throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "empty response");
            return (Map<String, Object>) body;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("[effective-gates] failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "orchestrator unavailable");
        }
    }
}
