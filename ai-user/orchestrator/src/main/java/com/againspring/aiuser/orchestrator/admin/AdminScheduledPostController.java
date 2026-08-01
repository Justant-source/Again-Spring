package com.againspring.aiuser.orchestrator.admin;

import com.againspring.aiuser.orchestrator.domain.enums.ScheduledPostStatus;
import com.againspring.aiuser.orchestrator.service.threadplan.AdminScheduledPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Internal admin API for held posts in {@code ai_scheduled_posts}.
 * Docker network only — backend ADMIN JWT gateway proxies public access.
 */
@RestController
@RequestMapping("/admin/scheduled-posts")
@RequiredArgsConstructor
public class AdminScheduledPostController {
    private final AdminScheduledPostService service;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(required = false) String status) {
        List<ScheduledPostStatus> statuses = parseStatuses(status);
        return ResponseEntity.ok(service.list(statuses));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Map<String, Object>> patch(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(service.patch(id, body == null ? Map.of() : body));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable String id) {
        return ResponseEntity.ok(service.cancel(id));
    }

    private static List<ScheduledPostStatus> parseStatuses(String status) {
        if (status == null || status.isBlank() || "SCHEDULED".equalsIgnoreCase(status)) {
            return List.of(ScheduledPostStatus.SCHEDULED);
        }
        if ("ALL_PENDING".equalsIgnoreCase(status)) {
            return List.of(ScheduledPostStatus.SCHEDULED, ScheduledPostStatus.FAILED, ScheduledPostStatus.PUBLISHING);
        }
        return Arrays.stream(status.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> ScheduledPostStatus.valueOf(s.toUpperCase()))
                .toList();
    }
}
