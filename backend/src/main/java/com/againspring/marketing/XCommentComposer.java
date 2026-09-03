package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.XPersonaExample;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.LlmImage;
import com.againspring.llm.PromptSanitizer;
import com.againspring.llm.prompt.PromptLoader;
import com.againspring.repository.ai.SystemSettingRepository;
import com.againspring.repository.marketing.XPersonaExampleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Drafts X replies and ritual lines in the learned persona voice.
 * LLM output is never posted as-is without skip/safety checks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XCommentComposer {

    static final String OUTBOUND_REPLY_PROMPT = "marketing/x-outbound-reply.md";
    static final String OUTBOUND_DONTS_PROMPT = "marketing/x-outbound-donts.md";
    static final String ORIGINAL_POST_PROMPT = "marketing/x-original-post.md";
    static final int ORIGINAL_MAX_CHARS = 140;
    static final int ORIGINAL_MAX_LINES = 3;

    public record Draft(boolean skip, String body, String skipReason) {
        public static Draft of(String body) {
            return new Draft(false, body, null);
        }

        public static Draft skipped(String reason) {
            return new Draft(true, null, reason);
        }
    }

    private final SystemSettingRepository systemSettingRepository;
    private final LLMProvider llmProvider;
    private final PromptSanitizer promptSanitizer;
    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper;
    private final XPersonaExampleRepository exampleRepository;

    @Value("${llm.enabled:true}")
    private boolean llmEnabled;

    @Value("${llm.claude-code.model:claude-haiku-4-5-20251001}")
    private String model;

    /** Reply to someone else's tweet/comment. Persona from marketing.x.persona_profile_json. */
    public Draft composeReply(String targetText, String parentContext) {
        if (!llmEnabled) {
            return Draft.skipped("DEV_LLM_OFF");
        }
        String profile = readProfile();
        String safeProfile = promptSanitizer.sanitize(profile);
        String safeTarget = promptSanitizer.sanitize(targetText);
        String safeParent = promptSanitizer.sanitize(parentContext);
        String prompt = """
            당신은 X 계정 @againspring_net의 목소리로 짧은 댓글을 답니다.
            1-2줄. 반말과 해요체 혼용. ㅋㅋㅋ 가능. 습니다체 금지.
            판결/승패/유무죄 금지. 공감만. 처방하지 마세요.

            목소리 프로필:
            <user_input>
            %s
            </user_input>

            대상 트윗:
            <user_input>
            %s
            </user_input>

            부모 맥락:
            <user_input>
            %s
            </user_input>

            댓글 본문만 출력하세요. 할 말이 없으면 '할 말 없음'만 출력하세요.
            """.formatted(safeProfile, safeTarget, safeParent);
        return invokeDraft(prompt);
    }

    /**
     * Outbound JSON compose. Photo bytes travel as {@link LlmImage}, never inside the prompt.
     */
    public Draft composeOutbound(String tweetText, List<String> peerReplies, String photoJpegBase64) {
        return composeOutbound(tweetText, peerReplies, photoJpegBase64, null);
    }

    /**
     * Outbound compose with a held-out tweet id skipped in TIMELINE few-shot
     * (shadow eval must not see the gold example being scored).
     */
    public Draft composeOutbound(
            String tweetText, List<String> peerReplies, String photoJpegBase64, String excludeTweetId) {
        if (!llmEnabled) {
            return Draft.skipped("DEV_LLM_OFF");
        }
        String instructions;
        String donts;
        try {
            instructions = promptLoader.get(OUTBOUND_REPLY_PROMPT);
            donts = promptLoader.get(OUTBOUND_DONTS_PROMPT);
        } catch (Exception e) {
            log.warn("[x-composer] outbound prompt missing: {}", e.getMessage());
            return Draft.skipped("UNSURE");
        }
        String safeProfile = promptSanitizer.sanitize(readProfile());
        String safeTweet = promptSanitizer.sanitize(tweetText);
        String safePeers = promptSanitizer.sanitize(joinPeers(peerReplies));
        String safeDonts = promptSanitizer.sanitize(donts);
        String safeShots = promptSanitizer.sanitize(fewShotBlock(tweetText, excludeTweetId));
        String safeAvoid = promptSanitizer.sanitize(avoidDeletedBlock());
        String prompt = instructions + """

            목소리 프로필:
            <user_input>
            %s
            </user_input>

            대상 트윗:
            <user_input>
            %s
            </user_input>

            다른 사람 댓글 (힌트, 베끼지 말 것):
            <user_input>
            %s
            </user_input>

            하지 말 것:
            <user_input>
            %s
            </user_input>

            운영자가 같은 종류 글에 직접 단 댓글 (베끼지 말고 결만):
            <user_input>
            %s
            </user_input>

            운영자가 지운 자동댓글 (이 결·문맥 빗나감 쓰지 말 것):
            <user_input>
            %s
            </user_input>
            """.formatted(safeProfile, safeTweet, safePeers, safeDonts, safeShots, safeAvoid);
        return invokeOutbound(prompt, photoJpegBase64);
    }

    /** slot = "morning" or "night". Warm 1-line encouragement in persona voice. */
    public Draft composeRitual(String slot) {
        if (!llmEnabled) {
            return Draft.skipped("DEV_LLM_OFF");
        }
        String profile = readProfile();
        String safeProfile = promptSanitizer.sanitize(profile);
        String safeSlot = promptSanitizer.sanitize(slot);
        String prompt = """
            당신은 X 계정 @againspring_net의 목소리로 짧은 안부 인사를 씁니다.
            슬롯: <user_input>%s</user_input> (morning=아침, night=밤)
            1줄. 따뜻한 응원. 반말과 해요체 혼용. ㅋㅋㅋ 가능. 습니다체 금지.
            제품 홍보 금지. URL 금지. 판결/승패/유무죄 금지.

            목소리 프로필:
            <user_input>
            %s
            </user_input>

            인사 한 줄만 출력하세요. 할 말이 없으면 '할 말 없음'만 출력하세요.
            """.formatted(safeSlot, safeProfile);
        return invokeDraft(prompt);
    }

    /**
     * Original post from a plaza story scoop. Length guard is 140 chars / 3 lines
     * (not the 40-char outbound comment guard).
     */
    public Draft composeOriginal(String storySummary, String link) {
        if (!llmEnabled) {
            return Draft.skipped("DEV_LLM_OFF");
        }
        String instructions;
        try {
            instructions = promptLoader.get(ORIGINAL_POST_PROMPT);
        } catch (Exception e) {
            log.warn("[x-composer] original prompt missing: {}", e.getMessage());
            return Draft.skipped("UNSURE");
        }
        String donts;
        try {
            donts = promptLoader.get(OUTBOUND_DONTS_PROMPT);
        } catch (Exception e) {
            donts = "";
        }
        String safeProfile = promptSanitizer.sanitize(readProfile());
        String safeStory = promptSanitizer.sanitize(storySummary);
        String safeLink = promptSanitizer.sanitize(link);
        String safeDonts = promptSanitizer.sanitize(donts);
        String safeShots = promptSanitizer.sanitize(fewShotPostBlock());
        String prompt = instructions + """

            목소리 프로필:
            <user_input>
            %s
            </user_input>

            사연 요약:
            <user_input>
            %s
            </user_input>

            링크:
            <user_input>
            %s
            </user_input>

            하지 말 것:
            <user_input>
            %s
            </user_input>

            운영자 원글 예시 (베끼지 말고 결만):
            <user_input>
            %s
            </user_input>
            """.formatted(safeProfile, safeStory, safeLink, safeDonts, safeShots);
        try {
            String raw = llmProvider.invoke(prompt, model);
            return toOriginalDraft(raw);
        } catch (Exception e) {
            log.warn("[x-composer] original invoke failed: {}", e.getMessage());
            return Draft.skipped("LLM_ERROR");
        }
    }

    private Draft invokeOutbound(String prompt, String photoJpegBase64) {
        try {
            String raw;
            if (photoJpegBase64 != null && !photoJpegBase64.isBlank()) {
                raw = llmProvider.invoke(
                    prompt, model, List.of(new LlmImage("image/jpeg", photoJpegBase64)));
            } else {
                raw = llmProvider.invoke(prompt, model);
            }
            return toOutboundDraft(raw);
        } catch (UnsupportedOperationException e) {
            log.warn("[x-composer] vision unavailable: {}", e.getMessage());
            return Draft.skipped("VISION_FAIL");
        } catch (Exception e) {
            log.warn("[x-composer] outbound invoke failed: {}", e.getMessage());
            return Draft.skipped("LLM_ERROR");
        }
    }

    private Draft invokeDraft(String prompt) {
        try {
            String raw = llmProvider.invoke(prompt, model);
            return toDraft(raw);
        } catch (Exception e) {
            log.warn("[x-composer] invoke failed: {}", e.getMessage());
            return Draft.skipped("LLM_ERROR");
        }
    }

    private Draft toOutboundDraft(String raw) {
        if (raw == null || raw.isBlank()) {
            return Draft.skipped("UNSURE");
        }
        if (XPersonaLearnService.looksLikeLlmError(raw)) {
            return Draft.skipped("LLM_ERROR");
        }
        JsonNode n = parseOutboundJson(raw);
        if (n == null) {
            return Draft.skipped("UNSURE");
        }
        if (!n.path("ok").asBoolean(false)) {
            return Draft.skipped("UNSURE");
        }
        String body = n.path("body").asText("");
        if (body == null || body.isBlank()) {
            return Draft.skipped("UNSURE");
        }
        if (XPersonaLearnService.looksLikeLlmError(body)) {
            return Draft.skipped("LLM_ERROR");
        }
        return Draft.of(body);
    }

    private JsonNode parseOutboundJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String json = extractJsonObject(raw);
        try {
            JsonNode n = objectMapper.readTree(json);
            if (n == null || !n.isObject()) {
                return null;
            }
            return n;
        } catch (Exception e) {
            return null;
        }
    }

    static String extractJsonObject(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            int end = s.lastIndexOf("```");
            if (nl >= 0 && end > nl) {
                s = s.substring(nl + 1, end).trim();
            }
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }

    static String joinPeers(List<String> peerReplies) {
        if (peerReplies == null || peerReplies.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        int i = 1;
        for (String p : peerReplies) {
            if (p == null || p.isBlank()) {
                continue;
            }
            lines.add(i + ". " + p.strip());
            i++;
            if (lines.size() >= 10) {
                break;
            }
        }
        return String.join("\n", lines);
    }

    String fewShotBlock(String tweetText) {
        return fewShotBlock(tweetText, null);
    }

    String fewShotBlock(String tweetText, String excludeTweetId) {
        List<XPersonaExample> gold;
        try {
            gold = exampleRepository.findTop40BySourceOrderByCreatedAtDesc(
                XPersonaExample.Source.TIMELINE);
        } catch (Exception e) {
            return "";
        }
        if (gold == null || gold.isEmpty()) {
            return "";
        }
        OutboundDraftGuard.Script want = OutboundDraftGuard.scriptOf(
            tweetText == null ? "" : tweetText);
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        for (XPersonaExample ex : gold) {
            if (ex == null || ex.getOperatorBody() == null || ex.getOperatorBody().isBlank()) {
                continue;
            }
            if (excludeTweetId != null && !excludeTweetId.isBlank()
                && excludeTweetId.equals(ex.getTweetId())) {
                continue;
            }
            String sit = ex.getPostText() == null ? "" : ex.getPostText();
            if (want != OutboundDraftGuard.Script.MIXED && !sit.isBlank()) {
                OutboundDraftGuard.Script got = OutboundDraftGuard.scriptOf(sit);
                if (got != OutboundDraftGuard.Script.MIXED && got != want) {
                    continue;
                }
            }
            sb.append("상황: ").append(sit.replace('\n', ' ').trim()).append('\n');
            sb.append("운영자: ").append(ex.getOperatorBody().replace('\n', ' ').trim()).append("\n---\n");
            kept++;
            if (kept >= 5) {
                break;
            }
        }
        return sb.toString();
    }

    String fewShotPostBlock() {
        List<XPersonaExample> gold;
        try {
            gold = exampleRepository.findTop40BySourceOrderByCreatedAtDesc(
                XPersonaExample.Source.TIMELINE_POST);
        } catch (Exception e) {
            return "";
        }
        if (gold == null || gold.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        for (XPersonaExample ex : gold) {
            if (ex == null || ex.getOperatorBody() == null || ex.getOperatorBody().isBlank()) {
                continue;
            }
            sb.append("원글: ").append(ex.getOperatorBody().replace('\n', ' ').trim()).append("\n---\n");
            kept++;
            if (kept >= 5) {
                break;
            }
        }
        return sb.toString();
    }

    String avoidDeletedBlock() {
        List<XPersonaExample> deleted;
        try {
            deleted = exampleRepository.findTop20BySourceOrderByCreatedAtDesc(
                XPersonaExample.Source.DELETED_AUTO);
        } catch (Exception e) {
            return "";
        }
        if (deleted == null || deleted.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        for (XPersonaExample ex : deleted) {
            if (ex == null || ex.getOperatorBody() == null || ex.getOperatorBody().isBlank()) {
                continue;
            }
            sb.append(ex.getOperatorBody().replace('\n', ' ').trim()).append('\n');
            kept++;
            if (kept >= 8) {
                break;
            }
        }
        return sb.toString();
    }

    private Draft toDraft(String raw) {
        if (raw == null || raw.isBlank()) {
            return Draft.skipped("NO_VOICE");
        }
        String body = raw.trim();
        if (XPersonaLearnService.looksLikeLlmError(body)) {
            return Draft.skipped("LLM_ERROR");
        }
        String compact = body.replaceAll("\\s+", "");
        if ("할말없음".equals(compact)
            || "SKIP".equalsIgnoreCase(compact)
            || "SKIPPED".equalsIgnoreCase(compact)) {
            return Draft.skipped("NO_VOICE");
        }
        return Draft.of(body);
    }

    private Draft toOriginalDraft(String raw) {
        Draft draft = toDraft(raw);
        if (draft.skip()) {
            return draft;
        }
        if (originalTooLong(draft.body())) {
            return Draft.skipped("TOO_LONG");
        }
        return draft;
    }

    static boolean originalTooLong(String body) {
        if (body == null) {
            return true;
        }
        String t = body.trim();
        if (t.length() > ORIGINAL_MAX_CHARS) {
            return true;
        }
        int lines = 1;
        for (int i = 0; i < t.length(); i++) {
            if (t.charAt(i) == '\n') {
                lines++;
                if (lines > ORIGINAL_MAX_LINES) {
                    return true;
                }
            }
        }
        return false;
    }

    private String readProfile() {
        return systemSettingRepository.findById(XPersonaLearnService.KEY_PROFILE)
            .map(SystemSetting::getSettingValue)
            .filter(v -> v != null && !v.isBlank())
            .orElse(XPersonaLearnService.SEED_PROFILE);
    }
}
