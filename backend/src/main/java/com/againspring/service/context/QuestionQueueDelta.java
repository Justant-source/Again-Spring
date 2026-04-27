package com.againspring.service.context;

import com.againspring.domain.Session;
import java.util.List;

/**
 * Phase D PR-4 — LLM turn_meta.question_queue_delta 파싱 결과 DTO.
 * 권위본: shared/docs/policies/context-algorithm.md §4.3
 */
public class QuestionQueueDelta {
    public List<String> asked;
    public List<Session.PendingQuestion> newQuestions; // "new"는 Java 예약어라 newQuestions 사용
}
