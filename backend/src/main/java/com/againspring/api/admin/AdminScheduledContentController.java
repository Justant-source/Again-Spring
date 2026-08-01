package com.againspring.api.admin;

import com.againspring.service.admin.ScheduledPostProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ADMIN gateway for AI-user held posts ({@code ai_scheduled_posts}).
 * Proxies to orchestrator {@code /admin/scheduled-posts}.
 */
@RestController
@RequestMapping("/api/admin/content/scheduled-posts")
@RequiredArgsConstructor
@Tag(name = "Admin — Scheduled Content", description = "예약 홀딩 글 조회·수정·취소 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
public class AdminScheduledContentController {

    private final ScheduledPostProxyService proxy;

    @GetMapping
    @Operation(summary = "예약 홀딩 목록", description = "status 기본 SCHEDULED. 쉼표 구분 또는 ALL_PENDING 가능.")
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(proxy.list(status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "예약 홀딩 상세", description = "글·댓글/대댓글 후보와 각 scheduledAt 포함.")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        return ResponseEntity.ok(proxy.get(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "예약 홀딩 수정", description = "SCHEDULED만. title/body/category/scheduledPublishAt/items.")
    public ResponseEntity<Map<String, Object>> patch(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(proxy.patch(id, body));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "예약 홀딩 취소", description = "SCHEDULED → CANCELLED.")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable String id) {
        return ResponseEntity.ok(proxy.cancel(id));
    }
}
