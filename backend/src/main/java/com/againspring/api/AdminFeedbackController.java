package com.againspring.api;

import com.againspring.api.dto.request.UpdateFeedbackStatusRequest;
import com.againspring.domain.Feedback;
import com.againspring.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/feedbacks")
@RequiredArgsConstructor
@Tag(name = "Admin — Feedbacks", description = "피드백 목록 조회·상태 관리 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    @GetMapping
    @Operation(summary = "피드백 목록 조회", description = "category 또는 status 필터로 페이지네이션 조회. 둘 다 없으면 전체 반환.")
    @ApiResponse(responseCode = "200", description = "피드백 페이지")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<Page<Feedback>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Feedback> result;
        if (category != null && !category.isBlank()) {
            result = feedbackService.listByCategory(category, pageable);
        } else if (status != null && !status.isBlank()) {
            result = feedbackService.listByStatus(status, pageable);
        } else {
            result = feedbackService.listAll(pageable);
        }
        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "피드백 상태 업데이트", description = "피드백 처리 상태와 관리자 메모를 업데이트한다.")
    @ApiResponse(responseCode = "200", description = "업데이트된 피드백 반환")
    @ApiResponse(responseCode = "400", description = "유효성 검사 실패")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @ApiResponse(responseCode = "404", description = "피드백 없음")
    public ResponseEntity<Feedback> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFeedbackStatusRequest req) {

        Feedback updated = feedbackService.updateStatus(id, req.getStatus(), req.getAdminNote());
        return ResponseEntity.ok(updated);
    }
}
