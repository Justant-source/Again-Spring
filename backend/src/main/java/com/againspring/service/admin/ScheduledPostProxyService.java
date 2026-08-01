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

import java.util.List;
import java.util.Map;

/**
 * Proxies admin scheduled-post CRUD to the ai-user orchestrator (shared DB owner of
 * {@code ai_scheduled_posts}). Public ADMIN JWT stays on this backend; orchestrator remains
 * Docker-network-only.
 */
@Slf4j
@Service
public class ScheduledPostProxyService {

    private final String orchestratorUrl;
    private final RestClient restClient;

    @Autowired
    public ScheduledPostProxyService(
            @Value("${ai.user.orchestrator-url:http://againspring-ai-user-orchestrator:8096}") String orchestratorUrl) {
        this(orchestratorUrl, RestClient.create());
    }

    /** Test-friendly constructor. */
    ScheduledPostProxyService(String orchestratorUrl, RestClient restClient) {
        this.orchestratorUrl = orchestratorUrl;
        this.restClient = restClient;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> list(String status) {
        try {
            String url = orchestratorUrl + "/admin/scheduled-posts"
                    + (status != null && !status.isBlank() ? "?status=" + status : "");
            List<?> body = restClient.get().uri(url).retrieve().body(List.class);
            return body == null ? List.of() : (List<Map<String, Object>>) body;
        } catch (RestClientResponseException e) {
            throw map(e);
        } catch (Exception e) {
            log.error("[scheduled-posts] list failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "orchestrator unavailable");
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String id) {
        try {
            Map<?, ?> body = restClient.get()
                    .uri(orchestratorUrl + "/admin/scheduled-posts/" + id)
                    .retrieve()
                    .body(Map.class);
            if (body == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");
            return (Map<String, Object>) body;
        } catch (RestClientResponseException e) {
            throw map(e);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("[scheduled-posts] get failed id={}: {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "orchestrator unavailable");
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> patch(String id, Map<String, Object> body) {
        try {
            Map<?, ?> resp = restClient.patch()
                    .uri(orchestratorUrl + "/admin/scheduled-posts/" + id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body == null ? Map.of() : body)
                    .retrieve()
                    .body(Map.class);
            return resp == null ? Map.of() : (Map<String, Object>) resp;
        } catch (RestClientResponseException e) {
            throw map(e);
        } catch (Exception e) {
            log.error("[scheduled-posts] patch failed id={}: {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "orchestrator unavailable");
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> cancel(String id) {
        try {
            Map<?, ?> resp = restClient.delete()
                    .uri(orchestratorUrl + "/admin/scheduled-posts/" + id)
                    .retrieve()
                    .body(Map.class);
            return resp == null ? Map.of() : (Map<String, Object>) resp;
        } catch (RestClientResponseException e) {
            throw map(e);
        } catch (Exception e) {
            log.error("[scheduled-posts] cancel failed id={}: {}", id, e.getMessage());
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
