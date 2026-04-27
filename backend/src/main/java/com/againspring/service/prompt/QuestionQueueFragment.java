package com.againspring.service.prompt;

import com.againspring.domain.Session;
import com.againspring.domain.enums.MessageSender;
import org.springframework.stereotype.Component;
import java.util.Comparator;
import java.util.List;

/**
 * Phase D PR-4 — 현재 사용자의 PQ 상위 3개를 프롬프트에 주입.
 * LLM은 text를 그대로 인용하지 않고 흐름에 맞게 재구성해야 함.
 *
 * 권위본: shared/docs/policies/context-algorithm.md §4.4, §5.1(c)
 */
@Component
public class QuestionQueueFragment {

    private static final int TOP_K = 3;

    public String render(Session session, MessageSender currentUserSender) {
        if (session == null) return "";

        List<Session.PendingQuestion> queue = currentUserSender == MessageSender.USER_A
            ? session.getQuestionQueueA() : session.getQuestionQueueB();
        if (queue == null || queue.isEmpty()) return "";

        List<Session.PendingQuestion> top = queue.stream()
            .filter(q -> !Boolean.TRUE.equals(q.asked))
            .sorted(Comparator.comparingDouble((Session.PendingQuestion q) ->
                q.priority == null ? 0.0 : q.priority).reversed())
            .limit(TOP_K)
            .toList();
        if (top.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<pending_questions for=\"").append(currentUserSender.name()).append("\" ")
          .append("note=\"누적된 미발화 질문. 가장 priority 높은 것을 자연스럽게 한 번 다뤄주세요. ")
          .append("그대로 읽지 말고 사용자 발화 흐름에 맞게 재구성해 주세요. ")
          .append("발화 시 question_queue_delta.asked 에 ID를 반드시 적어주세요.\">\n");
        for (Session.PendingQuestion q : top) {
            sb.append("- id=").append(q.id)
              .append(" intent=").append(q.intent == null ? "?" : q.intent.name())
              .append(" priority=").append(String.format("%.2f", q.priority == null ? 0.0 : q.priority))
              .append("\n  hint: ").append(q.text).append("\n");
        }
        sb.append("</pending_questions>\n");
        return sb.toString();
    }
}
