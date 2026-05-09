package com.againspring.api;

import com.againspring.common.exception.BusinessException;
import com.againspring.domain.Feedback;
import com.againspring.service.FeedbackService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedbackController Tests")
class FeedbackControllerTest {

    @Mock
    private FeedbackService feedbackService;

    @InjectMocks
    private FeedbackController feedbackController;

    @Test
    @DisplayName("정상 제출 → 201 + id 반환")
    void submit_valid_returns201WithId() {
        Feedback saved = Feedback.builder()
                .category("praise")
                .content("정말 좋았어요!")
                .contactConsent(false)
                .build();
        saved.setId(42L);
        when(feedbackService.submit(any(Feedback.class))).thenReturn(saved);

        var req = buildRequest("praise", "정말 좋았어요!", false, null);
        var response = feedbackController.submit(null, req);

        assertEquals(201, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(42L, response.getBody().get("id"));
    }

    @Test
    @DisplayName("FeedbackService 예외 → 컨트롤러 전파")
    void submit_serviceThrows_propagatesException() {
        when(feedbackService.submit(any(Feedback.class)))
                .thenThrow(new IllegalArgumentException("유효하지 않은 카테고리입니다."));

        var req = buildRequest("invalid", "내용 내용 내용 내용 내용", false, null);
        assertThrows(IllegalArgumentException.class,
                () -> feedbackController.submit(null, req));
    }

    @Test
    @DisplayName("contactConsent=false 시 contactEmail을 null로 설정")
    void submit_noContactConsent_emailSetToNull() {
        Feedback captured = Feedback.builder().build();
        Feedback saved = Feedback.builder()
                .category("other")
                .content("내용 내용 내용 내용 내용")
                .contactConsent(false)
                .build();
        saved.setId(1L);
        when(feedbackService.submit(any(Feedback.class))).thenAnswer(inv -> {
            Feedback fb = inv.getArgument(0);
            // contactEmail이 null이어야 함
            assertNull(fb.getContactEmail());
            return saved;
        });

        var req = buildRequest("other", "내용 내용 내용 내용 내용", false, "test@test.com");
        feedbackController.submit(null, req);
    }

    private com.againspring.api.dto.request.SubmitFeedbackRequest buildRequest(
            String category, String content, boolean contactConsent, String contactEmail) {
        try {
            var req = new com.againspring.api.dto.request.SubmitFeedbackRequest();
            var categoryField = req.getClass().getDeclaredField("category");
            categoryField.setAccessible(true);
            categoryField.set(req, category);

            var contentField = req.getClass().getDeclaredField("content");
            contentField.setAccessible(true);
            contentField.set(req, content);

            var consentField = req.getClass().getDeclaredField("contactConsent");
            consentField.setAccessible(true);
            consentField.set(req, contactConsent);

            var emailField = req.getClass().getDeclaredField("contactEmail");
            emailField.setAccessible(true);
            emailField.set(req, contactEmail);

            return req;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
