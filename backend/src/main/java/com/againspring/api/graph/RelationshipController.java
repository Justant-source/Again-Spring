package com.againspring.api.graph;

import com.againspring.api.dto.response.graph.PersonRelationshipSummary;
import com.againspring.api.dto.response.graph.SessionHistoryItem;
import com.againspring.service.graph.RelationshipGraphService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관계 그래프 API 컨트롤러
 * SQL 기반 관계 데이터 조회 (Neo4j 대체)
 */
@Slf4j
@RestController
@RequestMapping("/api/users/me/relationships")
@RequiredArgsConstructor
public class RelationshipController {

    private final RelationshipGraphService relationshipGraphService;

    /**
     * GET /api/users/me/relationships
     * 현재 사용자의 모든 관계 목록
     */
    @GetMapping
    public ResponseEntity<List<PersonRelationshipSummary>> listRelationships() {
        String userId = getCurrentUserId();
        List<PersonRelationshipSummary> relationships = relationshipGraphService.listRelationships(userId);
        return ResponseEntity.ok(relationships);
    }

    /**
     * GET /api/users/me/relationships/{counterpartUserId}/history
     * 특정 사용자와의 세션 이력 조회
     */
    @GetMapping("/{counterpartUserId}/history")
    public ResponseEntity<List<SessionHistoryItem>> getSessionHistory(
            @PathVariable String counterpartUserId) {

        String userId = getCurrentUserId();
        List<SessionHistoryItem> history = relationshipGraphService.historyWith(userId, counterpartUserId);
        return ResponseEntity.ok(history);
    }

    /**
     * 현재 인증된 사용자 ID 추출
     */
    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() != null) {
            Object principal = auth.getPrincipal();
            if (principal instanceof String) {
                return (String) principal;
            }
        }
        throw new IllegalStateException("User not authenticated");
    }

}
