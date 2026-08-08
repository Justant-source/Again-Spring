package com.againspring.api.admin;

import com.againspring.annotation.Auditable;
import com.againspring.api.admin.dto.MarketingPlatformResponse;
import com.againspring.api.admin.dto.UpdateMarketingPlatformAutoRequest;
import com.againspring.marketing.MarketingPlatformAutoService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin API for per-platform marketing auto-publish toggles (S5).
 * Separate from {@link AdminMarketingController} to avoid merge conflicts during redesign.
 */
@RestController
@RequestMapping("/api/admin/marketing/platforms")
@RequiredArgsConstructor
@Tag(name = "Admin — Marketing Platforms", description = "플랫폼별 자동 발행 on/off (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMarketingPlatformController {

    private final MarketingPlatformAutoService marketingPlatformAutoService;

    @GetMapping
    @Operation(summary = "List marketing platforms", description = "전체 플랫폼 + autoEnabled / runtimeSupported")
    @ApiResponse(responseCode = "200", description = "Platforms listed")
    public ResponseEntity<List<MarketingPlatformResponse>> listPlatforms() {
        List<MarketingPlatformResponse> body = marketingPlatformAutoService.listPlatforms().stream()
            .map(MarketingPlatformResponse::from)
            .collect(Collectors.toList());
        return ResponseEntity.ok(body);
    }

    @PutMapping("/{platform}/auto")
    @Operation(summary = "Set platform auto-publish", description = "미지원 플랫폼도 저장 가능; enabled=true면 warning 포함")
    @ApiResponse(responseCode = "200", description = "Updated")
    @ApiResponse(responseCode = "400", description = "Unknown platform or invalid body")
    @Auditable(action = "UPDATE_MARKETING_PLATFORM_AUTO", targetType = "MARKETING_PLATFORM")
    public ResponseEntity<MarketingPlatformResponse> setAuto(
            @PathVariable String platform,
            @Valid @RequestBody UpdateMarketingPlatformAutoRequest req,
            Authentication auth) {
        String updatedBy = auth != null ? auth.getName() : "admin";
        return ResponseEntity.ok(MarketingPlatformResponse.from(
            marketingPlatformAutoService.setAutoEnabled(platform, req.getEnabled(), updatedBy)));
    }
}
