package com.againspring.service.context;

import com.againspring.domain.Session;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * Phase D PR-5 — B 진입 시 첫 질문 결정.
 * B 큐 미발화 최상단을 WELCOME_PARTNER로 격상하거나, 비어있으면 IssueContext 기반 fallback 생성.
 *
 * 권위본: shared/docs/policies/context-algorithm.md §6.3
 */
@Component
public class WelcomeQuestionResolver {

    public Session.PendingQuestion resolveOrCreate(Session session) {
        List<Session.PendingQuestion> queueB = session.getQuestionQueueB();
        if (queueB == null) queueB = new ArrayList<>();

        // 1. 미발화 최상단을 WELCOME_PARTNER로 격상
        Optional<Session.PendingQuestion> top = queueB.stream()
            .filter(q -> !Boolean.TRUE.equals(q.asked))
            .max(Comparator.comparingDouble(q -> q.priority == null ? 0.0 : q.priority));
        if (top.isPresent()) {
            Session.PendingQuestion q = top.get();
            q.intent = Session.Intent.WELCOME_PARTNER;
            q.priority = 1.0;
            return q;
        }

        // 2. 빈 큐 — IssueContext.headline 기반 fallback 생성
        Session.PendingQuestion q = new Session.PendingQuestion();
        q.id = UUID.randomUUID().toString();
        q.intent = Session.Intent.WELCOME_PARTNER;
        q.target = "USER_B";
        q.text = "최근 두 분 사이에 어떤 마음이 드셨는지";
        Session.IssueContext ctx = session.getIssueContext();
        q.hookFromIssue = (ctx != null && ctx.headline != null) ? ctx.headline : null;
        q.priority = 1.0;
        q.createdTurn = 0;
        q.ageInTurns = 0;
        q.asked = false;
        queueB.add(q);
        session.setQuestionQueueB(queueB);
        return q;
    }
}
