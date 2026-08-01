package com.againspring.service.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * Proxies admin pending thread-plan item CRUD to the ai-user orchestrator.
 */
@Slf4j
@Service
public class ThreadPlanItemProxyService {

    private final String orchestratorUrl;
    private final RestClient restClient;

    @Autowired
    public ThreadPlanItemProxyService(
            @Value("${ai.user.orchestrator-url:http://againspring-ai-user-orchestrator:8096}") String orchestratorUrl) {
        this(orchestratorUrl, RestClient.create());
    }

    ThreadPlanItemProxyService(String orchestratorUrl, RestClient restClient) {
        this.orchestratorUrl = orchestratorUrl;
        this.restClient = restClient;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listPending(String postId) {
        try {
            String url = UriComponentsBuilder
                    .fromUriString(orchestratorUrl + "/admin/thread-plan-items")
                    .queryParam("postId", postId)
                    .toUriString();
            List<?> body = restClient.get().uri(url).retrieve().body(List.class);
            return body == null ? List.of() : (List<Map<String, Object>>) body;
        } catch (Exception e) {
            log.warn("[thread-plan-items] list failed postId={}: {}", postId, e.getMessage());
            // Soft-fail on read so published comments still render if orchestrator is down.
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> patchPending(String postId, List<Map<String, Object>> body) {
        try {
            String url = UriComponentsBuilder
                    .fromUriString(orchestratorUrl + "/admin/thread-plan-items")
                    .queryParam("postId", postId)
                    .toUriString();
            List<?> resp = restClient.patch()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body == null ? List.of() : body)
                    .retrieve()
                    .body(List.class);
            return resp == null ? List.of() : (List<Map<String, Object>>) resp;
        } catch (RestClientResponseException e) {
            throw map(e);
        } catch (Exception e) {
            log.error("[thread-plan-items] patch failed postId={}: {}", postId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "orchestrator unavailable");
        }
    }

    private static ResponseStatusException map(RestClientResponseException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        if (status == null) status = HttpStatus.BAD_GATEWAY;
        if (status == HttpStatus.NOT_FOUND || status == HttpStatus.CONFLICT || status == HttpStatus.BAD_REQUEST) {
            return new ResponseStatusException(status, e.getResponseBodyAsString());
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getResponseBodyAsString());
    }
}
