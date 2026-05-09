package com.againspring.service;

import com.againspring.domain.Feedback;
import com.againspring.repository.FeedbackRepository;
import com.againspring.service.notify.CrisisFeedbackNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FeedbackService Tests")
class FeedbackServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private CrisisFeedbackNotifier crisisFeedbackNotifier;

    private FeedbackService feedbackService;

    @BeforeEach
    void setUp() {
        feedbackService = new FeedbackService(feedbackRepository, crisisFeedbackNotifier);
    }

    @Test
    @DisplayName("유효한 피드백 제출 성공")
    void submit_validFeedback_succeeds() {
        Feedback input = buildFeedback("praise", "정말 좋았어요 감사해요");
        Feedback saved = buildFeedback("praise", "정말 좋았어요 감사해요");
        saved.setId(1L);
        when(feedbackRepository.save(any(Feedback.class))).thenReturn(saved);

        Feedback result = feedbackService.submit(input);

        assertNotNull(result.getId());
        verify(feedbackRepository).save(any(Feedback.class));
        verify(crisisFeedbackNotifier).notifyIfCrisis(saved);
    }

    @Test
    @DisplayName("카테고리 crisis 시 notifier 호출")
    void submit_crisisCategory_callsNotifier() {
        Feedback input = buildFeedback("crisis", "도움이 필요한 상황이에요 정말 힘들어요");
        Feedback saved = buildFeedback("crisis", "도움이 필요한 상황이에요 정말 힘들어요");
        saved.setId(2L);
        when(feedbackRepository.save(any(Feedback.class))).thenReturn(saved);

        feedbackService.submit(input);

        verify(crisisFeedbackNotifier).notifyIfCrisis(saved);
    }

    @Test
    @DisplayName("카테고리 praise 시 notifier는 위기 분기 미실행")
    void submit_praiseCategory_notifierCalledButNoCrisis() {
        Feedback input = buildFeedback("praise", "너무 좋은 서비스에요!");
        Feedback saved = buildFeedback("praise", "너무 좋은 서비스에요!");
        saved.setId(3L);
        when(feedbackRepository.save(any(Feedback.class))).thenReturn(saved);

        feedbackService.submit(input);

        // notifier는 항상 호출되지만 crisis 분기는 notifier 내부에서 처리
        verify(crisisFeedbackNotifier).notifyIfCrisis(saved);
    }

    @Test
    @DisplayName("유효하지 않은 카테고리 → IllegalArgumentException")
    void submit_invalidCategory_throwsException() {
        Feedback input = buildFeedback("invalid_cat", "내용 내용 내용 내용 내용");

        assertThrows(IllegalArgumentException.class, () -> feedbackService.submit(input));
        verifyNoInteractions(feedbackRepository);
    }

    @Test
    @DisplayName("10자 미만 내용 → IllegalArgumentException")
    void submit_contentTooShort_throwsException() {
        Feedback input = buildFeedback("other", "짧아요");

        assertThrows(IllegalArgumentException.class, () -> feedbackService.submit(input));
        verifyNoInteractions(feedbackRepository);
    }

    @Test
    @DisplayName("정확히 10자 내용 → 정상 저장")
    void submit_contentExactly10chars_succeeds() {
        String tenChars = "1234567890"; // 정확히 10자
        Feedback input = buildFeedback("other", tenChars);
        Feedback saved = buildFeedback("other", tenChars);
        saved.setId(4L);
        when(feedbackRepository.save(any(Feedback.class))).thenReturn(saved);

        assertDoesNotThrow(() -> feedbackService.submit(input));
    }

    private Feedback buildFeedback(String category, String content) {
        return Feedback.builder()
                .category(category)
                .content(content)
                .contactConsent(false)
                .build();
    }
}
