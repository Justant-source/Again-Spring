package com.againspring.service.context;

import com.againspring.domain.Session;
import com.againspring.domain.enums.MessageSender;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * Phase D PR-4 — 우선순위 산출식.
 * priority = base × stateMultiplier × categoryMultiplier
 * base = 0.5 × recency + 0.3 × urgency + 0.2 × coverageGap
 *
 * 권위본: shared/docs/policies/context-algorithm.md §5.3
 */
@Component
public class QuestionPrioritizer {

    public void rescore(List<Session.PendingQuestion> queue, Session session,
                        MessageSender target) {
        if (queue == null || queue.isEmpty()) return;

        Session.UserState currentState = currentStateFor(session, target);
        Set<String> unresolvedThreadTexts = collectUnresolvedThreadTexts(session);
        String categoryMinor = session.getCategory() != null ? session.getCategory().minorId : null;

        for (Session.PendingQuestion q : queue) {
            if (Boolean.TRUE.equals(q.asked)) {
                q.priority = 0.0;
                continue;
            }
            double recency = 1.0 / (1 + Math.max(0, q.ageInTurns == null ? 0 : q.ageInTurns));
            double urgency = urgencyOf(q.intent);
            double coverageGap = (q.hookFromIssue != null
                && unresolvedThreadTexts.contains(q.hookFromIssue)) ? 1.0 : 0.3;
            double base = 0.5 * recency + 0.3 * urgency + 0.2 * coverageGap;
            double stateMult = stateMultiplier(currentState, q.intent);
            double catMult = categoryMultiplier(q.intent, categoryMinor);
            q.priority = clamp01(base * stateMult * catMult);
        }
    }

    private double urgencyOf(Session.Intent i) {
        if (i == null) return 0.3;
        return switch (i) {
            case WELCOME_PARTNER -> 1.0;
            case SEEK_NEED -> 0.7;
            case SEEK_FEELING, BRIDGE_PERSPECTIVE -> 0.5;
            case SEEK_FACT -> 0.4;
            case INVITE_REPAIR, REFLECT_PATTERN -> 0.0;
        };
    }

    /** context-algorithm.md §5.3 state multiplier 매트릭스 (7×7). */
    private double stateMultiplier(Session.UserState state, Session.Intent intent) {
        if (state == null || intent == null) return 1.0;
        return switch (state) {
            case OPENING -> switch (intent) {
                case SEEK_FEELING, WELCOME_PARTNER, SEEK_FACT -> 1.0;
                case SEEK_NEED -> 0.8;
                case BRIDGE_PERSPECTIVE, REFLECT_PATTERN -> 0.5;
                case INVITE_REPAIR -> 0.3;
            };
            case VENTING -> switch (intent) {
                case SEEK_FEELING -> 1.3;
                case SEEK_NEED, WELCOME_PARTNER -> 1.0;
                case SEEK_FACT, BRIDGE_PERSPECTIVE -> 0.7;
                case REFLECT_PATTERN -> 0.5;
                case INVITE_REPAIR -> 0.3;
            };
            case DEFENSIVE -> switch (intent) {
                case REFLECT_PATTERN -> 1.2;
                case SEEK_FEELING, WELCOME_PARTNER -> 1.0;
                case SEEK_FACT, SEEK_NEED -> 0.7;
                case BRIDGE_PERSPECTIVE -> 0.5;
                case INVITE_REPAIR -> 0.3;
            };
            case BLAMING -> switch (intent) {
                case REFLECT_PATTERN -> 1.3;
                case SEEK_FEELING -> 1.2;
                case SEEK_NEED, WELCOME_PARTNER -> 1.0;
                case SEEK_FACT, BRIDGE_PERSPECTIVE -> 0.5;
                case INVITE_REPAIR -> 0.3;
            };
            case REFLECTING -> switch (intent) {
                case SEEK_NEED, BRIDGE_PERSPECTIVE -> 1.3;
                case WELCOME_PARTNER, SEEK_FACT, SEEK_FEELING, REFLECT_PATTERN -> 1.0;
                case INVITE_REPAIR -> 0.7;
            };
            case NEGOTIATING -> switch (intent) {
                case BRIDGE_PERSPECTIVE -> 1.2;
                case INVITE_REPAIR, SEEK_FACT, SEEK_NEED, WELCOME_PARTNER -> 1.0;
                case SEEK_FEELING, REFLECT_PATTERN -> 0.7;
            };
            case RESOLVING -> switch (intent) {
                case INVITE_REPAIR, WELCOME_PARTNER -> 1.0;
                default -> 0.5;
            };
        };
    }

    /** context-algorithm.md §5.3 categoryMultiplier 표 (한국 고유 4종). */
    private double categoryMultiplier(Session.Intent intent, String categoryMinor) {
        if (categoryMinor == null || intent == null) return 1.0;
        return switch (categoryMinor) {
            case "in_law" -> switch (intent) {
                case BRIDGE_PERSPECTIVE, SEEK_NEED -> 1.2;
                default -> 1.0;
            };
            case "face" -> intent == Session.Intent.SEEK_FEELING ? 1.3 : 1.0;
            case "lingered" -> switch (intent) {
                case SEEK_NEED, REFLECT_PATTERN -> 1.3;
                case SEEK_FACT -> 0.0; // 단일 사건 인터뷰 금지
                default -> 1.0;
            };
            case "generation" -> switch (intent) {
                case BRIDGE_PERSPECTIVE, SEEK_NEED -> 1.2;
                default -> 1.0;
            };
            default -> 1.0;
        };
    }

    private Session.UserState currentStateFor(Session session, MessageSender target) {
        List<Session.UserStateEntry> hist = session.getUserStateHistory();
        if (hist == null || hist.isEmpty()) return null;
        Session.UserState latest = null;
        for (Session.UserStateEntry e : hist) {
            if (target.name().equals(e.sender)) latest = e.state;
        }
        return latest;
    }

    private Set<String> collectUnresolvedThreadTexts(Session session) {
        Session.IssueContext ctx = session.getIssueContext();
        if (ctx == null || ctx.threads == null) return Collections.emptySet();
        Set<String> texts = new HashSet<>();
        for (Session.UnresolvedThread t : ctx.threads) {
            if (!Boolean.TRUE.equals(t.addressedByQueue)) texts.add(t.text);
        }
        return texts;
    }

    private double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
