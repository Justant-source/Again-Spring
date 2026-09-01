package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.XPersonaExample;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.LlmImage;
import com.againspring.llm.PromptSanitizer;
import com.againspring.llm.prompt.PromptLoader;
import com.againspring.repository.ai.SystemSettingRepository;
import com.againspring.repository.marketing.XPersonaExampleRepository;
import com.againspring.safety.KeywordGuard;
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
    private final KeywordGuard keywordGuard;
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
        String safeShots = promptSanitizer.sanitize(fewShotBlock(tweetText, photoJpegBase64 != null));
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
            """.formatted(safeProfile, safeTweet, safePeers, safeDonts, safeShots);
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
        if (keywordGuard.scanLLMOutput(body).isBlocked() || containsVerdictBelt(body)) {
            return Draft.skipped("SAFETY");
        }
        String filtered = keywordGuard.applyOutputFilter(body);
        if (filtered == null || filtered.isBlank() || containsVerdictBelt(filtered)) {
            return Draft.skipped("SAFETY");
        }
        return Draft.of(filtered);
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

    String fewShotBlock(String tweetText, boolean hasPhoto) {
        List<XPersonaExample> drills;
        try {
            drills = exampleRepository.findTop40BySourceOrderByCreatedAtDesc(
                XPersonaExample.Source.DRILL);
        } catch (Exception e) {
            return "";
        }
        if (drills == null || drills.isEmpty()) {
            return "";
        }
        OutboundDraftGuard.Script want = OutboundDraftGuard.scriptOf(
            tweetText == null ? "" : tweetText);
        StringBuilder sb = new StringBuilder();
        int kept = 0;
        for (XPersonaExample ex : drills) {
            if (ex == null || ex.getOperatorBody() == null || ex.getOperatorBody().isBlank()) {
                continue;
            }
            if (ex.isHasPhoto() != hasPhoto) {
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
        if (keywordGuard.scanLLMOutput(body).isBlocked() || containsVerdictBelt(body)) {
            return Draft.skipped("SAFETY");
        }
        String filtered = keywordGuard.applyOutputFilter(body);
        if (filtered == null || filtered.isBlank() || containsVerdictBelt(filtered)) {
            return Draft.skipped("SAFETY");
        }
        return Draft.of(filtered);
    }

    private static boolean containsVerdictBelt(String text) {
        return text.contains("판결")
            || text.contains("유죄")
            || text.contains("무죄")
            || text.contains("가해자")
            || text.contains("피해자");
    }

    private String readProfile() {
        return systemSettingRepository.findById(XPersonaLearnService.KEY_PROFILE)
            .map(SystemSetting::getSettingValue)
            .filter(v -> v != null && !v.isBlank())
            .orElse(XPersonaLearnService.SEED_PROFILE);
    }
}
