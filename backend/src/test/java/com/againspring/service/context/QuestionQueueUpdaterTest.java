package com.againspring.service.context;

import static org.junit.jupiter.api.Assertions.*;

import com.againspring.config.PhaseDProperties;
import com.againspring.domain.Session;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuestionQueueUpdaterTest {

    private QuestionQueueUpdater updater;

    @BeforeEach
    void setUp() {
        updater = new QuestionQueueUpdater(new QuestionPrioritizer(new PhaseDProperties()), new CategoryRuleEnforcer());
    }

    private QuestionQueueDelta deltaWithNew(Session.Intent intent, String target, String hook) {
        QuestionQueueDelta d = new QuestionQueueDelta();
        Session.PendingQuestion q = new Session.PendingQuestion();
        q.intent = intent;
        q.target = target;
        q.text = "힌트 텍스트";
        q.hookFromIssue = hook;
        d.newQuestions = new ArrayList<>(List.of(q));
        return d;
    }

    @Test
    void push_addsNewQuestion() {
        Session session = new Session();
        updater.update(session, deltaWithNew(Session.Intent.SEEK_FEELING, "USER_A", null), 1);

        assertEquals(1, session.getQuestionQueueA().size());
        assertEquals(Session.Intent.SEEK_FEELING, session.getQuestionQueueA().get(0).intent);
    }

    @Test
    void push_setsUuidAndFields() {
        Session session = new Session();
        updater.update(session, deltaWithNew(Session.Intent.SEEK_NEED, "USER_A", null), 1);

        Session.PendingQuestion q = session.getQuestionQueueA().get(0);
        assertNotNull(q.id, "UUID가 설정되어야 함");
        assertEquals(1, q.createdTurn);
        assertFalse(Boolean.TRUE.equals(q.asked));
    }

    @Test
    void push_toDifferentTarget_goesToQueueB() {
        Session session = new Session();
        updater.update(session, deltaWithNew(Session.Intent.SEEK_FEELING, "USER_B", null), 1);

        assertTrue(session.getQuestionQueueA() == null || session.getQuestionQueueA().isEmpty());
        assertEquals(1, session.getQuestionQueueB().size());
    }

    @Test
    void dedup_sameIntentTargetHook_rejected() {
        Session session = new Session();
        QuestionQueueDelta delta = deltaWithNew(Session.Intent.SEEK_FEELING, "USER_A", "hook1");
        updater.update(session, delta, 1);
        updater.update(session, delta, 2); // 동일한 delta 재push

        assertEquals(1, session.getQuestionQueueA().size(), "중복은 추가되지 않아야 함");
    }

    @Test
    void ageing_nonAskedQuestionsIncrementAge() {
        Session session = new Session();
        updater.update(session, deltaWithNew(Session.Intent.SEEK_FEELING, "USER_A", null), 1);

        int ageBefore = session.getQuestionQueueA().get(0).ageInTurns;
        updater.update(session, null, 2); // delta null → 새 질문 없음, aging만

        int ageAfter = session.getQuestionQueueA().get(0).ageInTurns;
        assertEquals(ageBefore + 1, ageAfter, "미발화 항목의 ageInTurns가 +1 되어야 함");
    }

    @Test
    void markAsked_setsAskedTrue() {
        Session session = new Session();
        updater.update(session, deltaWithNew(Session.Intent.SEEK_FEELING, "USER_A", null), 1);
        String id = session.getQuestionQueueA().get(0).id;

        QuestionQueueDelta askedDelta = new QuestionQueueDelta();
        askedDelta.asked = new ArrayList<>(List.of(id));
        updater.update(session, askedDelta, 2);

        assertTrue(session.getQuestionQueueA().get(0).asked);
        assertEquals(2, session.getQuestionQueueA().get(0).askedTurn);
    }

    @Test
    void evict_exceedMaxSize_removesOldestAsked() {
        Session session = new Session();
        // 5개 추가 후 asked=true로 만들어 두고, 6번째 추가
        for (int i = 0; i < 5; i++) {
            updater.update(session, deltaWithNew(Session.Intent.SEEK_FEELING, "USER_A", "hook" + i), i + 1);
        }
        // 3개를 asked=true
        for (int i = 0; i < 3; i++) {
            String id = session.getQuestionQueueA().get(i).id;
            QuestionQueueDelta askedDelta = new QuestionQueueDelta();
            askedDelta.asked = new ArrayList<>(List.of(id));
            updater.update(session, askedDelta, i + 2);
        }
        int sizeBefore = session.getQuestionQueueA().size();

        // 6번째 — 새로운 hook으로 중복 회피
        updater.update(session, deltaWithNew(Session.Intent.SEEK_NEED, "USER_A", "hookNew"), 10);

        // 큐 크기가 MAX_QUEUE_SIZE(5) 이내여야 함
        assertTrue(session.getQuestionQueueA().size() <= 5,
            "큐 크기가 MAX_QUEUE_SIZE를 초과하지 않아야 함");
    }

    @Test
    void welcomePartner_neverEvicted() {
        Session session = new Session();
        // WELCOME_PARTNER 1개 + 다른 질문 4개 → 총 5개 (MAX)
        updater.update(session, deltaWithNew(Session.Intent.WELCOME_PARTNER, "USER_B", null), 1);
        for (int i = 0; i < 4; i++) {
            updater.update(session, deltaWithNew(Session.Intent.SEEK_FEELING, "USER_B", "h" + i), i + 2);
        }
        // 6번째 추가 → evict 발동
        updater.update(session, deltaWithNew(Session.Intent.SEEK_NEED, "USER_B", "hNew"), 10);

        boolean welcomePresent = session.getQuestionQueueB().stream()
            .anyMatch(q -> q.intent == Session.Intent.WELCOME_PARTNER);
        assertTrue(welcomePresent, "WELCOME_PARTNER는 절대 evict되지 않아야 함");
    }

    @Test
    void lingered_seekFact_rejectedByRuleEnforcer() {
        Session session = new Session();
        // V47~: koreanTag 방식 (minorId/category 불필요)
        session.setKoreanTag("lingered");

        updater.update(session, deltaWithNew(Session.Intent.SEEK_FACT, "USER_A", null), 1);

        assertTrue(session.getQuestionQueueA() == null || session.getQuestionQueueA().isEmpty(),
            "lingered 카테고리에서 SEEK_FACT는 거부되어야 함");
    }
}
