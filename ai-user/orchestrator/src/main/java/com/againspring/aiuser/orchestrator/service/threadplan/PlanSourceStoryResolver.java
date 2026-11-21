package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.AiLearningClient;
import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.service.PersonaHistoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves example_bank grounding for PLAN AI_POST generation via popularity claim
 * ({@link AiLearningClient#claimPopularSource}) — no findSimilar primary selection,
 * no archetype freestyle fallback when the claim pool is empty.
 *
 * <p>persona-diversity-v4 WP2 계약7 — claim 성공 후 llm 워커 {@code /v2/extract-skeleton}로
 * "뼈대"만 뽑아 {@code sourceContext}에 담는다. 원문 본문·topicSeed 원문 필드는 더 이상
 * sourceContext에 실리지 않는다. 골격 추출이 실패하면 그 소스를 release하고 다음 소스로
 * 최대 3회 재시도한다(기존 fill 재시도 관례 재사용).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanSourceStoryResolver {
    /** 계약3 §3 — 골격 추출 실패 시 release 후 재시도하는 최대 횟수. */
    static final int MAX_SKELETON_ATTEMPTS = 3;

    /** 실제 댓글 참고 최대 개수(llm 계약). */
    public static final int COMMENT_STYLE_LIMIT = 12;

    private final AiLearningClient aiLearningClient;
    private final PersonaHistoryStore personaHistoryStore;
    private final LlmAiUserClient llmAiUserClient;

    public record ResolvedSource(
            String topicSeed,
            Map<String, Object> sourceContext,
            boolean reconstructMode,
            Long sourceExampleId,
            String sourceBody,
            String sourceCommunity,
            String sourceUrl,
            String sourceTitle,
            String dynamicExamples,
            List<String> recentBodies,
            List<String> commentStyleReference
    ) {
        /** 실제 댓글 참고가 없는 호출자용 호환 생성자. */
        public ResolvedSource(
                String topicSeed, Map<String, Object> sourceContext, boolean reconstructMode,
                Long sourceExampleId, String sourceBody, String sourceCommunity, String sourceUrl,
                String sourceTitle, String dynamicExamples, List<String> recentBodies) {
            this(topicSeed, sourceContext, reconstructMode, sourceExampleId, sourceBody, sourceCommunity,
                    sourceUrl, sourceTitle, dynamicExamples, recentBodies, List.of());
        }

        public Map<String, Object> provenanceForTrace() {
            Map<String, Object> m = new LinkedHashMap<>();
            if (sourceExampleId != null) m.put("sourceExampleId", sourceExampleId);
            if (sourceCommunity != null) m.put("sourceCommunity", sourceCommunity);
            if (sourceUrl != null) m.put("sourceUrl", sourceUrl);
            if (sourceTitle != null) m.put("sourceOriginalTitle", sourceTitle);
            if (sourceBody != null) m.put("sourceOriginalBody", truncate(sourceBody, 2000));
            m.put("reconstructMode", reconstructMode);
            // 투표 시점(PlanEngagementDispatcher)에 원문이 정한 쏠림 방향을 알 수 있게 기록한다.
            if (sourceContext != null && sourceContext.get("verdict_lean") != null) {
                m.put("verdictLean", String.valueOf(sourceContext.get("verdict_lean")).trim().toUpperCase());
            }
            return m;
        }
    }

    /**
     * Claim a popular crawl source and build reconstruct grounding.
     * Empty claim → {@link Optional#empty()} (caller must skip the slot; no freestyle).
     *
     * @param author          may be null (persona chosen after claim); recentBodies empty then
     * @param preferredSource {@code "blind"} or {@code "natepan"} (required)
     * @param categoryHint    plaza enum (COUPLE/MARRIED/…); scopes claim so reconstruct
     *                        content matches the post label (required for correct labeling)
     */
    public Optional<ResolvedSource> claimAndResolve(
            Persona author,
            String preferredSource,
            String reservationKey,
            Instant reserveUntil,
            String categoryHint) {
        return claimAndResolve(author, preferredSource, reservationKey, reserveUntil, categoryHint, null);
    }

    public Optional<ResolvedSource> claimAndResolve(
            Persona author,
            String preferredSource,
            String reservationKey,
            Instant reserveUntil,
            String categoryHint,
            java.util.Set<Long> excludeExampleIds) {
        String source = normalizePreferredSource(preferredSource);
        if (source == null || reservationKey == null || reservationKey.isBlank() || reserveUntil == null) {
            log.debug("claimAndResolve skipped: invalid args source={} key={} until={} categoryHint={}",
                    preferredSource, reservationKey, reserveUntil, categoryHint);
            return Optional.empty();
        }

        String plaza = normalizePlazaCategory(categoryHint);
        Set<Long> excluded = excludeExampleIds == null ? new HashSet<>() : new HashSet<>(excludeExampleIds);

        for (int attempt = 1; attempt <= MAX_SKELETON_ATTEMPTS; attempt++) {
            Optional<AiLearningClient.ExampleItem> claimed =
                    aiLearningClient.claimPopularSource(source, reservationKey, reserveUntil, plaza, excluded);
            if (claimed.isEmpty()) {
                log.info("claimAndResolve empty: no popular source for preferredSource={} plaza={} reservationKey={} attempt={}/{}",
                        source, plaza, reservationKey, attempt, MAX_SKELETON_ATTEMPTS);
                return Optional.empty();
            }

            AiLearningClient.ExampleItem primary = claimed.get();
            Optional<Map<String, Object>> skeleton = extractSkeleton(primary, plaza, reservationKey);
            if (skeleton.isEmpty()) {
                log.warn("claimAndResolve: skeleton extraction failed exampleId={} attempt={}/{} — releasing and retrying",
                        primary.getId(), attempt, MAX_SKELETON_ATTEMPTS);
                releaseQuietly(primary.getId(), reservationKey);
                excluded.add(primary.getId());
                continue;
            }
            if (!hasConflict(skeleton.get())) {
                log.warn("claimAndResolve: no conflict in source exampleId={} attempt={}/{} — rejecting and retrying",
                        primary.getId(), attempt, MAX_SKELETON_ATTEMPTS);
                aiLearningClient.rejectSource(primary.getId(), reservationKey, "no_conflict");
                excluded.add(primary.getId());
                continue;
            }

            return Optional.of(buildResolvedSource(author, source, primary, skeleton.get()));
        }

        log.warn("claimAndResolve: skeleton extraction/conflict check failed {} times, giving up preferredSource={} plaza={} reservationKey={}",
                MAX_SKELETON_ATTEMPTS, source, plaza, reservationKey);
        return Optional.empty();
    }

    /** 계약7 골격만 담은 {@code sourceContext} + 일반화된 topicSeed로 {@link ResolvedSource}를 만든다. */
    private ResolvedSource buildResolvedSource(
            Persona author, String source, AiLearningClient.ExampleItem primary, Map<String, Object> skeleton) {
        String dynamicExamples = optionalStyleExamples(author, source);
        List<String> recent = loadRecentPostBodies(author, 3);

        // item3: sourceContext에는 골격 JSON만 — 원문 body·raw topicSeed 필드는 담지 않는다.
        Map<String, Object> ctx = new LinkedHashMap<>(skeleton);
        ctx.remove("ok");
        ctx.remove("reason");

        return new ResolvedSource(
                generalizedTopicSeed(skeleton),
                ctx,
                true,
                primary.getId(),
                // sourceBody: 원문 그대로 유지 — StoryProfile 분석·provenance·(장래) SourceOverlapGuard
                // 입력으로 AiPostBundleService가 이미 쓰고 있어 이 브랜치에서는 필드 자체를 없애지
                // 않았다(레코드 arity 변경은 다른 WP가 만지는 파일까지 깨뜨린다 — 보고서 참조).
                // sourceContext(LLM 프롬프트로 나가는 값)에는 더 이상 실리지 않으므로 프롬프트
                // 누출 경로는 막혀 있다.
                primary.getContent(),
                primary.getSource() != null ? primary.getSource() : source,
                primary.getSourceUrl(),
                primary.getTitle(),
                dynamicExamples,
                recent,
                fetchSourceComments(primary.getId())
        );
    }

    /** 뼈대의 {@code has_conflict}. 키 없음·파싱 불가는 true(기존 동작 유지). */
    static boolean hasConflict(Map<String, Object> skeleton) {
        if (skeleton == null) return true;
        Object v = skeleton.get("has_conflict");
        if (v == null) return true;
        if (v instanceof Boolean b) return b;
        return !"false".equalsIgnoreCase(String.valueOf(v).trim());
    }

    /** 원문에 달린 실제 댓글(문체 참고). 실패·빈 응답이면 빈 목록 — 생성을 막지 않는다. */
    private List<String> fetchSourceComments(Long exampleId) {
        if (exampleId == null) return List.of();
        try {
            List<String> c = aiLearningClient.sourceComments(exampleId, COMMENT_STYLE_LIMIT);
            return c == null ? List.of() : c;
        } catch (Exception e) {
            log.debug("sourceComments failed exampleId={}: {}", exampleId, e.getMessage());
            return List.of();
        }
    }

    /** 원문 대신 골격에서 뽑은 짧은 일반화 문자열(200자 이내) — LLM 요청 {@code topicHint}로 나간다. */
    private static String generalizedTopicSeed(Map<String, Object> skeleton) {
        Object incident = skeleton.get("incident");
        if (incident != null && !String.valueOf(incident).isBlank()) {
            return truncate(String.valueOf(incident), 200);
        }
        Object category = skeleton.get("category");
        return category != null ? String.valueOf(category) : "";
    }

    /** llm 워커 {@code /v2/extract-skeleton} 호출. 실패(ok:false·네트워크 오류)는 empty. */
    private Optional<Map<String, Object>> extractSkeleton(
            AiLearningClient.ExampleItem primary, String plaza, String correlationId) {
        return llmAiUserClient.extractSkeleton(
                primary.getId(), plaza, primary.getTitle(), primary.getContent(), correlationId);
    }

    private void releaseQuietly(Long exampleId, String reservationKey) {
        if (exampleId == null) return;
        try {
            aiLearningClient.releaseSource(exampleId, reservationKey);
        } catch (Exception e) {
            log.debug("releaseSource failed (non-critical) exampleId={}: {}", exampleId, e.getMessage());
        }
    }

    /**
     * persona-diversity-v4 배선: {@link #claimAndResolve}로 이미 claim한 소스를 호출자가 명시적으로
     * 해제할 때 쓰는 공개 API(예: {@code PairedPostScheduler}의 b_side_viable 강등·
     * {@code SourceOverlapGuard} 거부 경로). 실패해도 예외를 던지지 않는다.
     */
    public void release(Long exampleId, String reservationKey) {
        releaseQuietly(exampleId, reservationKey);
    }

    /**
     * Legacy entry: maps persona {@code voice_type} → preferredSource and claims with a
     * short-lived reservation. Does not freestyle when the pool is empty — throws instead.
     * Prefer {@link #claimAndResolve} for new callers.
     */
    public ResolvedSource resolve(Persona author, String category, String topicHint) {
        String preferred = preferredSourceFromVoice(author);
        String reservationKey = "legacy-" + UUID.randomUUID();
        Instant reserveUntil = Instant.now().plus(24, ChronoUnit.HOURS);
        // topicHint unused for selection (popularity claim replaces topicSeed/findSimilar)
        return claimAndResolve(author, preferred, reservationKey, reserveUntil, category)
                .orElseThrow(() -> new IllegalStateException(
                        "No popular source available for preferredSource=" + preferred
                                + " category=" + category
                                + (topicHint == null || topicHint.isBlank() ? "" : " topicHint=" + topicHint.trim())));
    }

    /** BLIND → blind; everything else (incl. null) → natepan. */
    static String preferredSourceFromVoice(Persona author) {
        if (author != null && author.getVoiceProfile() != null) {
            Object vt = author.getVoiceProfile().get("voice_type");
            if (vt != null && "BLIND".equalsIgnoreCase(String.valueOf(vt).trim())) {
                return "blind";
            }
        }
        return "natepan";
    }

    /** Accept blind|natepan (any case); null/blank/other → null. */
    static String normalizePreferredSource(String preferredSource) {
        if (preferredSource == null || preferredSource.isBlank()) return null;
        String s = preferredSource.trim().toLowerCase();
        if ("blind".equals(s) || "natepan".equals(s)) return s;
        return null;
    }

    /** Plaza enum uppercase; blank/null → null (claim without category filter). */
    static String normalizePlazaCategory(String categoryHint) {
        if (categoryHint == null || categoryHint.isBlank()) return null;
        return categoryHint.trim().toUpperCase();
    }

    /** Best-effort style anchors; never blocks claim path. */
    private String optionalStyleExamples(Persona author, String source) {
        try {
            String register = resolveRegister(author);
            List<AiLearningClient.ExampleItem> style =
                    aiLearningClient.styleSample(source, "POST", register, 2, 350);
            if (style == null || style.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (AiLearningClient.ExampleItem e : style) {
                if (e == null || e.getContent() == null || e.getContent().isBlank()) continue;
                if (!sb.isEmpty()) sb.append("\n---\n");
                sb.append(truncate(e.getContent(), 350));
            }
            return sb.toString();
        } catch (Exception e) {
            log.debug("optional styleSample failed source={}: {}", source, e.getMessage());
            return "";
        }
    }

    private static String resolveRegister(Persona persona) {
        if (persona == null || persona.getVoiceProfile() == null) return "casual";
        Object formality = persona.getVoiceProfile().get("formality");
        if ("polite".equalsIgnoreCase(String.valueOf(formality))) return "polite";
        return "casual";
    }

    private List<String> loadRecentPostBodies(Persona author, int n) {
        if (author == null) return List.of();
        try {
            List<String> rows = personaHistoryStore.loadRecentPosts(author.getId(), n);
            List<String> out = new ArrayList<>();
            for (String body : rows) {
                if (body != null && !body.isBlank()) out.add(body);
            }
            return out;
        } catch (Exception e) {
            log.debug("recent posts load failed persona={}: {}", author.getId(), e.getMessage());
            return List.of();
        }
    }

    /** 문체 참고용 원문 최대 길이(코드포인트). llm 워커 계약: styleReference ≤ 1500자. */
    public static final int STYLE_REFERENCE_MAX_CODEPOINTS = 1500;

    /**
     * 2026-11 방침: 원문을 <b>문체 참고</b>로만 작성 호출에 전달한다(사실은 여전히 골격 기준).
     * {@code styleReference}=원문 앞 1500자(코드포인트), {@code sourceLength}=자르기 전 글자 수.
     * 원문이 없거나 공백뿐이면 두 필드 모두 생략. 본문을 쓰는 첫 호출에만 쓴다(follow-up 금지).
     */
    public static void putStyleReference(Map<String, Object> request, String sourceBody) {
        if (request == null || sourceBody == null || sourceBody.isBlank()) return;
        String t = sourceBody.trim();
        int total = t.codePointCount(0, t.length());
        String ref = total <= STYLE_REFERENCE_MAX_CODEPOINTS
                ? t
                : t.substring(0, t.offsetByCodePoints(0, STYLE_REFERENCE_MAX_CODEPOINTS));
        request.put("styleReference", ref);
        request.put("sourceLength", total);
    }

    /**
     * 원문이 claim된 사연의 생성 요청에 {@code commentStyleReference}(≤12)·{@code sourceTitle}을 넣는다.
     * 둘 다 비어 있으면 아무것도 넣지 않는다.
     */
    public static void putCommentReference(Map<String, Object> request, List<String> comments, String sourceTitle) {
        if (request == null) return;
        if (comments != null && !comments.isEmpty()) {
            List<String> cleaned = new ArrayList<>();
            for (String c : comments) {
                if (c != null && !c.isBlank()) cleaned.add(c.trim());
                if (cleaned.size() >= COMMENT_STYLE_LIMIT) break;
            }
            if (!cleaned.isEmpty()) request.put("commentStyleReference", cleaned);
        }
        if (sourceTitle != null && !sourceTitle.isBlank()) request.put("sourceTitle", sourceTitle.trim());
    }

    static String truncate(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    /** Prompt-friendly bullet list of recent bodies (anti-self-copy). */
    public static List<String> recentOutputsForRequest(List<String> bodies, int eachMax) {
        if (bodies == null || bodies.isEmpty()) return List.of();
        List<String> out = new ArrayList<>(bodies.size());
        for (int i = bodies.size() - 1; i >= 0; i--) {
            out.add(truncate(bodies.get(i).replaceAll("\\s+", " ").trim(), eachMax));
        }
        return out;
    }
}
