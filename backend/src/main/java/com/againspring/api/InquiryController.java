package com.againspring.api;

import com.againspring.domain.inquiry.Inquiry;
import com.againspring.domain.inquiry.InquiryMessage;
import com.againspring.repository.inquiry.InquiryMessageRepository;
import com.againspring.repository.inquiry.InquiryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 사용자 문의 제출 API
 * 인증된 사용자만 문의를 제출할 수 있음
 * TODO: Add /api/inquiries to SecurityConfig.java authenticated() matcher if needed
 * (현재 SecurityConfig의 .anyRequest().authenticated()로 이미 보호됨)
 */
@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
@Tag(name = "Inquiries", description = "사용자 1:1 문의 제출")
@SecurityRequirement(name = "bearer-jwt")
public class InquiryController {

    private final InquiryRepository inquiryRepository;
    private final InquiryMessageRepository inquiryMessageRepository;

    /**
     * POST /api/inquiries
     * 사용자가 1:1 문의 제출
     *
     * Body: {
     *   "subject": "제목",
     *   "category": "기술지원|결제|계정|기타",
     *   "message": "문의 내용"
     * }
     *
     * Response: {
     *   "id": "inquiry_uuid",
     *   "userId": "user_id",
     *   "subject": "...",
     *   "category": "...",
     *   "status": "OPEN",
     *   "createdAt": "2026-06-05T..."
     * }
     */
    @PostMapping
    @Operation(
            summary = "문의 제출",
            description = "사용자가 1:1 문의를 제출. 인증 필요."
    )
    @ApiResponse(responseCode = "201", description = "문의 제출됨")
    @ApiResponse(responseCode = "400", description = "유효성 검사 실패")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    public ResponseEntity<SubmitInquiryResponse> submitInquiry(
            @Valid @RequestBody SubmitInquiryRequest req,
            Authentication authentication) {

        String userId = authentication.getName();
        String inquiryId = "inq_" + UUID.randomUUID().toString().replace("-", "").substring(0, 28);

        // 문의 생성
        Inquiry inquiry = Inquiry.builder()
                .id(inquiryId)
                .userId(userId)
                .subject(req.getSubject())
                .category(req.getCategory())
                .status("OPEN")
                .build();
        inquiryRepository.save(inquiry);

        // 첫 번째 메시지 (사용자 메시지)
        InquiryMessage message = InquiryMessage.builder()
                .inquiryId(inquiryId)
                .senderRole("USER")
                .senderUserId(userId)
                .body(req.getMessage())
                .build();
        inquiryMessageRepository.save(message);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                SubmitInquiryResponse.from(inquiry)
        );
    }

    // ===== DTO =====

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmitInquiryRequest {
        @NotBlank(message = "제목은 필수입니다")
        private String subject;

        @NotBlank(message = "카테고리는 필수입니다")
        private String category;

        @NotBlank(message = "문의 내용은 필수입니다")
        private String message;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubmitInquiryResponse {
        private String id;
        private String userId;
        private String subject;
        private String category;
        private String status;
        private String createdAt;

        public static SubmitInquiryResponse from(Inquiry inquiry) {
            SubmitInquiryResponse resp = new SubmitInquiryResponse();
            resp.setId(inquiry.getId());
            resp.setUserId(inquiry.getUserId());
            resp.setSubject(inquiry.getSubject());
            resp.setCategory(inquiry.getCategory());
            resp.setStatus(inquiry.getStatus());
            resp.setCreatedAt(inquiry.getCreatedAt() != null ? inquiry.getCreatedAt().toString() : null);
            return resp;
        }
    }
}
