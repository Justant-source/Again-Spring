package com.againspring.service.community;

import com.againspring.llm.LLMProvider;
import com.againspring.llm.PromptSanitizer;
import com.againspring.marketing.MarketingBriefText;
import com.againspring.marketing.MarketingLlmAuthGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Stage-2 video variants (H3): when a story is committed to Reels and/or Shorts,
 * generate platform-specific hook + summary script (not full-story read) and optional
 * channel {@code sibom_plan} (AS-owned; guard downgrades, no 3rd LLM call).
 *
 * <p>Reels ≤30s · Shorts ≤45s. PromptSanitizer + no 판결/처방/승패/배심원.
 * Channel LLM calls stay separate when both platforms are requested.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoVariantService {

    public static final int MAX_DURATION_REELS_SEC = 30;
    public static final int MAX_DURATION_SHORTS_SEC = 45;

    private static final int BODY_PROMPT_MAX = 900;
    private static final int HOOK_STORE_MAX = 200;
    // TTS 한국어 발화 속도: 10.19 글자/초 (편차 0.35, 범위 9.41~10.52)
    // 실측: 256편 오디오 재생 시간 대비 워커 로그의 text=N자 기준
    // 보수 하림: 9.4 글자/초
    // Q6 구조: 훅(3s) + 본문(릴스 13~16s, 쇼츠 16~20s) + 댓글(별도) + CTA(3s)
    private static final double TTS_CHARS_PER_SEC = 10.19;
    // 릴스 본문 목표 13~16초 → (13+16)/2 * 10.19 = 148자 중앙값
    // 상한은 16.7초 (170자) 버림
    private static final int SCRIPT_REELS_MAX = 170;
    // 쇼츠 본문 목표 16~20초 → (16+20)/2 * 10.19 = 183자 중앙값
    // 상한은 20초 (205자) 버림
    private static final int SCRIPT_SHORTS_MAX = 205;
    private static final int SIBOM_CARD_MAX = 10;
    // 훅 목표 2.5~3초 → 3 * 10.19 = 30자 중앙값, 상한 34자
    private static final int HOOK_TARGET_CHARS = 30;
    private static final int HOOK_MAX_CHARS = 34;
    // Package-private (not private): SibomPlanGuard reuses this set for sibom_plan
    // caption validation so the forbidden-word list stays single-sourced (2026-08-29).
    static final Set<String> FORBIDDEN = Set.of(
            "판결", "처방", "승패", "승자", "패자", "가해자", "피해자", "배심원", "유죄", "무죄");

    /** Platform variant bundle for brief / Waggle render. */
    public record Variants(
            String hookReels,
            String scriptReels,
            Integer maxDurationReelsSec,
            String hookShorts,
            String scriptShorts,
            Integer maxDurationShortsSec,
            List<SibomPlanItem> sibomPlanReels,
            List<SibomPlanItem> sibomPlanShorts,
            Map<String, String> channelGenerationStatus,
            Map<String, Object> generationDiagnostics
    ) {
        public Variants {
            sibomPlanReels = sibomPlanReels == null ? List.of() : List.copyOf(sibomPlanReels);
            sibomPlanShorts = sibomPlanShorts == null ? List.of() : List.copyOf(sibomPlanShorts);
            channelGenerationStatus = channelGenerationStatus == null ? Map.of() : Map.copyOf(channelGenerationStatus);
            generationDiagnostics = generationDiagnostics == null ? Map.of() : Map.copyOf(generationDiagnostics);
        }

        /** Source-compatible constructor for callers that only supply the render brief. */
        public Variants(String hookReels, String scriptReels, Integer maxDurationReelsSec,
                        String hookShorts, String scriptShorts, Integer maxDurationShortsSec,
                        List<SibomPlanItem> sibomPlanReels, List<SibomPlanItem> sibomPlanShorts) {
            this(hookReels, scriptReels, maxDurationReelsSec, hookShorts, scriptShorts, maxDurationShortsSec,
                sibomPlanReels, sibomPlanShorts, Map.of(), Map.of());
        }

        /** Compatibility constructor retained for callers that provide channel statuses. */
        public Variants(String hookReels, String scriptReels, Integer maxDurationReelsSec,
                        String hookShorts, String scriptShorts, Integer maxDurationShortsSec,
                        List<SibomPlanItem> sibomPlanReels, List<SibomPlanItem> sibomPlanShorts,
                        Map<String, String> channelGenerationStatus) {
            this(hookReels, scriptReels, maxDurationReelsSec, hookShorts, scriptShorts, maxDurationShortsSec,
                sibomPlanReels, sibomPlanShorts, channelGenerationStatus, Map.of());
        }

        public static Variants empty() {
            return new Variants(null, null, null, null, null, null, List.of(), List.of(), Map.of(), Map.of());
        }

        /**
         * Active channel plan when generated for a single channel; prefers reels then shorts.
         * Dual-channel jobs should use {@link #sibomPlanReels()} / {@link #sibomPlanShorts()}.
         */
        public List<SibomPlanItem> sibomPlan() {
            if (!sibomPlanReels.isEmpty()) return sibomPlanReels;
            return sibomPlanShorts;
        }
    }

    /** Result of the AS-owned mandatory video quality gate, before any ASM request. */
    public record QualityGateResult(String failureCode, Map<String, Object> diagnostics) {
        public boolean isValid() {
            return failureCode == null;
        }
    }

    /**
     * Video publication is fail-closed: an absent or undersized guarded Sibom plan must
     * not silently become a text-only render. This deliberately validates the post-guard
     * plan, which is the exact plan sent to ASM.
     */
    public static QualityGateResult validateRequiredSibomPlans(
            Variants variants, boolean needReels, boolean needShorts) {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("reels_required", needReels ? SibomPlanGuard.MIN_REELS : 0);
        diagnostics.put("shorts_required", needShorts ? SibomPlanGuard.MIN_SHORTS : 0);
        int reelsCount = variants == null ? 0 : variants.sibomPlanReels().size();
        int shortsCount = variants == null ? 0 : variants.sibomPlanShorts().size();
        diagnostics.put("reels_guarded_plan_count", reelsCount);
        diagnostics.put("shorts_guarded_plan_count", shortsCount);
        if (variants != null && !variants.generationDiagnostics().isEmpty()) {
            diagnostics.putAll(variants.generationDiagnostics());
        }
        // Empty/unresolvable script (no sentence boundary found even after retry) must block
        // publish rather than send ASM/WaggleBot a blank or truncated narration (2026-08-16).
        if (needReels && isBlank(variants == null ? null : variants.scriptReels())) {
            return new QualityGateResult("SCRIPT_REELS_EMPTY", diagnostics);
        }
        if (needShorts && isBlank(variants == null ? null : variants.scriptShorts())) {
            return new QualityGateResult("SCRIPT_SHORTS_EMPTY", diagnostics);
        }
        if (needReels && reelsCount < SibomPlanGuard.MIN_REELS) {
            return new QualityGateResult(failureFor(channelStatus(variants, "instagram_reels"), reelsCount), diagnostics);
        }
        if (needShorts && shortsCount < SibomPlanGuard.MIN_SHORTS) {
            return new QualityGateResult(failureFor(channelStatus(variants, "youtube_shorts"), shortsCount), diagnostics);
        }
        return new QualityGateResult(null, diagnostics);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String channelStatus(Variants variants, String channel) {
        String status = variants == null ? null : variants.channelGenerationStatus().get(channel);
        return status == null ? "OK" : status;
    }

    private static String failureFor(String status, int planCount) {
        if (planCount > 0) return "SIBOM_PLAN_TOO_SHORT";
        return switch (status) {
            case "LLM_ERROR", "LLM_DISABLED", "LLM_TRANSIENT_ERROR" -> "VARIANT_LLM_ERROR";
            case "PARSE_ERROR" -> "VARIANT_PARSE_ERROR";
            case "CANDIDATE_POOL_TOO_SMALL" -> "SIBOM_CANDIDATE_POOL_TOO_SMALL";
            default -> "SIBOM_PLAN_EMPTY";
        };
    }

    @Qualifier("remoteLlmProvider")
    private final LLMProvider llmProvider;
    private final PromptSanitizer promptSanitizer;
    private final ObjectMapper objectMapper;
    private final MarketingLlmAuthGuard llmAuthGuard;

    @Value("${llm.model:claude-haiku-4-5-20251001}")
    private String model;

    @Value("${video-variant.enabled:true}")
    private boolean enabled;

    /**
     * Legacy overload (no sibom candidates). Prefer
     * {@link #generate(String, String, String, String, boolean, boolean, List)}.
     */
    public Variants generate(
            String masterHook,
            String hookEmotion,
            String title,
            String body,
            boolean needReels,
            boolean needShorts
    ) {
        return generate(masterHook, hookEmotion, title, body, needReels, needShorts, List.of());
    }

    /**
     * Generate variants for requested video platforms. When both Reels and Shorts are needed,
     * runs <strong>separate</strong> LLM calls (channel-specific script + {@code sibom_plan}).
     *
     * @param sibomCandidates shortlist ids (≤12 from post); prompt injects ≤10 one-line cards
     */
    public Variants generate(
            String masterHook,
            String hookEmotion,
            String title,
            String body,
            boolean needReels,
            boolean needShorts,
            List<String> sibomCandidates
    ) {
        if (!needReels && !needShorts) {
            return Variants.empty();
        }

        String safeHook = blankToNull(masterHook);
        String safeTitle = title != null ? title.trim() : "";
        String safeBody = body != null ? body.trim() : "";
        List<String> candidates = sibomCandidates != null ? sibomCandidates : List.of();

        String hookReels = null;
        String scriptReels = null;
        Integer durReels = null;
        List<SibomPlanItem> planReels = List.of();
        String hookShorts = null;
        String scriptShorts = null;
        Integer durShorts = null;
        List<SibomPlanItem> planShorts = List.of();
        Map<String, String> channelStatuses = new LinkedHashMap<>();
        Map<String, Object> generationDiagnostics = new LinkedHashMap<>();

        if (needReels) {
            ChannelResult reels = generateOneChannel(
                    safeHook, hookEmotion, safeTitle, safeBody,
                    SibomPlanGuard.Channel.REELS, candidates);
            durReels = MAX_DURATION_REELS_SEC;
            hookReels = reels.hook();
            scriptReels = reels.script();
            planReels = reels.sibomPlan();
            channelStatuses.put("instagram_reels", reels.generationStatus());
            generationDiagnostics.put("instagram_reels", reels.diagnostics());
        }
        if (needShorts) {
            ChannelResult shorts = generateOneChannel(
                    safeHook, hookEmotion, safeTitle, safeBody,
                    SibomPlanGuard.Channel.SHORTS, candidates);
            durShorts = MAX_DURATION_SHORTS_SEC;
            hookShorts = shorts.hook();
            scriptShorts = shorts.script();
            planShorts = shorts.sibomPlan();
            channelStatuses.put("youtube_shorts", shorts.generationStatus());
            generationDiagnostics.put("youtube_shorts", shorts.diagnostics());
        }

        // Dual: force distinct hooks/scripts so shared-pool dual jobs still unique-render.
        if (needReels && needShorts
                && hookReels != null && hookReels.equals(hookShorts)
                && scriptReels != null && scriptReels.equals(scriptShorts)) {
            hookShorts = distinctHook(hookShorts);
            // CTA ("당신은 어느 쪽?")는 아웃트로 씬에서만 나타나야 함 (2026-08-22)
            // 대본 append는 제거하고, WaggleBot outro 씬 렌더링에서 담당
        }

        return new Variants(
                hookReels, scriptReels, durReels,
                hookShorts, scriptShorts, durShorts,
                planReels, planShorts, channelStatuses, generationDiagnostics);
    }

    /**
     * Single-channel entry (Reels or Shorts). Returns a {@link Variants} with only that
     * channel's fields populated; {@link Variants#sibomPlan()} returns the channel plan.
     */
    public Variants generateForChannel(
            String masterHook,
            String hookEmotion,
            String title,
            String body,
            String channel,
            List<String> sibomCandidates
    ) {
        SibomPlanGuard.Channel ch = SibomPlanGuard.Channel.from(channel);
        if (ch == null) {
            return Variants.empty();
        }
        boolean needReels = ch == SibomPlanGuard.Channel.REELS;
        boolean needShorts = ch == SibomPlanGuard.Channel.SHORTS;
        return generate(masterHook, hookEmotion, title, body, needReels, needShorts, sibomCandidates);
    }

    private record ChannelResult(
            String hook,
            String script,
            List<SibomPlanItem> sibomPlan,
            String generationStatus,
            Map<String, Object> diagnostics
    ) {
        static ChannelResult empty(String status) {
            return new ChannelResult(null, null, List.of(), status, Map.of());
        }
    }

    private ChannelResult generateOneChannel(
            String masterHook,
            String hookEmotion,
            String title,
            String body,
            SibomPlanGuard.Channel channel,
            List<String> sibomCandidates
    ) {
        int minimum = channel.minSlots();
        int eligibleCandidateCount = eligibleCandidateCount(sibomCandidates);
        List<Map<String, Object>> attempts = new ArrayList<>();
        if (llmAuthGuard != null && llmAuthGuard.isCircuitOpen()) {
            Map<String, Object> diagnostics = new LinkedHashMap<>();
            diagnostics.put("eligible_candidate_count", eligibleCandidateCount);
            diagnostics.put("required_plan_count", minimum);
            diagnostics.put("attempts", List.of());
            return new ChannelResult(null, null, List.of(), "LLM_AUTH_CIRCUIT_OPEN", diagnostics);
        }
        if (eligibleCandidateCount < minimum) {
            Map<String, Object> diagnostics = new LinkedHashMap<>();
            diagnostics.put("eligible_candidate_count", eligibleCandidateCount);
            diagnostics.put("required_plan_count", minimum);
            diagnostics.put("attempts", List.of());
            return new ChannelResult(null, null, List.of(), "CANDIDATE_POOL_TOO_SMALL", diagnostics);
        }

        ChannelResult first = invokeChannelAttempt(
                masterHook, hookEmotion, title, body, channel, sibomCandidates, null, 1, attempts);
        ChannelResult resolved = first;
        if (shouldCorrect(first, minimum)) {
            resolved = invokeChannelAttempt(masterHook, hookEmotion, title, body, channel, sibomCandidates,
                    correctionInstruction(first, minimum), 2, attempts);
        }

        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("eligible_candidate_count", eligibleCandidateCount);
        diagnostics.put("required_plan_count", minimum);
        diagnostics.put("guarded_plan_count", resolved.sibomPlan().size());
        diagnostics.put("attempts", List.copyOf(attempts));
        return new ChannelResult(
                resolved.hook(), resolved.script(), resolved.sibomPlan(), resolved.generationStatus(), diagnostics);
    }

    private ChannelResult invokeChannelAttempt(
            String masterHook, String hookEmotion, String title, String body,
            SibomPlanGuard.Channel channel, List<String> sibomCandidates,
            String correction, int attemptNumber, List<Map<String, Object>> attempts
    ) {
        Instant startedAt = Instant.now();
        ChannelResult llm = ChannelResult.empty(enabled ? "PARSE_ERROR" : "LLM_DISABLED");
        String errorMessage = null;
        String raw = null;
        String promptText = null;
        if (enabled) {
            try {
                promptText = buildChannelPrompt(
                        masterHook, hookEmotion, title, body, channel, sibomCandidates, correction);
                raw = llmProvider.invoke(promptText, model);
                if (looksLikeLlmError(raw)) {
                    errorMessage = clamp(raw, 200);
                    llm = ChannelResult.empty(isTransientLlmFailureMessage(errorMessage)
                            ? "LLM_TRANSIENT_ERROR" : "LLM_ERROR");
                } else {
                    llm = parseChannelResult(raw, channel);
                }
            } catch (Exception e) {
                String status = isTransientLlmFailure(e) ? "LLM_TRANSIENT_ERROR" : "LLM_ERROR";
                errorMessage = e.getMessage();
                log.warn("VideoVariant LLM failed ({} attempt {}): {}", channel, attemptNumber, e.getMessage());
                llm = ChannelResult.empty(status);

                // Check for authentication errors and record in guard (Decision #6)
                if (llmAuthGuard != null && llmAuthGuard.isAuthenticationError(errorMessage)) {
                    if (llmAuthGuard.recordAuthError(errorMessage)) {
                        log.error("🚨 LLM authentication circuit opened after {} consecutive errors", attemptNumber);
                    }
                }
            }
        }
        int scriptMax = channel == SibomPlanGuard.Channel.REELS ? SCRIPT_REELS_MAX : SCRIPT_SHORTS_MAX;
        String hook = sanitizeHook(llm.hook(), masterHook, title);
        String script = sanitizeScript(llm.script(), body, scriptMax);
        String leakCheckSource = SibomPlanGuard.buildLeakIndex(title, body);
        SibomPlanGuard.GuardResult guardResult =
                SibomPlanGuard.guardWithLog(llm.sibomPlan(), channel, leakCheckSource);
        List<SibomPlanItem> plan = guardResult.items();
        // Neither the LLM candidate nor the raw-body fallback fit a sentence boundary —
        // more specific than the underlying LLM status so validateRequiredSibomPlans and
        // the correction retry can react to it (2026-08-16).
        String effectiveStatus = script == null ? "SCRIPT_QUALITY_ERROR" : llm.generationStatus();
        Map<String, Object> attempt = new LinkedHashMap<>();
        attempt.put("attempt", attemptNumber);
        attempt.put("started_at", startedAt.toString());
        attempt.put("duration_ms", Duration.between(startedAt, Instant.now()).toMillis());
        attempt.put("result", effectiveStatus);
        attempt.put("guarded_plan_count", plan.size());
        if (errorMessage != null) {
            attempt.put("error", clamp(errorMessage, 200));
        }
        attempt.put("model", model);
        attempt.put("prompt", promptText);
        if (raw != null) {
            attempt.put("response", raw);
        }
        attempt.put("sibom_plan_llm", llm.sibomPlan());
        attempt.put("guard_log", guardResult.log());
        attempts.add(Map.copyOf(attempt));
        return new ChannelResult(hook, script, plan, effectiveStatus, Map.of());
    }

    private static boolean shouldCorrect(ChannelResult result, int minimum) {
        if (result.script() == null) return true;
        if (result.sibomPlan().size() >= minimum) return false;
        return switch (result.generationStatus()) {
            case "OK", "PARSE_ERROR", "TRUNCATED_JSON" -> true;
            default -> false;
        };
    }

    private static String correctionInstruction(ChannelResult first, int required) {
        StringBuilder sb = new StringBuilder();
        String status = first.generationStatus();

        if ("TRUNCATED_JSON".equals(status)) {
            // Output was cut mid-response — request shorter output to complete within LLM limits
            sb.append("응답이 중간에 끊겼습니다. 훅과 대본을 더 짧게(한두 문장) 다시 작성하고, ")
              .append("sibom_plan은 필수 항목만 간결하게 작성하세요.");
        } else if (first.script() == null) {
            sb.append("직전 결과의 대본이 비어있거나 문장 단위로 정리할 수 없을 만큼 길었습니다. ")
              .append("완결된 문장으로 이루어진 짧은 요약 대본을 다시 작성하세요.");
        }

        int count = first.sibomPlan().size();
        if (count < required) {
            if (sb.length() > 0) sb.append(' ');
            sb.append("첫 결과의 가드 후 시봄이 플랜이 ").append(count).append("장으로 최소 ").append(required)
              .append("장에 미달합니다. 중복 image_id·swap_group을 피해 ").append(required)
              .append("장 이상 남도록 여유 있게 작성하세요.");
        }
        return sb.toString();
    }

    /** Count unique image/swap groups legally selectable by the prompt, including soft fill. */
    private static int eligibleCandidateCount(List<String> sibomCandidates) {
        Set<String> ids = new java.util.LinkedHashSet<>();
        if (sibomCandidates != null) {
            for (String raw : sibomCandidates) {
                if (raw != null && SibomCatalog.isKnown(raw.trim())) ids.add(raw.trim());
                if (ids.size() >= SIBOM_CARD_MAX) break;
            }
        }
        ids.addAll(SibomPlanGuard.SOFT_FILL_POOL);
        Set<String> groups = new java.util.HashSet<>();
        int count = 0;
        for (String id : ids) {
            String group = SibomCatalog.get(id).map(SibomCatalog.Entry::swapGroup).orElse("");
            if (group.isEmpty() || groups.add(group)) count++;
        }
        return count;
    }

    private static boolean isTransientLlmFailure(Exception error) {
        return isTransientLlmFailureMessage(error.getMessage());
    }

    private static boolean isTransientLlmFailureMessage(String message) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return lower.contains("timeout") || lower.contains("timed out")
                || lower.contains("502") || lower.contains("503") || lower.contains("504")
                || lower.contains("temporar") || lower.contains("overload")
                || lower.contains("connection reset") || lower.contains("service unavailable")
                || lower.contains("session limit") || lower.contains("rate limit");
    }

    private String buildChannelPrompt(
            String masterHook,
            String emotion,
            String title,
            String body,
            SibomPlanGuard.Channel channel,
            List<String> sibomCandidates,
            String correction
    ) {
        String rawBody = body != null ? body : "";
        if (rawBody.length() > BODY_PROMPT_MAX) {
            rawBody = rawBody.substring(0, BODY_PROMPT_MAX);
        }
        String safeTitle = promptSanitizer.sanitize(title != null ? title : "");
        String safeBody = promptSanitizer.sanitize(rawBody);
        String safeHook = promptSanitizer.sanitize(masterHook != null ? masterHook : "");
        String emo = PromoTitleService.validateEmotion(emotion);
        String emoLine = emo != null ? emo : "tension";

        boolean reels = channel == SibomPlanGuard.Channel.REELS;
        String platform = reels ? "instagram_reels(≤30초)" : "youtube_shorts(≤45초)";
        String hookKey = reels ? "hook_reels" : "hook_shorts";
        String scriptKey = reels ? "script_reels" : "script_shorts";
        // 목표 재생 시간 기준 권장 글자 수 (TTS 10.19 글자/초 기준)
        // 릴스: 13~16초 = 132~163자 (목표 125~155자, 상한 170자)
        // 쇼츠: 16~20초 = 163~204자 (목표 155~190자, 상한 205자)
        String scriptHint = reels
                ? "script는 125~155자 범위(재생시간 목표 13~16초). 너무 짧지 않게."
                : "script는 155~190자 범위(재생시간 목표 16~20초). 너무 짧지 않게.";
        int softTargetLo = reels ? 5 : 6;
        int softTargetHi = reels ? 5 : 7;

        String cards = buildSibomCards(sibomCandidates);
        String softFillList = String.join(", ", SibomPlanGuard.SOFT_FILL_POOL);

        return """
            당신은 SNS 숏폼용 카피라이터입니다.
            마스터 훅을 채널용으로 변형하고, 전문 낭독이 아닌 **요약 나레이션** 대본을 씁니다.
            같은 호출에서 시봄이 캐릭터 삽입 플랜(sibom_plan)도 제안합니다.

            ## 대상
            %s 만 (이 채널 전용 — 다른 채널 필드 금지)

            ## 규칙
            - 한국어. 이모지·해시태그·따옴표 장식·슬래시(/, ／) 금지. 절을 이을 때는 공백이나 개행만.
            - 판결/처방/승패/유무죄/가해자·피해자 단정 금지. 「배심원」 금지.
            - %s : 스크롤 스톱 한 줄(개행 허용). 마스터 훅과 글자 복제 금지·비틀기 허용.
            - %s : 정해진 구조로 작성:
              ① [사건] 구체적 사실(기간·나이·금액·횟수 등)을 문장 맨 앞에 두어 긴장을 만드세요. "진짜"·"완전"·"너무" 같은 감정 형용사 대신 사실 자체로 표현.
              ② [상대방 입장] 본문에 상대방 입장(partner_body)이 있으면 그것을 1문장으로 인용하세요. 없으면 이 단계 생략(추측 생성 금지).
              ③ [반전/결정적 한마디] 사건의 모순이나 예상을 깬 한 문장.
              ④ [여운] 화자의 미해결 질문으로 끝내기 / 시간 경과 암시 / 마지막에 짧은 문장 하나 — 시청자 여운 남기기.
              전문 낭독 금지. 공감비율 확인·댓글 작성·의견 요청·"당신은 어느 쪽" 같은 참여 유도 문구를 넣지 마세요(아웃트로에서만). %s
            - 메타포 일러스트 사용 금지. 시봄이만.
            - sibom_plan: 인트로 포함 최소 4장 필수(절대 하한 — 미달 시 발행 불가), 권장 %d~%d장.
              role=intro|peak|punch|soft_fill. intro/peak=large+hold, punch/soft_fill=small+punch.
              image_id와 swap_group은 전 항목에서 서로 달라야 합니다. 중복은 자동으로 제거되어
              장수가 줄어드니, 중복 제거 후에도 4장 이상 남도록 여유 있게 작성하세요.
              soft_fill은 아래 풀 id만·intro/peak 불가. image_id는 후보 카드 또는 soft_fill 풀에서만.
              caption은 카탈로그 재사용 또는 최대 10자(maxChars=10) 단문(판정·승패·처방 금지).
              caption은 감정·상황을 나타내는 명사구여야 합니다("낯섦", "말못함"처럼). 본문/제목 문장이나
              그 안의 어절(사건·금액·날짜·이름 등 구체적 사실)을 그대로 잘라 caption에 쓰지 마세요.
              beat_index=대본 비트 인덱스. 1피크=hook_emotion 정렬, 2피크=결말/반전만·후반.

            ## 시봄이 후보 카드 (id|arc|people|meaning|maxChars) — 전체 카탈로그 금지
            %s

            ## soft_fill 풀
            %s

            ## 보정 지시
            %s

            <user_input>
            마스터훅: %s
            hook_emotion: %s
            제목: %s
            본문: %s
            </user_input>

            ## 출력 (JSON only)
            {"%s":"...","%s":"...","sibom_plan":[{"role":"intro","image_id":"...","caption":"...","beat_index":0,"size":"large","dwell":"hold"}]}
            """.formatted(
                platform,
                hookKey,
                scriptKey,
                scriptHint,
                softTargetLo,
                softTargetHi,
                cards.isBlank() ? "(후보 없음 — soft_fill 풀만 사용 가능, 없으면 sibom_plan=[])" : cards,
                softFillList,
                correction == null ? "첫 시도입니다." : correction,
                safeHook,
                emoLine,
                safeTitle,
                safeBody,
                hookKey,
                scriptKey);
    }

    /** ≤10 one-line cards from candidates known in catalog. Never dump full 30. */
    static String buildSibomCards(List<String> sibomCandidates) {
        if (sibomCandidates == null || sibomCandidates.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String raw : sibomCandidates) {
            if (raw == null || raw.isBlank()) continue;
            var entry = SibomCatalog.get(raw.trim());
            if (entry.isEmpty()) continue;
            if (n > 0) sb.append('\n');
            sb.append(SibomCatalog.oneLineCard(entry.get()));
            n++;
            if (n >= SIBOM_CARD_MAX) break;
        }
        return sb.toString();
    }

    private ChannelResult parseChannelResult(String jsonResult, SibomPlanGuard.Channel channel) {
        try {
            String json = TonalizationService.extractJsonObject(jsonResult);
            JsonNode root = objectMapper.readTree(json);
            boolean reels = channel == SibomPlanGuard.Channel.REELS;
            String hook = reels
                    ? text(root, "hook_reels", "hookReels")
                    : text(root, "hook_shorts", "hookShorts");
            String script = reels
                    ? text(root, "script_reels", "scriptReels")
                    : text(root, "script_shorts", "scriptShorts");
            List<SibomPlanItem> plan = parseSibomPlan(root.path("sibom_plan"));
            if (plan.isEmpty()) {
                plan = parseSibomPlan(root.path("sibomPlan"));
            }
            return new ChannelResult(hook, script, plan, "OK", Map.of());
        } catch (Exception e) {
            // Detect truncated JSON (missing closing brace or trailing array open)
            // which indicates LLM output was cut mid-response (2026-08-23)
            if (looksLikeTruncatedJson(jsonResult)) {
                log.debug("VideoVariant detected truncated JSON ({}): {}", channel, e.getMessage());
                return ChannelResult.empty("TRUNCATED_JSON");
            }
            log.debug("VideoVariant parse failed ({}): {}", channel, e.getMessage());
            return ChannelResult.empty("PARSE_ERROR");
        }
    }

    /**
     * Detect if the JSON response was truncated mid-generation.
     * Symptoms: trailing array open, missing closing brace, incomplete object.
     */
    private static boolean looksLikeTruncatedJson(String s) {
        if (s == null || s.length() < 20) return false;
        String trimmed = s.trim();
        // Truncated at array open: ...,"sibom_plan":[
        if (trimmed.endsWith("[") || trimmed.endsWith(":[")) {
            return true;
        }
        // Truncated mid-field: ...,"hook_shorts": "some text without closing
        if (trimmed.endsWith("\"") && !trimmed.endsWith("\"}") && !trimmed.endsWith("\"],") && !trimmed.endsWith("\"]")) {
            return true;
        }
        // Missing closing brace but has opening
        int opens = 0;
        int closes = 0;
        for (char c : trimmed.toCharArray()) {
            if (c == '{') opens++;
            else if (c == '}') closes++;
        }
        // More opens than closes = incomplete object
        return opens > closes;
    }

    static List<SibomPlanItem> parseSibomPlan(JsonNode arr) {
        if (arr == null || !arr.isArray()) return List.of();
        List<SibomPlanItem> out = new ArrayList<>();
        for (JsonNode n : arr) {
            if (n == null || !n.isObject()) continue;
            String role = textNode(n, "role");
            String imageId = textNode(n, "image_id", "imageId");
            if (imageId == null || imageId.isBlank()) continue;
            String caption = textNode(n, "caption");
            Integer beat = null;
            if (n.has("beat_index") && n.get("beat_index").canConvertToInt()) {
                beat = n.get("beat_index").asInt();
            } else if (n.has("beatIndex") && n.get("beatIndex").canConvertToInt()) {
                beat = n.get("beatIndex").asInt();
            }
            String size = textNode(n, "size");
            String dwell = textNode(n, "dwell");
            out.add(new SibomPlanItem(role, imageId.trim(), caption, beat, size, dwell));
        }
        return out;
    }

    private static String textNode(JsonNode n, String... keys) {
        for (String key : keys) {
            JsonNode v = n.get(key);
            if (v != null && !v.isNull()) {
                String s = v.asText(null);
                if (s != null && !s.isBlank()) return s.trim();
            }
        }
        return null;
    }

    private static String text(JsonNode root, String snake, String camel) {
        String v = root.path(snake).asText(null);
        if (v == null || v.isBlank()) {
            v = root.path(camel).asText(null);
        }
        if (v == null || v.isBlank()) return null;
        return MarketingBriefText.normalize(v.trim());
    }

    private static String sanitizeHook(String candidate, String masterHook, String title) {
        String h = blankToNull(candidate);
        if (h == null) {
            h = blankToNull(masterHook);
        }
        if (h == null) {
            h = blankToNull(title);
        }
        if (h == null) return null;
        h = cleanSpokenText(h);
        if (h.isBlank() || looksLikeLlmError(h)) {
            h = blankToNull(masterHook);
            if (h == null) h = blankToNull(title);
            if (h == null) return null;
            h = cleanSpokenText(h);
        }
        return clamp(h, HOOK_STORE_MAX);
    }

    /**
     * Video script text (Reels/Shorts narration). Cuts only at a sentence boundary
     * within the platform's char budget — a mid-sentence character-count cut reads as
     * broken narration/subtitles (2026-08-16 shortform text quality fix). When neither
     * the LLM candidate nor the raw-body fallback contains a sentence boundary inside
     * the budget, this returns null so the caller can fail the job closed instead of
     * publishing a truncated fragment.
     */
    private static String sanitizeScript(String candidate, String body, int maxLen) {
        String s = blankToNull(candidate);
        if (s == null || looksLikeLlmError(s) || containsForbidden(s)) {
            return heuristicScript(body, maxLen);
        }
        s = cleanSpokenText(s);
        if (s.isBlank()) {
            return heuristicScript(body, maxLen);
        }
        if (s.length() <= maxLen) return s;
        String cut = sentenceBoundaryClamp(s, maxLen);
        return cut != null ? cut : heuristicScript(body, maxLen);
    }

    /** Raw-story fallback when the LLM candidate is missing/unusable. May return null (see below). */
    private static String heuristicScript(String body, int maxLen) {
        String b = PromoTitleService.stripSlashSeparators(
                MarketingBriefText.normalize(body != null ? body : "").trim().replaceAll("[ \\t]+", " "));
        if (b.isEmpty()) return null;
        if (b.length() <= maxLen) return b;
        return sentenceBoundaryClamp(b, maxLen);
    }

    /**
     * Cuts {@code s} at the last sentence-ending punctuation at/before {@code max},
     * searching an 80-char lookback window. When no boundary exists in that window,
     * falls back to cutting at the last space before {@code max} to avoid mid-word cuts.
     * Pathological: no sentence boundary AND no space in window → returns null only when
     * text is truly unsalvageable (2026-08-22 WS5.4 polback).
     */
    private static String sentenceBoundaryClamp(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty() || t.length() <= max) return t.isEmpty() ? null : t;
        String endings = ".!?…\n";
        int lookback = Math.min(max, 80);

        // First try: sentence boundary within lookback window
        for (int i = max - 1; i >= max - lookback; i--) {
            if (endings.indexOf(t.charAt(i)) >= 0) {
                String cut = t.substring(0, i + 1).trim();
                return cut.isEmpty() ? null : cut;
            }
        }

        // Fallback: no sentence boundary found — cut at last space before max
        // to avoid mid-word cuts that would truncate narration mid-syllable
        for (int i = max - 1; i >= Math.max(0, max - lookback); i--) {
            if (t.charAt(i) == ' ') {
                String cut = t.substring(0, i).trim();
                return cut.isEmpty() ? null : cut;
            }
        }

        // Last resort: no boundary or space in window — return partial to prevent null
        // (null triggers quality-gate failure; partial render is better than full block)
        String partial = t.substring(0, Math.min(max, t.length())).trim();
        return partial.isEmpty() ? null : partial;
    }

    /** Newlines stay; slash separators become spaces so TTS never reads "슬래시". */
    private static String cleanSpokenText(String s) {
        return PromoTitleService.stripSlashSeparators(
                stripForbidden(MarketingBriefText.normalize(s == null ? "" : s).trim()));
    }

    private static String distinctHook(String hook) {
        if (hook == null) return null;
        String flat = hook.replace("\n", " ").trim();
        if (flat.length() + 4 <= HOOK_STORE_MAX) {
            return clamp(flat + "…?", HOOK_STORE_MAX);
        }
        return clamp(flat, HOOK_STORE_MAX);
    }

    private static boolean containsForbidden(String s) {
        if (s == null) return false;
        String lower = s.toLowerCase(Locale.ROOT);
        for (String f : FORBIDDEN) {
            if (lower.contains(f.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static String stripForbidden(String s) {
        if (s == null) return "";
        String out = s;
        for (String f : FORBIDDEN) {
            out = out.replace(f, "");
        }
        return out.replaceAll(" {2,}", " ").trim();
    }

    /**
     * Lightweight LLM-error fingerprint (avoid posting credit/error blobs as copy).
     * {@code overloaded} is matched only as a provider error — the Sibom catalog id
     * {@code overloaded} in JSON ({@code "image_id": "overloaded"}) is valid content
     * (jobs 1009/1010, 2026-09-02).
     */
    static boolean looksLikeLlmError(String s) {
        if (s == null) return true;
        String t = s.toLowerCase(Locale.ROOT);
        if (t.contains("overloaded") && !hasProviderOverload(t)) {
            // 카탈로그 image_id "overloaded"는 정상 콘텐츠 — 'overloaded' 시그니처만 제외하고 나머지 검사
            String masked = t.replace("\"image_id\": \"overloaded\"", "").replace("overloaded", "");
            return com.againspring.service.ai.LlmErrorSignatures.get().containsSignature(masked);
        }
        return com.againspring.service.ai.LlmErrorSignatures.get().containsSignature(t)
                || hasProviderOverload(t);
    }

    /** Anthropic 529 / CLI "Overloaded" — not the catalog image_id string. */
    private static boolean hasProviderOverload(String lower) {
        if (lower.contains("overloaded_error") || lower.contains("api overloaded")) {
            return true;
        }
        int from = 0;
        while (from < lower.length()) {
            int idx = lower.indexOf("overloaded", from);
            if (idx < 0) {
                return false;
            }
            boolean jsonStringValue = idx > 0 && lower.charAt(idx - 1) == '"'
                    && idx + "overloaded".length() < lower.length()
                    && lower.charAt(idx + "overloaded".length()) == '"';
            if (!jsonStringValue) {
                return true;
            }
            from = idx + "overloaded".length();
        }
        return false;
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s.trim();
    }

    private static String clamp(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, max).trim();
    }
}
