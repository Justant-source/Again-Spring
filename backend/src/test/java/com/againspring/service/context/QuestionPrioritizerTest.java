package com.againspring.service.context;

import static org.junit.jupiter.api.Assertions.*;

import com.againspring.config.PhaseDProperties;
import com.againspring.domain.Session;
import com.againspring.domain.enums.MessageSender;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuestionPrioritizerTest {

    private QuestionPrioritizer prioritizer;

    @BeforeEach
    void setUp() {
        prioritizer = new QuestionPrioritizer(new PhaseDProperties());
    }

    private Session.PendingQuestion q(Session.Intent intent, int age) {
        Session.PendingQuestion q = new Session.PendingQuestion();
        q.intent = intent;
        q.target = "USER_A";
        q.ageInTurns = age;
        q.asked = false;
        q.priority = 0.0;
        return q;
    }

    private Session sessionWithState(Session.UserState state) {
        Session session = new Session();
        if (state != null) {
            Session.UserStateEntry e = new Session.UserStateEntry();
            e.sender = MessageSender.USER_A.name();
            e.state = state;
            session.setUserStateHistory(new ArrayList<>(List.of(e)));
        }
        return session;
    }

    // --- stateMultiplier 핵심 셀 검증 ---

    @Test
    void defensive_seekFact_multiplierIs0_7() {
        Session session = sessionWithState(Session.UserState.DEFENSIVE);
        Session.PendingQuestion seekFact = q(Session.Intent.SEEK_FACT, 0);
        Session.PendingQuestion seekFeeling = q(Session.Intent.SEEK_FEELING, 0);
        prioritizer.rescore(new ArrayList<>(List.of(seekFact, seekFeeling)), session, MessageSender.USER_A);

        // DEFENSIVE + SEEK_FACT = 0.7, SEEK_FEELING = 1.0 → SEEK_FACT < SEEK_FEELING
        assertTrue(seekFact.priority < seekFeeling.priority,
            "DEFENSIVE 상태에서 SEEK_FACT priority가 SEEK_FEELING보다 낮아야 함");
    }

    @Test
    void reflecting_seekNeed_multiplierIs1_3() {
        Session session = sessionWithState(Session.UserState.REFLECTING);
        Session.PendingQuestion seekNeed = q(Session.Intent.SEEK_NEED, 0);
        Session.PendingQuestion seekFact = q(Session.Intent.SEEK_FACT, 0);
        prioritizer.rescore(new ArrayList<>(List.of(seekNeed, seekFact)), session, MessageSender.USER_A);

        // REFLECTING + SEEK_NEED = 1.3, SEEK_FACT = 1.0 → SEEK_NEED > SEEK_FACT
        assertTrue(seekNeed.priority > seekFact.priority,
            "REFLECTING 상태에서 SEEK_NEED priority가 SEEK_FACT보다 높아야 함");
    }

    @Test
    void resolving_inviteRepair_priorityNotZero() {
        Session session = sessionWithState(Session.UserState.RESOLVING);
        Session.PendingQuestion inviteRepair = q(Session.Intent.INVITE_REPAIR, 0);
        prioritizer.rescore(new ArrayList<>(List.of(inviteRepair)), session, MessageSender.USER_A);

        assertTrue(inviteRepair.priority > 0.0, "RESOLVING + INVITE_REPAIR는 priority > 0이어야 함");
    }

    // --- categoryMultiplier 검증 ---

    @Test
    void lingered_seekFact_priorityIsZero() {
        Session session = new Session();
        // V47~: koreanTag 방식 (minorId/category 불필요)
        session.setKoreanTag("lingered");

        Session.PendingQuestion seekFact = q(Session.Intent.SEEK_FACT, 0);
        prioritizer.rescore(new ArrayList<>(List.of(seekFact)), session, MessageSender.USER_A);

        assertEquals(0.0, seekFact.priority, "lingered + SEEK_FACT는 priority=0이어야 함");
    }

    @Test
    void inLaw_bridgePerspective_higherThanDefault() {
        Session session = new Session();
        // V47~: koreanTag 방식 (minorId/category 불필요)
        session.setKoreanTag("in_law");

        Session.PendingQuestion bridge = q(Session.Intent.BRIDGE_PERSPECTIVE, 0);
        Session.PendingQuestion seekFact = q(Session.Intent.SEEK_FACT, 0);
        prioritizer.rescore(new ArrayList<>(List.of(bridge, seekFact)), session, MessageSender.USER_A);

        assertTrue(bridge.priority > seekFact.priority,
            "in_law에서 BRIDGE_PERSPECTIVE(×1.2)가 SEEK_FACT(×1.0)보다 높아야 함");
    }

    // --- recency 검증 ---

    @Test
    void recency_ageZero_higherThanAgeFour() {
        Session session = new Session();
        Session.PendingQuestion fresh = q(Session.Intent.SEEK_FEELING, 0);
        Session.PendingQuestion stale = q(Session.Intent.SEEK_FEELING, 4);
        prioritizer.rescore(new ArrayList<>(List.of(fresh, stale)), session, MessageSender.USER_A);

        assertTrue(fresh.priority > stale.priority, "ageInTurns=0이 ageInTurns=4보다 priority 높아야 함");
    }

    // --- coverageGap 검증 ---

    @Test
    void coverageGap_hookMatchesUnresolvedThread_boostedPriority() {
        Session session = new Session();
        Session.IssueContext ctx = new Session.IssueContext();
        Session.UnresolvedThread thread = new Session.UnresolvedThread();
        thread.text = "며칠 전 분위기가 무거웠던 이유";
        thread.addressedByQueue = false;
        ctx.threads = new ArrayList<>(List.of(thread));
        session.setIssueContext(ctx);

        Session.PendingQuestion withHook = q(Session.Intent.SEEK_FEELING, 0);
        withHook.hookFromIssue = "며칠 전 분위기가 무거웠던 이유"; // 일치
        Session.PendingQuestion withoutHook = q(Session.Intent.SEEK_FEELING, 0);
        withoutHook.hookFromIssue = null;

        prioritizer.rescore(new ArrayList<>(List.of(withHook, withoutHook)), session, MessageSender.USER_A);

        assertTrue(withHook.priority > withoutHook.priority,
            "hookFromIssue가 미해결 thread와 일치하면 priority 더 높아야 함");
    }

    // --- asked 항목은 priority=0 ---

    @Test
    void asked_priorityBecomesZero() {
        Session session = new Session();
        Session.PendingQuestion asked = q(Session.Intent.SEEK_FEELING, 0);
        asked.asked = true;
        prioritizer.rescore(new ArrayList<>(List.of(asked)), session, MessageSender.USER_A);

        assertEquals(0.0, asked.priority, "asked=true 항목은 priority=0이어야 함");
    }
}
