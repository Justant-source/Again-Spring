package com.againspring.api.admin.marketing;

import com.againspring.api.dto.response.ContentResponse;
import com.againspring.service.marketing.RepurposeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/marketing/repurpose")
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Marketing Repurpose", description = "Repurpose content to different platform")
@SecurityRequirement(name = "bearerAuth")
public class RepurposeController {

    private final RepurposeService repurposeService;

    @PostMapping("/{sourceId}")
    @Operation(summary = "Repurpose content to a different platform")
    public ResponseEntity<ContentResponse> repurpose(
            @PathVariable Long sourceId,
            @RequestParam String targetPlatform) {
        try {
            ContentResponse response = repurposeService.repurpose(sourceId, targetPlatform);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Repurpose failed: sourceId={}", sourceId, e);
            throw new RuntimeException("Repurpose failed: " + e.getMessage(), e);
        }
    }
}
