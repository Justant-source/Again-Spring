package com.againspring.api.admin;

import com.againspring.annotation.Auditable;
import com.againspring.api.admin.dto.ForceMarketingCompletedRequest;
import com.againspring.api.admin.dto.ForceMarketingCompletedResponse;
import com.againspring.api.admin.dto.MarketingCompletedListResponse;
import com.againspring.domain.marketing.MarketingHoldingStatus;
import com.againspring.marketing.holding.MarketingHoldingCommitService;
import com.againspring.marketing.holding.MarketingHoldingCommitService.ForceMode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin completed-tab API for marketing holdings (S4).
 * Separate from {@link AdminMarketingController} to avoid parallel-edit conflicts.
 */
@RestController
@RequestMapping("/api/admin/marketing/completed")
@RequiredArgsConstructor
@Tag(name = "Admin — Marketing Completed", description = "마케팅 완료·탈락 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMarketingCompletedController {

    private final MarketingHoldingCommitService holdingCommitService;

    @GetMapping
    @Operation(summary = "List completed / dropped holdings",
        description = "COMMITTED·DROPPED 홀딩 + 최근 잡 요약. status 쿼리로 필터 가능.")
    @ApiResponse(responseCode = "200", description = "List returned")
    public ResponseEntity<MarketingCompletedListResponse> list(
            @RequestParam(required = false) MarketingHoldingStatus status,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(MarketingCompletedListResponse.from(
            holdingCommitService.listCompleted(status, limit)));
    }

    @PostMapping("/{postId}/force")
    @Operation(summary = "Force-commit a completed-tab holding",
        description = "상한 무시 강제 발행. mode=VIDEO_AND_TEXT|TEXT_ONLY. "
            + "주로 DROPPED(탈락) 재진입. 초안 잠금 + COMMITTED.")
    @ApiResponse(responseCode = "200", description = "Forced commit")
    @ApiResponse(responseCode = "400", description = "Invalid mode/status or no platforms")
    @ApiResponse(responseCode = "404", description = "Holding/post not found")
    @Auditable(action = "FORCE_MARKETING_COMMIT")
    public ResponseEntity<ForceMarketingCompletedResponse> force(
            @PathVariable String postId,
            @Valid @RequestBody ForceMarketingCompletedRequest req,
            Authentication auth) {
        ForceMode mode = req.getMode();
        String by = auth != null ? auth.getName() : "admin";
        return ResponseEntity.ok(ForceMarketingCompletedResponse.from(
            holdingCommitService.forceCommit(postId, mode, by)));
    }
}
