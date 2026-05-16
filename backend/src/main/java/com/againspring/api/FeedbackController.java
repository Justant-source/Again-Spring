package com.againspring.api;

import com.againspring.api.dto.request.SubmitFeedbackRequest;
import com.againspring.domain.Feedback;
import com.againspring.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
@Tag(name = "Feedback", description = "사용자 피드백 제출")
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    @Operation(summary = "피드백 제출", description = "인증 여부와 무관하게 제출 가능. contactConsent=true일 때만 contactEmail 저장.")
    @ApiResponse(responseCode = "201", description = "피드백 저장 완료 (id 반환)")
    @ApiResponse(responseCode = "400", description = "유효성 검사 실패")
    public ResponseEntity<Map<String, Long>> submit(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SubmitFeedbackRequest req) {

        Feedback feedback = Feedback.builder()
                .userId(userDetails != null ? userDetails.getUsername() : null)
                .sessionId(req.getSessionId())
                .category(req.getCategory())
                .content(req.getContent())
                .contactConsent(req.isContactConsent())
                .contactEmail(req.isContactConsent() ? req.getContactEmail() : null)
                .pageUrl(req.getPageUrl())
                .userAgent(req.getUserAgent())
                .build();

        Feedback saved = feedbackService.submit(feedback);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId()));
    }
}
