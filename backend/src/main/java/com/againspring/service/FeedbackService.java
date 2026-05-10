package com.againspring.service;

import com.againspring.domain.Feedback;
import com.againspring.repository.FeedbackRepository;
import com.againspring.service.notify.CrisisFeedbackNotifier;
import com.againspring.service.notify.FeedbackEmailNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final Set<String> VALID_CATEGORIES =
            Set.of("ui_bug", "feature", "content", "crisis", "praise", "other");

    private final FeedbackRepository feedbackRepository;
    private final CrisisFeedbackNotifier crisisFeedbackNotifier;
    private final FeedbackEmailNotifier feedbackEmailNotifier;

    @Transactional
    public Feedback submit(Feedback feedback) {
        if (!VALID_CATEGORIES.contains(feedback.getCategory())) {
            throw new IllegalArgumentException("유효하지 않은 카테고리입니다: " + feedback.getCategory());
        }
        if (feedback.getContent() == null || feedback.getContent().trim().length() < 10) {
            throw new IllegalArgumentException("의견은 10자 이상이어야 합니다.");
        }

        Feedback saved = feedbackRepository.save(feedback);
        crisisFeedbackNotifier.notifyIfCrisis(saved);
        feedbackEmailNotifier.notifyNewFeedback(saved); // 운영자 메일 알림 (모든 카테고리)
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Feedback> listAll(Pageable pageable) {
        return feedbackRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Feedback> listByCategory(String category, Pageable pageable) {
        return feedbackRepository.findByCategoryOrderByCreatedAtDesc(category, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Feedback> listByStatus(String status, Pageable pageable) {
        return feedbackRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    @Transactional
    public Feedback updateStatus(Long id, String status, String adminNote) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("피드백을 찾을 수 없습니다: " + id));
        feedback.setStatus(status);
        if (adminNote != null) feedback.setAdminNote(adminNote);
        return feedbackRepository.save(feedback);
    }
}
