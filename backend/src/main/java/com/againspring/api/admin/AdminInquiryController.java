package com.againspring.api.admin;

import com.againspring.annotation.Auditable;
import com.againspring.api.dto.request.InquiryReplyRequest;
import com.againspring.api.dto.response.InquiryDetailResponse;
import com.againspring.domain.inquiry.Inquiry;
import com.againspring.service.admin.AdminInquiryService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 문의 관리 API
 * /api/admin/inquiries에서 문의 목록 조회, 상세 조회, 답변, 종료 등 처리
 */
@RestController
@RequestMapping("/api/admin/inquiries")
@RequiredArgsConstructor
@Tag(name = "Admin — Inquiries", description = "1:1 문의 관리 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
public class AdminInquiryController {

    private final AdminInquiryService adminInquiryService;

    /**
     * GET /api/admin/inquiries?status=OPEN&page=0&size=20
     * 문의 목록 조회 (상태별, 페이지네이션)
     */
    @GetMapping
    @Operation(
            summary = "문의 목록 조회",
            description = "상태별(OPEN/ANSWERED/CLOSED)로 문의를 조회. status 파라미터 생략시 전체 조회."
    )
    @ApiResponse(responseCode = "200", description = "문의 페이지")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<Page<Inquiry>> listInquiries(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Inquiry> result = adminInquiryService.listInquiries(status, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/admin/inquiries/count?status=OPEN
     * 특정 상태의 문의 개수 조회 (배지 업데이트용)
     */
    @GetMapping("/count")
    @Operation(
            summary = "문의 개수 조회",
            description = "특정 상태의 문의 개수를 반환. 배지 폴링용."
    )
    @ApiResponse(responseCode = "200", description = "{\"count\": N}")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    public ResponseEntity<CountResponse> getInquiryCount(@RequestParam(required = false) String status) {
        long count = adminInquiryService.countByStatus(status);
        return ResponseEntity.ok(new CountResponse(count));
    }

    /**
     * GET /api/admin/inquiries/{id}
     * 문의 상세 조회 (모든 메시지 포함)
     */
    @GetMapping("/{id}")
    @Operation(
            summary = "문의 상세 조회",
            description = "문의ID로 상세 정보 및 모든 메시지 조회"
    )
    @ApiResponse(responseCode = "200", description = "문의 상세 정보 + 메시지 리스트")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @ApiResponse(responseCode = "404", description = "문의 없음")
    public ResponseEntity<InquiryDetailResponse> getInquiryDetail(@PathVariable String id) {
        InquiryDetailResponse result = adminInquiryService.getInquiryDetail(id);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/admin/inquiries/{id}/reply
     * 문의에 답변 추가
     */
    @PostMapping("/{id}/reply")
    @Operation(
            summary = "문의에 답변 추가",
            description = "관리자가 문의에 답변을 추가하면 상태가 ANSWERED로 변경됨"
    )
    @ApiResponse(responseCode = "200", description = "답변 추가됨 (상태: ANSWERED)")
    @ApiResponse(responseCode = "400", description = "유효성 검사 실패")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @ApiResponse(responseCode = "404", description = "문의 없음")
    @Auditable(action = "INQUIRY_REPLY", targetType = "INQUIRY", targetId = "#id")
    public ResponseEntity<InquiryDetailResponse> replyToInquiry(
            @PathVariable String id,
            @Valid @RequestBody InquiryReplyRequest req,
            Authentication authentication) {

        String adminUserId = authentication.getName();
        InquiryDetailResponse result = adminInquiryService.replyToInquiry(id, adminUserId, req);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/admin/inquiries/{id}/close
     * 문의 종료
     */
    @PostMapping("/{id}/close")
    @Operation(
            summary = "문의 종료",
            description = "문의 상태를 CLOSED로 변경"
    )
    @ApiResponse(responseCode = "200", description = "문의 종료됨")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @ApiResponse(responseCode = "404", description = "문의 없음")
    @Auditable(action = "INQUIRY_CLOSE", targetType = "INQUIRY", targetId = "#id")
    public ResponseEntity<Void> closeInquiry(@PathVariable String id) {
        adminInquiryService.closeInquiry(id);
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /api/admin/inquiries/{id}
     * 문의 삭제 (모든 메시지 포함)
     */
    @DeleteMapping("/{id}")
    @Operation(
            summary = "문의 삭제",
            description = "문의와 관련된 모든 메시지 삭제"
    )
    @ApiResponse(responseCode = "200", description = "문의 삭제됨")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @ApiResponse(responseCode = "404", description = "문의 없음")
    @Auditable(action = "INQUIRY_DELETE", targetType = "INQUIRY", targetId = "#id")
    public ResponseEntity<Void> deleteInquiry(@PathVariable String id) {
        adminInquiryService.deleteInquiry(id);
        return ResponseEntity.ok().build();
    }

    // ===== DTO =====

    public static class CountResponse {
        public long count;

        public CountResponse(long count) {
            this.count = count;
        }

        public long getCount() {
            return count;
        }
    }
}
