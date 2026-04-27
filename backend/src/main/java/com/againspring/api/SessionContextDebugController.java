package com.againspring.api;

import com.againspring.domain.Session;
import com.againspring.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phase D PR-6 — Phase D 컨텍스트 디버그 엔드포인트.
 * app.admin.enabled=true 환경(dev)에서만 활성.
 *
 * 권위본: backend/docs/implementation/phase-d-implementation-instructions.md §6.1
 */
@RestController
@RequestMapping("/api/admin/sessions")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
public class SessionContextDebugController {

    private final SessionRepository sessionRepo;

    @GetMapping("/{id}/context")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> debug(@PathVariable String id) {
        Session s = sessionRepo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Session not found: " + id));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessionId", id);
        body.put("status", s.getStatus());
        body.put("issueContext", s.getIssueContext());
        body.put("userStateHistory", s.getUserStateHistory());
        body.put("questionQueueA", s.getQuestionQueueA());
        body.put("questionQueueB", s.getQuestionQueueB());
        body.put("horsemenHistory", s.getHorsemenHistory());
        body.put("currentFocus", s.getCurrentFocus());

        return ResponseEntity.ok(body);
    }
}
