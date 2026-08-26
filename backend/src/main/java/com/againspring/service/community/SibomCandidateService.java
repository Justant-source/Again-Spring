package com.againspring.service.community;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Keyword shortlist for Sibom characters at story create/update — no LLM.
 * Spec: {@code docs/shared/marketing/sibom-video-insertion.md} §5.
 */
@Service
public class SibomCandidateService {

    public static final int MAX_CANDIDATES = 12;

    /** Primary weight: catalog {@code keywords} phrase hit. */
    static final int KEYWORD_WEIGHT = 10;
    /** Soft weight: cleaned {@code trigger} token hit. */
    static final int TRIGGER_WEIGHT = 1;

    /**
     * Scored candidate: id, score, matched keywords and triggers.
     */
    public record ScoredCandidate(String id, int score, List<String> matchedKeywords, List<String> matchedTriggers) {}

    /**
     * Score detail: score with matched keywords and triggers.
     */
    record ScoreDetail(int score, List<String> matchedKeywords, List<String> matchedTriggers) {}

    /**
     * Score body (required) and optional title against each image; return top ≤12 ids.
     * Blank/null body → empty list. Zero-score images are omitted (no soft-fill padding).
     */
    public List<String> shortlist(String body, String title) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        List<SibomCatalog.Entry> images = SibomCatalog.entries();
        if (images.isEmpty()) {
            return List.of();
        }

        StringBuilder hay = new StringBuilder(body.length() + 64);
        hay.append(body);
        if (title != null && !title.isBlank()) {
            hay.append('\n').append(title);
        }
        String text = hay.toString();

        List<Scored> scored = new ArrayList<>(images.size());
        for (int i = 0; i < images.size(); i++) {
            SibomCatalog.Entry img = images.get(i);
            int score = score(img, text);
            if (score > 0) {
                scored.add(new Scored(img.id(), score, i));
            }
        }
        scored.sort(Comparator
                .comparingInt(Scored::score).reversed()
                .thenComparingInt(Scored::catalogIndex));

        int n = Math.min(MAX_CANDIDATES, scored.size());
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(scored.get(i).id());
        }
        return List.copyOf(out);
    }

    /** Convenience: body only. */
    public List<String> shortlist(String body) {
        return shortlist(body, null);
    }

    /**
     * Score detail: compute score and track matched keywords/triggers.
     */
    static ScoreDetail scoreDetail(SibomCatalog.Entry img, String text) {
        List<String> matchedKeywords = new ArrayList<>();
        List<String> matchedTriggers = new ArrayList<>();
        int score = 0;
        for (String kw : img.keywords()) {
            if (kw != null && !kw.isEmpty() && text.contains(kw)) {
                score += KEYWORD_WEIGHT;
                matchedKeywords.add(kw);
            }
        }
        for (String tok : img.triggerTokens()) {
            if (tok != null && !tok.isEmpty() && text.contains(tok)) {
                score += TRIGGER_WEIGHT;
                matchedTriggers.add(tok);
            }
        }
        return new ScoreDetail(score, List.copyOf(matchedKeywords), List.copyOf(matchedTriggers));
    }

    /**
     * Score image against text (delegates to scoreDetail).
     */
    static int score(SibomCatalog.Entry img, String text) {
        return scoreDetail(img, text).score();
    }

    /**
     * Score body (required) and optional title against each image; return top ≤12 with scores and match details.
     * Blank/null body → empty list. Zero-score images are omitted.
     */
    public List<ScoredCandidate> scoreDetailed(String body, String title) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        List<SibomCatalog.Entry> images = SibomCatalog.entries();
        if (images.isEmpty()) {
            return List.of();
        }

        StringBuilder hay = new StringBuilder(body.length() + 64);
        hay.append(body);
        if (title != null && !title.isBlank()) {
            hay.append('\n').append(title);
        }
        String text = hay.toString();

        List<ScoredCandidateInternal> scored = new ArrayList<>(images.size());
        for (int i = 0; i < images.size(); i++) {
            SibomCatalog.Entry img = images.get(i);
            ScoreDetail detail = scoreDetail(img, text);
            if (detail.score() > 0) {
                scored.add(new ScoredCandidateInternal(img.id(), detail, i));
            }
        }
        scored.sort(Comparator
                .comparingInt((ScoredCandidateInternal s) -> s.detail.score()).reversed()
                .thenComparingInt(ScoredCandidateInternal::catalogIndex));

        int n = Math.min(MAX_CANDIDATES, scored.size());
        List<ScoredCandidate> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ScoredCandidateInternal s = scored.get(i);
            out.add(new ScoredCandidate(s.id, s.detail.score(), s.detail.matchedKeywords(), s.detail.matchedTriggers()));
        }
        return List.copyOf(out);
    }

    private record Scored(String id, int score, int catalogIndex) {}

    private record ScoredCandidateInternal(String id, ScoreDetail detail, int catalogIndex) {}
}
