package com.againspring.api;

import com.againspring.api.dto.request.SubmitFeedbackRequest;
import com.againspring.domain.Feedback;
import com.againspring.service.FeedbackService;
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
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
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
