package com.againspring.api.admin.marketing;

import com.againspring.api.dto.request.StoryRequest;
import com.againspring.api.dto.response.StoryResponse;
import com.againspring.api.dto.response.StorySummaryResponse;
import com.againspring.safety.KeywordGuard;
import com.againspring.safety.ScanResult;
import com.againspring.service.marketing.StoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Story admin controller.
 * V15.2: Marketing story CRUD operations (ADMIN only).
 */
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RestController
@RequestMapping("/api/admin/marketing/stories")
@RequiredArgsConstructor
@Tag(name = "Admin — Marketing Stories (V15.2)", description = "마케팅 소스 스토리 관리 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
public class StoryController {

    private final StoryService storyService;
    private final KeywordGuard keywordGuard;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "스토리 생성 및 익명화", description = "새 스토리를 제출하고 자동 익명화 및 재작성률 계산을 수행한다.")
    @ApiResponse(responseCode = "201", description = "스토리 생성 완료", content = @Content(schema = @Schema(implementation = StoryResponse.class)))
    @ApiResponse(responseCode = "400", description = "금지어 감지 또는 검증 실패")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<StoryResponse> createStory(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody StoryRequest request) {

        ScanResult scanResult = keywordGuard.scanUserInput(request.getRawText(), userDetails.getUsername());
        if (scanResult.isBlocked() || scanResult.isCrisis()) {
            return ResponseEntity.badRequest().build();
        }

        StoryResponse response = storyService.create(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "스토리 목록 조회", description = "상태별로 필터링된 스토리 목록을 반환한다.")
    @ApiResponse(responseCode = "200", description = "스토리 목록", content = @Content(schema = @Schema(implementation = StorySummaryResponse.class)))
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<List<StorySummaryResponse>> listStories(
            @RequestParam(required = false) String status) {
        List<StorySummaryResponse> stories = storyService.findAll(status);
        return ResponseEntity.ok(stories);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "스토리 상세 조회", description = "특정 스토리의 전체 세부사항을 반환한다.")
    @ApiResponse(responseCode = "200", description = "스토리 상세", content = @Content(schema = @Schema(implementation = StoryResponse.class)))
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @ApiResponse(responseCode = "404", description = "스토리 없음")
    public ResponseEntity<StoryResponse> getStory(@PathVariable Long id) {
        StoryResponse response = storyService.findById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "스토리 승인", description = "스토리 상태를 APPROVED로 변경한다.")
    @ApiResponse(responseCode = "200", description = "스토리 승인 완료", content = @Content(schema = @Schema(implementation = StoryResponse.class)))
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @ApiResponse(responseCode = "404", description = "스토리 없음")
    public ResponseEntity<StoryResponse> approveStory(@PathVariable Long id) {
        StoryResponse response = storyService.approve(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "스토리 거절", description = "스토리 상태를 REJECTED로 변경하고 거절 사유를 기록한다.")
    @ApiResponse(responseCode = "200", description = "스토리 거절 완료", content = @Content(schema = @Schema(implementation = StoryResponse.class)))
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @ApiResponse(responseCode = "404", description = "스토리 없음")
    public ResponseEntity<StoryResponse> rejectStory(
            @PathVariable Long id,
            @RequestParam String reason) {
        StoryResponse response = storyService.reject(id, reason);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "스토리 삭제", description = "스토리를 영구 삭제한다.")
    @ApiResponse(responseCode = "204", description = "스토리 삭제 완료")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @ApiResponse(responseCode = "404", description = "스토리 없음")
    public ResponseEntity<Void> deleteStory(@PathVariable Long id) {
        storyService.delete(id);
        return ResponseEntity.noContent().build();
    }

}
