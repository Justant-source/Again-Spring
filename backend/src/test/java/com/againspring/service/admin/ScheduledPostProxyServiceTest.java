package com.againspring.service.admin;

import com.againspring.api.admin.AdminScheduledContentController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScheduledPostProxyServiceTest {

    @Test
    void listMapsUnreachableOrchestratorToBadGateway() {
        ScheduledPostProxyService svc = new ScheduledPostProxyService("http://127.0.0.1:1");
        assertThatThrownBy(() -> svc.list(null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void controllerDelegatesToProxy() {
        ScheduledPostProxyService proxy = mock(ScheduledPostProxyService.class);
        when(proxy.list("SCHEDULED")).thenReturn(List.of(Map.of("id", "h1", "title", "t")));
        when(proxy.get("h1")).thenReturn(Map.of("id", "h1", "body", "b"));
        when(proxy.cancel("h1")).thenReturn(Map.of("id", "h1", "status", "CANCELLED"));

        AdminScheduledContentController controller = new AdminScheduledContentController(proxy);
        assertThat(controller.list("SCHEDULED").getBody()).hasSize(1);
        assertThat(controller.get("h1").getBody()).containsEntry("body", "b");
        assertThat(controller.cancel("h1").getBody()).containsEntry("status", "CANCELLED");
    }
}
