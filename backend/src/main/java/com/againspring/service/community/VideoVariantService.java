package com.againspring.service.community;

import com.againspring.llm.LLMProvider;
import com.againspring.llm.PromptSanitizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;

/**
 * Stage-2 video variants (H3): when a story is committed to Reels and/or Shorts,
 * generate platform-specific hook + summary script (not full-story read).
 *
 * <p>Reels ≤30s · Shorts ≤45s. PromptSanitizer + no 판결/처방/승패/배심원.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoVariantService {

    public static final int MAX_DURATION_REELS_SEC = 30;
    public static final int MAX_DURATION_SHORTS_SEC = 45;

    private static final int BODY_PROMPT_MAX = 900;
    private static final int HOOK_STORE_MAX = 200;
    private static final int SCRIPT_REELS_MAX = 220;
    private static final int SCRIPT_SHORTS_MAX = 320;
    private static final String CTA_CLIFF =
            "공감 비율은? 댓글로 남겨주세요.";

    private static final Set<String> FORBIDDEN = Set.of(
            "판결", "처방", "승패", "승자", "패자", "가해자", "피해자", "배심원", "유죄", "무죄");

    /** Platform variant bundle for brief / Waggle render. */
    public record Variants(
            String hookReels,
            String scriptReels,
            Integer maxDurationReelsSec,
            String hookShorts,
            String scriptShorts,
            Integer maxDurationShortsSec
    ) {
        public static Variants empty() {
            return new Variants(null, null, null, null, null, null);
        }
    }

    @Qualifier("remoteLlmProvider")
    private final LLMProvider llmProvider;
    private final PromptSanitizer promptSanitizer;
    private final ObjectMapper objectMapper;

    @Value("${llm.model:claude-haiku-4-5-20251001}")
    private String model;

    @Value("${video-variant.enabled:true}")
    private boolean enabled;

    /**
     * Generate variants only for requested video platforms.
     *
     * @param needReels  {@code true} when targets include {@code instagram_reels}
     * @param needShorts {@code true} when targets include {@code youtube_shorts}
     */
    public Variants generate(
            String masterHook,
            String hookEmotion,
            String title,
            String body,
            boolean needReels,
            boolean needShorts
    ) {
        if (!needReels && !needShorts) {
            return Variants.empty();
        }

        String safeHook = blankToNull(masterHook);
        String safeTitle = title != null ? title.trim() : "";
        String safeBody = body != null ? body.trim() : "";

        Variants llm = null;
        if (enabled) {
            try {
                llm = parseResult(llmProvider.invoke(
                        buildPrompt(safeHook, hookEmotion, safeTitle, safeBody, needReels, needShorts),
                        model));
            } catch (Exception e) {
                log.warn("VideoVariant LLM failed: {}", e.getMessage());
            }
        }

        String hookReels = null;
        String scriptReels = null;
        Integer durReels = null;
        String hookShorts = null;
        String scriptShorts = null;
        Integer durShorts = null;

        if (needReels) {
            durReels = MAX_DURATION_REELS_SEC;
            hookReels = sanitizeHook(llm != null ? llm.hookReels() : null, safeHook, safeTitle);
            scriptReels = sanitizeScript(
                    llm != null ? llm.scriptReels() : null,
                    safeBody,
                    SCRIPT_REELS_MAX);
        }
        if (needShorts) {
            durShorts = MAX_DURATION_SHORTS_SEC;
            hookShorts = sanitizeHook(llm != null ? llm.hookShorts() : null, safeHook, safeTitle);
            scriptShorts = sanitizeScript(
                    llm != null ? llm.scriptShorts() : null,
                    safeBody,
                    SCRIPT_SHORTS_MAX);
        }

        // Dual: force distinct hooks/scripts so shared-pool dual jobs still unique-render.
        if (needReels && needShorts
                && hookReels != null && hookReels.equals(hookShorts)
                && scriptReels != null && scriptReels.equals(scriptShorts)) {
            hookShorts = distinctHook(hookShorts, "Shorts");
            scriptShorts = clamp(scriptShorts + " 당신은 어느 쪽?", SCRIPT_SHORTS_MAX);
        }

        return new Variants(hookReels, scriptReels, durReels, hookShorts, scriptShorts, durShorts);
    }

    private String buildPrompt(
            String masterHook,
            String emotion,
            String title,
            String body,
            boolean needReels,
            boolean needShorts
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

        String platforms;
        if (needReels && needShorts) {
            platforms = "instagram_reels(≤30초) 와 youtube_shorts(≤45초) 둘 다";
        } else if (needReels) {
            platforms = "instagram_reels(≤30초)만";
        } else {
            platforms = "youtube_shorts(≤45초)만";
        }

        return """
            당신은 SNS 숏폼(릴스/쇼츠)용 카피라이터입니다.
            마스터 훅을 플랫폼별로 변형하고, 전문 낭독이 아닌 **요약 나레이션 + 클리프행어 CTA** 대본을 씁니다.

            ## 대상
            %s

            ## 규칙
            - 한국어. 이모지·해시태그·따옴표 장식 금지.
            - 판결/처방/승패/유무죄/가해자·피해자 단정 금지. 「배심원」 금지.
            - hook_* : 스크롤 스톱 한 줄(개행 허용). 마스터 훅과 글자 복제 금지·비틀기 허용.
              **본문 속 구체적 사실(기간·나이·금액·횟수 등 숫자)을 문장 맨 앞에 두고, 그 직후에 모순·반전을 심으세요.**
              "진짜"·"완전"·"너무" 같은 감정 형용사 대신 사실 자체로 긴장을 만드세요.
              예(형식 참고용, 실제 사연 아님): "9년 사귄 사람이 결혼 얘기 나오자 혼자 여행부터 갑니다."
            - script_* : 자극 훅 톤 유지 → 갈등 핵심 2~4문장 요약 → 공감비율/댓글 유도 클리프행어.
              전문 낭독 금지. 문장마다 사실(숫자·행동) 하나씩 담아 전개하고 형용사로 때우지 마세요.
              Reels script는 짧게(말했을 때 ~25초), Shorts는 조금 더 길게(~40초).
            - Reels와 Shorts를 둘 다 쓸 때 hook/script는 **서로 다르게**.
            - 불필요 필드는 JSON에서 생략(해당 플랫폼만).

            <user_input>
            마스터훅: %s
            hook_emotion: %s
            제목: %s
            본문: %s
            </user_input>

            ## 출력 (JSON only)
            {"hook_reels":"...","script_reels":"...","hook_shorts":"...","script_shorts":"..."}
            """.formatted(platforms, safeHook, emoLine, safeTitle, safeBody);
    }

    private Variants parseResult(String jsonResult) {
        try {
            String json = TonalizationService.extractJsonObject(jsonResult);
            JsonNode root = objectMapper.readTree(json);
            return new Variants(
                    text(root, "hook_reels", "hookReels"),
                    text(root, "script_reels", "scriptReels"),
                    null,
                    text(root, "hook_shorts", "hookShorts"),
                    text(root, "script_shorts", "scriptShorts"),
                    null
            );
        } catch (Exception e) {
            log.debug("VideoVariant parse failed: {}", e.getMessage());
            return null;
        }
    }

    private static String text(JsonNode root, String snake, String camel) {
        String v = root.path(snake).asText(null);
        if (v == null || v.isBlank()) {
            v = root.path(camel).asText(null);
        }
        if (v == null || v.isBlank()) return null;
        return v.trim().replace("\\n", "\n");
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
        h = stripForbidden(h.replace("\r\n", "\n").replace('\r', '\n').trim());
        if (h.isBlank() || looksLikeLlmError(h)) {
            h = blankToNull(masterHook);
            if (h == null) h = blankToNull(title);
            if (h == null) return null;
            h = stripForbidden(h);
        }
        return clamp(h, HOOK_STORE_MAX);
    }

    private static String sanitizeScript(String candidate, String body, int maxLen) {
        String s = blankToNull(candidate);
        if (s == null || looksLikeLlmError(s) || containsForbidden(s)) {
            s = heuristicScript(body, maxLen);
        } else {
            s = stripForbidden(s.replace("\r\n", "\n").replace('\r', '\n').trim());
            if (s.isBlank()) {
                s = heuristicScript(body, maxLen);
            }
        }
        if (!s.contains("공감") && !s.contains("댓글")) {
            s = clamp(s + " " + CTA_CLIFF, maxLen);
        }
        return clamp(s, maxLen);
    }

    private static String heuristicScript(String body, int maxLen) {
        String b = body != null ? body.trim().replaceAll("\\s+", " ") : "";
        int budget = Math.max(40, maxLen - CTA_CLIFF.length() - 2);
        if (b.length() > budget) {
            b = b.substring(0, budget).trim() + "…";
        }
        if (b.isBlank()) {
            return CTA_CLIFF;
        }
        return b + " " + CTA_CLIFF;
    }

    private static String distinctHook(String hook, String suffixTag) {
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

    /** Lightweight LLM-error fingerprint (avoid posting credit/error blobs as copy). */
    private static boolean looksLikeLlmError(String s) {
        if (s == null) return true;
        String t = s.toLowerCase(Locale.ROOT);
        return t.contains("credit balance")
                || t.contains("rate limit")
                || t.contains("api error")
                || t.contains("overloaded")
                || t.contains("prompt is too long")
                || t.contains("i'm sorry")
                || t.contains("as an ai");
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
