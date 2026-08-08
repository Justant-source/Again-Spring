package com.againspring.api.admin;

import com.againspring.annotation.Auditable;
import com.againspring.api.admin.dto.MarketingHoldingBoardResponse;
import com.againspring.api.admin.dto.PinMarketingHoldingRequest;
import com.againspring.api.admin.dto.UpdateMarketingHoldingDraftRequest;
import com.againspring.marketing.holding.MarketingHoldingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin waiting-board API for marketing holdings (S2/S3).
 * Separate from {@link AdminMarketingController} to avoid parallel-edit conflicts.
 */
@RestController
@RequestMapping("/api/admin/marketing/holding")
@RequiredArgsConstructor
@Tag(name = "Admin — Marketing Holding", description = "마케팅 대기 보드 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMarketingHoldingController {

    private final MarketingHoldingService marketingHoldingService;

    @GetMapping
    @Operation(summary = "Marketing holding board",
        description = "최대 20행 대기 보드 + 메타(잔여 풀 N, 상한, 가중치). "
            + "컷라인 N = remainingPool - softReservedPool(핀). 진입 시 seed/상태 갱신.")
    @ApiResponse(responseCode = "200", description = "Board returned")
    public ResponseEntity<MarketingHoldingBoardResponse> getBoard() {
        return ResponseEntity.ok(
            MarketingHoldingBoardResponse.from(marketingHoldingService.getBoard()));
    }

    @PatchMapping("/{postId}/draft")
    @Operation(summary = "Update holding draft",
        description = "draft_json 교체. locked_at 이 있으면 400.")
    @ApiResponse(responseCode = "200", description = "Draft updated")
    @ApiResponse(responseCode = "400", description = "Draft locked or invalid")
    @ApiResponse(responseCode = "404", description = "Holding not found")
    @Auditable(action = "UPDATE_MARKETING_HOLDING_DRAFT")
    public ResponseEntity<MarketingHoldingBoardResponse.Item> updateDraft(
            @PathVariable String postId,
            @Valid @RequestBody UpdateMarketingHoldingDraftRequest req) {
        return ResponseEntity.ok(toItem(
            marketingHoldingService.updateDraft(postId, req.getDraft())));
    }

    @PostMapping("/{postId}/pin")
    @Operation(summary = "Pin holding",
        description = "핀 + soft reserve. Body format=VIDEO|TEXT. "
            + "잔여 풀/영상 슬롯 부족 시 최하위 자동 후보 OUT_OF_CUT(Q8); "
            + "전부 핀이 점유하면 400.")
    @ApiResponse(responseCode = "200", description = "Pinned")
    @ApiResponse(responseCode = "400", description = "Pool/video capacity exhausted or invalid status")
    @ApiResponse(responseCode = "404", description = "Holding not found")
    @Auditable(action = "PIN_MARKETING_HOLDING")
    public ResponseEntity<MarketingHoldingBoardResponse.Item> pin(
            @PathVariable String postId,
            @Valid @RequestBody PinMarketingHoldingRequest req) {
        return ResponseEntity.ok(toItem(
            marketingHoldingService.pin(postId, req.getFormat())));
    }

    @DeleteMapping("/{postId}/pin")
    @Operation(summary = "Unpin holding",
        description = "핀 해제·예약 반환. 새 컷라인 기준 IN_POOL 또는 OUT_OF_CUT.")
    @ApiResponse(responseCode = "200", description = "Unpinned")
    @ApiResponse(responseCode = "400", description = "Not pinned")
    @ApiResponse(responseCode = "404", description = "Holding not found")
    @Auditable(action = "UNPIN_MARKETING_HOLDING")
    public ResponseEntity<MarketingHoldingBoardResponse.Item> unpin(@PathVariable String postId) {
        return ResponseEntity.ok(toItem(marketingHoldingService.unpin(postId)));
    }

    private static MarketingHoldingBoardResponse.Item toItem(
            MarketingHoldingService.BoardItem item) {
        return MarketingHoldingBoardResponse.Item.builder()
            .postId(item.postId())
            .title(item.title())
            .status(item.status())
            .pinFormat(item.pinFormat())
            .scoreSnapshot(item.scoreSnapshot())
            .rankSnapshot(item.rankSnapshot())
            .projectedFormat(item.projectedFormat())
            .postCreatedAt(item.postCreatedAt())
            .lockedAt(item.lockedAt())
            .createdAt(item.createdAt())
            .updatedAt(item.updatedAt())
            .draft(item.draft())
            .build();
    }
}
