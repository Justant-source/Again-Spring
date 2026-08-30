package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.PromptSanitizer;
import com.againspring.repository.ai.SystemSettingRepository;
import com.againspring.safety.KeywordGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Drafts X replies and ritual lines in the learned persona voice.
 * LLM output is never posted as-is without skip/safety checks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XCommentComposer {

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

    private Draft invokeDraft(String prompt) {
        try {
            String raw = llmProvider.invoke(prompt, model);
            return toDraft(raw);
        } catch (Exception e) {
            log.warn("[x-composer] invoke failed: {}", e.getMessage());
            return Draft.skipped("LLM_ERROR");
        }
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
