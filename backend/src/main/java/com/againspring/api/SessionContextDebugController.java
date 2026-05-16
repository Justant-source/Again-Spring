package com.againspring.api;

import com.againspring.domain.Session;
import com.againspring.repository.SessionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 */
@RestController
@RequestMapping("/api/admin/sessions")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
@Tag(name = "Admin — Debug", description = "세션 Phase D 컨텍스트 디버그 (app.admin.enabled=true + ADMIN 권한)")
public class SessionContextDebugController {

    private final SessionRepository sessionRepo;

    @GetMapping("/{id}/context")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "세션 컨텍스트 디버그", description = "Phase D 컨텍스트 전체(issueContext, userStateHistory, questionQueue A/B, horsemenHistory)를 반환한다.")
    @ApiResponse(responseCode = "200", description = "컨텍스트 맵 반환")
    @ApiResponse(responseCode = "400", description = "세션 없음")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
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
