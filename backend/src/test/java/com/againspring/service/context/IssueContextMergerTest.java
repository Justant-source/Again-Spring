package com.againspring.service.context;

import static org.junit.jupiter.api.Assertions.*;

import com.againspring.domain.Session;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IssueContextMergerTest {

    private IssueContextMerger merger;

    @BeforeEach
    void setUp() {
        merger = new IssueContextMerger(new CategoryRuleEnforcer(), new RatioElementTagger());
    }

    private Session.IssueFact fact(String text) {
        Session.IssueFact f = new Session.IssueFact();
        f.text = text;
        f.source = "USER_A_T1";
        return f;
    }

    private IssueContextDelta deltaWithFact(String factText) {
        IssueContextDelta d = new IssueContextDelta();
        d.factsAdded = List.of(fact(factText));
        return d;
    }

    @Test
    void merge_addsFactToContext() {
        Session session = new Session();
        merger.merge(session, deltaWithFact("인사 없이 지나침"), 1);

        assertNotNull(session.getIssueContext());
        assertEquals(1, session.getIssueContext().facts.size());
        assertEquals("인사 없이 지나침", session.getIssueContext().facts.get(0).text);
    }

    @Test
    void merge_deduplicatesFacts() {
        Session session = new Session();
        merger.merge(session, deltaWithFact("인사 없이 지나침"), 1);
        merger.merge(session, deltaWithFact("인사 없이 지나침"), 2);

        assertEquals(1, session.getIssueContext().facts.size());
    }

    @Test
    void merge_droposOldestFact_whenExceedsMaxFacts() {
        Session session = new Session();
        for (int i = 0; i < 12; i++) {
            merger.merge(session, deltaWithFact("사실 " + i), i);
        }
        assertEquals(12, session.getIssueContext().facts.size());

        // 13번째 추가 시 가장 오래된 것 제거
        merger.merge(session, deltaWithFact("사실 12"), 12);
        assertEquals(12, session.getIssueContext().facts.size());
        assertEquals("사실 1", session.getIssueContext().facts.get(0).text); // "사실 0"이 제거됨
    }

    @Test
    void merge_rejectsFact_whenCategoryRuleViolated() {
        Session session = new Session();
        Session.Category cat = new Session.Category();
        cat.minorId = "in_law";
        session.setCategory(cat);

        merger.merge(session, deltaWithFact("시어머니가 차별했다"), 1);

        // fact는 거부됐으므로 facts 리스트가 비어있어야 함
        assertTrue(session.getIssueContext() == null
            || session.getIssueContext().facts.isEmpty(),
            "Rejected fact should not appear in context");
    }

    @Test
    void merge_allowsFact_forNeutralInLawText() {
        Session session = new Session();
        Session.Category cat = new Session.Category();
        cat.minorId = "in_law";
        session.setCategory(cat);

        merger.merge(session, deltaWithFact("시어머니 댁에 방문했다"), 1);

        assertNotNull(session.getIssueContext());
        assertEquals(1, session.getIssueContext().facts.size());
    }

    @Test
    void merge_updatesHeadlineAndCurrentFocus() {
        Session session = new Session();
        IssueContextDelta d = new IssueContextDelta();
        d.headline = "최근 며칠간 이어진 긴장된 분위기";

        merger.merge(session, d, 1);

        assertEquals("최근 며칠간 이어진 긴장된 분위기", session.getIssueContext().headline);
        assertEquals("최근 며칠간 이어진 긴장된 분위기", session.getCurrentFocus());
    }

    @Test
    void merge_trimHeadlineTo50Chars() {
        Session session = new Session();
        IssueContextDelta d = new IssueContextDelta();
        d.headline = "a".repeat(60);

        merger.merge(session, d, 1);

        assertEquals(50, session.getIssueContext().headline.length());
    }

    @Test
    void merge_addsThread() {
        Session session = new Session();
        IssueContextDelta d = new IssueContextDelta();
        Session.UnresolvedThread t = new Session.UnresolvedThread();
        t.text = "며칠 전 분위기가 무거웠던 이유";
        t.origin = "USER_A_T2";
        d.threadsAdded = List.of(t);

        merger.merge(session, d, 2);

        assertEquals(1, session.getIssueContext().threads.size());
        assertEquals(2, session.getIssueContext().threads.get(0).mentionedTurn);
    }

    @Test
    void merge_removesResolvedThread() {
        Session session = new Session();
        // 먼저 thread 추가
        IssueContextDelta d1 = new IssueContextDelta();
        Session.UnresolvedThread t = new Session.UnresolvedThread();
        t.text = "해결될 갈래";
        d1.threadsAdded = List.of(t);
        merger.merge(session, d1, 1);
        assertEquals(1, session.getIssueContext().threads.size());

        // 그 다음 턴에 해결 처리
        IssueContextDelta d2 = new IssueContextDelta();
        d2.threadsResolved = List.of("해결될 갈래");
        merger.merge(session, d2, 2);

        assertEquals(0, session.getIssueContext().threads.size());
    }

    @Test
    void merge_doesNothing_whenDeltaNull() {
        Session session = new Session();
        merger.merge(session, null, 1);
        assertNull(session.getIssueContext());
    }

    @Test
    void merge_incrementsRevision() {
        Session session = new Session();
        merger.merge(session, deltaWithFact("사실1"), 1);
        merger.merge(session, deltaWithFact("사실2"), 2);

        assertEquals(2, session.getIssueContext().revision);
    }
}
