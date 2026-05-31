package com.againspring.service.context;

import com.againspring.domain.Session;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase D — LLM이 보낸 IssueContextDelta를 Session.issueContext에 병합.
 * dedup, FIFO drop, 카테고리 룰 검증, ratio 태깅, currentFocus 동기화 수행.
 *
 * 권위본: shared/docs/policies/context-algorithm.md §4.2, §5.1, §5.5
 */
@Component
@RequiredArgsConstructor
public class IssueContextMerger {

    private static final int MAX_FACTS = 12;
    private static final int MAX_NEEDS = 8;
    private static final int MAX_THREADS = 8;

    private final CategoryRuleEnforcer ruleEnforcer;
    private final RatioElementTagger ratioTagger;

    public void merge(Session session, IssueContextDelta delta, int currentTurn) {
        if (delta == null) return;

        Session.IssueContext ctx = session.getIssueContext();
        if (ctx == null) {
            ctx = new Session.IssueContext();
            ctx.facts = new ArrayList<>();
            ctx.namedNeeds = new ArrayList<>();
            ctx.threads = new ArrayList<>();
            ctx.revision = 0;
        }

        // V47~: koreanTag는 LLM 추론값 (session.koreanTag), categoryMinorId 대신 사용
        String categoryMinor = session.getKoreanTag();

        // headline — currentFocus와 동기화 (호환 레이어)
        if (delta.headline != null && !delta.headline.isBlank()) {
            ctx.headline = trimStr(delta.headline, 50);
            session.setCurrentFocus(ctx.headline);
        }

        // facts
        if (delta.factsAdded != null) {
            for (Session.IssueFact f : delta.factsAdded) {
                if (!ruleEnforcer.isFactAllowed(f, categoryMinor)) continue;
                if (containsFactText(ctx.facts, f.text)) continue;
                if (ctx.facts.size() >= MAX_FACTS) ctx.facts.remove(0);
                f.contributesTo = ratioTagger.tagFact(f);
                f.categoryRule = categoryMinor;
                ctx.facts.add(f);
            }
        }

        // facts_confirmed (Duo 모드에서 양쪽이 인정한 사실)
        if (delta.factsConfirmed != null) {
            for (String text : delta.factsConfirmed) {
                ctx.facts.stream()
                    .filter(f -> text.equals(f.text))
                    .forEach(f -> f.confirmedByOther = true);
            }
        }

        // needs
        if (delta.needsAdded != null) {
            for (Session.NeedSlot n : delta.needsAdded) {
                if (n.text == null || n.text.isBlank()) continue;
                if (containsNeed(ctx.namedNeeds, n.text, n.owner)) continue;
                if (ctx.namedNeeds.size() >= MAX_NEEDS) ctx.namedNeeds.remove(0);
                n.contributesTo = ratioTagger.tagNeed(n);
                n.firstMentionedTurn = currentTurn;
                ctx.namedNeeds.add(n);
            }
        }

        // threads
        if (delta.threadsAdded != null) {
            for (Session.UnresolvedThread t : delta.threadsAdded) {
                if (t.text == null || t.text.isBlank()) continue;
                if (containsThreadText(ctx.threads, t.text)) continue;
                if (ctx.threads.size() >= MAX_THREADS) ctx.threads.remove(0);
                t.mentionedTurn = currentTurn;
                t.addressedByQueue = false;
                t.ageInTurns = 0;
                ctx.threads.add(t);
            }
        }

        // threads_resolved
        if (delta.threadsResolved != null && !delta.threadsResolved.isEmpty()) {
            ctx.threads.removeIf(t -> delta.threadsResolved.contains(t.text));
        }

        // age existing threads +1
        for (Session.UnresolvedThread t : ctx.threads) {
            t.ageInTurns = (t.ageInTurns == null ? 0 : t.ageInTurns) + 1;
        }

        ctx.revision = (ctx.revision == null ? 0 : ctx.revision) + 1;
        ctx.lastUpdatedAt = Instant.now();
        session.setIssueContext(ctx);
    }

    private String trimStr(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }

    private boolean containsFactText(List<Session.IssueFact> list, String text) {
        return list.stream().anyMatch(f -> text.equals(f.text));
    }

    private boolean containsNeed(List<Session.NeedSlot> list, String text, String owner) {
        return list.stream().anyMatch(n -> text.equals(n.text) && owner != null && owner.equals(n.owner));
    }

    private boolean containsThreadText(List<Session.UnresolvedThread> list, String text) {
        return list.stream().anyMatch(t -> text.equals(t.text));
    }
}
