package com.againspring.aiuser.orchestrator.admin;

import com.againspring.aiuser.orchestrator.service.threadplan.AdminThreadPlanItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Internal admin API for pending COMMENT/REPLY items after a post is published.
 * Docker network only — backend ADMIN JWT gateway proxies public access.
 */
@RestController
@RequestMapping("/admin/thread-plan-items")
@RequiredArgsConstructor
public class AdminThreadPlanItemController {
    private final AdminThreadPlanItemService service;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listByPost(@RequestParam String postId) {
        return ResponseEntity.ok(service.listPendingContent(postId));
    }

    @PatchMapping
    public ResponseEntity<List<Map<String, Object>>> patchByPost(
            @RequestParam String postId,
            @RequestBody List<Map<String, Object>> body) {
        return ResponseEntity.ok(service.patchPendingContent(postId, body == null ? List.of() : body));
    }
}
