package com.againspring.service.context;

import com.againspring.domain.Session;
import com.againspring.domain.enums.MessageSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * Phase D PR-4 — question_queue_delta를 Session의 PQ에 반영.
 * WELCOME_PARTNER는 절대 evict하지 않음 (context-algorithm.md §4.5).
 *
 * 권위본: shared/docs/policies/context-algorithm.md §4.3~4.5
 */
@Component
@RequiredArgsConstructor
public class QuestionQueueUpdater {

    private static final int MAX_QUEUE_SIZE = 5;
    private static final int MAX_AGE_BEFORE_EVICT = 8;
    private static final double MIN_PRIORITY_KEEP = 0.2;

    private final QuestionPrioritizer prioritizer;
    private final CategoryRuleEnforcer ruleEnforcer;

    public void update(Session session, QuestionQueueDelta delta, int currentTurn) {
        if (session.getQuestionQueueA() == null) session.setQuestionQueueA(new ArrayList<>());
        if (session.getQuestionQueueB() == null) session.setQuestionQueueB(new ArrayList<>());

        // 1. asked 처리
        if (delta != null && delta.asked != null) {
            markAsked(session.getQuestionQueueA(), delta.asked, currentTurn);
            markAsked(session.getQuestionQueueB(), delta.asked, currentTurn);
            updateThreadAddressedFlag(session, delta.asked);
        }

        // 2. ageing — 모든 미발화 항목
        ageNonAsked(session.getQuestionQueueA());
        ageNonAsked(session.getQuestionQueueB());

        // 3. new 추가
        String categoryMinor = session.getCategory() != null
            ? session.getCategory().minorId : null;
        if (delta != null && delta.newQuestions != null) {
            for (Session.PendingQuestion nq : delta.newQuestions) {
                if (!ruleEnforcer.isIntentAllowed(nq.intent, categoryMinor)) continue;

                List<Session.PendingQuestion> queue = "USER_A".equals(nq.target)
                    ? session.getQuestionQueueA() : session.getQuestionQueueB();

                if (containsDuplicate(queue, nq)) continue;

                Session.PendingQuestion q = new Session.PendingQuestion();
                q.id = UUID.randomUUID().toString();
                q.intent = nq.intent;
                q.target = nq.target;
                q.text = trim(nq.text, 80);
                q.hookFromIssue = nq.hookFromIssue;
                q.antidoteFor = nq.antidoteFor;
                q.createdTurn = currentTurn;
                q.ageInTurns = 0;
                q.asked = false;
                q.categoryRuleApplied = categoryMinor;
                q.priority = 0.0; // rescore 전 초기값
                queue.add(q);
            }
        }

        // 4. priority 재계산
        prioritizer.rescore(session.getQuestionQueueA(), session, MessageSender.USER_A);
        prioritizer.rescore(session.getQuestionQueueB(), session, MessageSender.USER_B);

        // 5. evict
        evict(session.getQuestionQueueA());
        evict(session.getQuestionQueueB());
    }

    private void markAsked(List<Session.PendingQuestion> queue, List<String> ids, int turn) {
        for (Session.PendingQuestion q : queue) {
            if (ids.contains(q.id)) {
                q.asked = true;
                q.askedTurn = turn;
            }
        }
    }

    private void updateThreadAddressedFlag(Session session, List<String> askedIds) {
        Session.IssueContext ctx = session.getIssueContext();
        if (ctx == null || ctx.threads == null) return;
        Set<String> hooks = new HashSet<>();
        for (Session.PendingQuestion q : session.getQuestionQueueA()) {
            if (askedIds.contains(q.id) && q.hookFromIssue != null) hooks.add(q.hookFromIssue);
        }
        for (Session.PendingQuestion q : session.getQuestionQueueB()) {
            if (askedIds.contains(q.id) && q.hookFromIssue != null) hooks.add(q.hookFromIssue);
        }
        for (Session.UnresolvedThread t : ctx.threads) {
            if (hooks.contains(t.text)) t.addressedByQueue = true;
        }
    }

    private void ageNonAsked(List<Session.PendingQuestion> queue) {
        for (Session.PendingQuestion q : queue) {
            if (!Boolean.TRUE.equals(q.asked)) {
                q.ageInTurns = (q.ageInTurns == null ? 0 : q.ageInTurns) + 1;
            }
        }
    }

    private boolean containsDuplicate(List<Session.PendingQuestion> queue,
                                      Session.PendingQuestion nq) {
        return queue.stream().anyMatch(q ->
            q.intent == nq.intent
            && Objects.equals(q.target, nq.target)
            && Objects.equals(q.hookFromIssue, nq.hookFromIssue)
            && !Boolean.TRUE.equals(q.asked));
    }

    private void evict(List<Session.PendingQuestion> queue) {
        // 1단계: stale (오래되고 priority 낮은 것) 제거 — WELCOME_PARTNER 보호
        queue.removeIf(q ->
            !Boolean.TRUE.equals(q.asked)
            && q.intent != Session.Intent.WELCOME_PARTNER
            && q.ageInTurns != null && q.ageInTurns >= MAX_AGE_BEFORE_EVICT
            && q.priority < MIN_PRIORITY_KEEP);

        // 2단계: 큐 사이즈 초과 시 — asked 가장 오래된 것부터
        while (queue.size() > MAX_QUEUE_SIZE) {
            Optional<Session.PendingQuestion> toRemove = queue.stream()
                .filter(q -> Boolean.TRUE.equals(q.asked))
                .min(Comparator.comparingInt(q -> q.askedTurn == null ? 0 : q.askedTurn));
            if (toRemove.isPresent()) {
                queue.remove(toRemove.get());
                continue;
            }
            // asked가 없으면 priority 최저인 것 (WELCOME_PARTNER 제외)
            Optional<Session.PendingQuestion> lowest = queue.stream()
                .filter(q -> q.intent != Session.Intent.WELCOME_PARTNER)
                .min(Comparator.comparingDouble(q -> q.priority == null ? 0.0 : q.priority));
            if (lowest.isPresent()) {
                queue.remove(lowest.get());
            } else {
                break; // 모두 WELCOME_PARTNER면 더 이상 제거 불가
            }
        }
    }

    private String trim(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
