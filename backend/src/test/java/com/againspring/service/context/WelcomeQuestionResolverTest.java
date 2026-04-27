package com.againspring.service.context;

import static org.junit.jupiter.api.Assertions.*;

import com.againspring.domain.Session;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WelcomeQuestionResolverTest {

    private WelcomeQuestionResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new WelcomeQuestionResolver();
    }

    private Session.PendingQuestion pendingQ(Session.Intent intent, double priority) {
        Session.PendingQuestion q = new Session.PendingQuestion();
        q.intent = intent;
        q.target = "USER_B";
        q.text = "힌트 텍스트";
        q.priority = priority;
        q.asked = false;
        return q;
    }

    @Test
    void emptyQueue_createsFallbackWelcomeQuestion() {
        Session session = new Session();

        Session.PendingQuestion result = resolver.resolveOrCreate(session);

        assertEquals(Session.Intent.WELCOME_PARTNER, result.intent);
        assertEquals(1.0, result.priority);
        assertEquals("USER_B", result.target);
        assertNotNull(result.id);
    }

    @Test
    void emptyQueue_withHeadline_setsHookFromIssue() {
        Session session = new Session();
        Session.IssueContext ctx = new Session.IssueContext();
        ctx.headline = "며칠간 이어진 긴장된 분위기";
        session.setIssueContext(ctx);

        Session.PendingQuestion result = resolver.resolveOrCreate(session);

        assertEquals("며칠간 이어진 긴장된 분위기", result.hookFromIssue);
    }

    @Test
    void queueWithUnasked_topIsPromotedToWelcomePartner() {
        Session session = new Session();
        Session.PendingQuestion q1 = pendingQ(Session.Intent.SEEK_FEELING, 0.5);
        Session.PendingQuestion q2 = pendingQ(Session.Intent.SEEK_NEED, 0.8); // 최상단
        session.setQuestionQueueB(new ArrayList<>(List.of(q1, q2)));

        Session.PendingQuestion result = resolver.resolveOrCreate(session);

        assertEquals(Session.Intent.WELCOME_PARTNER, result.intent, "최상단이 WELCOME_PARTNER로 격상되어야 함");
        assertEquals(1.0, result.priority);
    }

    @Test
    void queueAllAsked_createsFallback() {
        Session session = new Session();
        Session.PendingQuestion q = pendingQ(Session.Intent.SEEK_FEELING, 0.7);
        q.asked = true;
        session.setQuestionQueueB(new ArrayList<>(List.of(q)));

        Session.PendingQuestion result = resolver.resolveOrCreate(session);

        assertEquals(Session.Intent.WELCOME_PARTNER, result.intent, "모두 asked면 fallback이어야 함");
        assertNotNull(result.id);
    }

    @Test
    void fallbackAddsToQueueB() {
        Session session = new Session();

        resolver.resolveOrCreate(session);

        assertNotNull(session.getQuestionQueueB());
        assertFalse(session.getQuestionQueueB().isEmpty(), "fallback 생성 시 큐에 추가되어야 함");
    }
}
