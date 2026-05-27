package com.againspring.service.marketing.content;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.bridge.PromptSanitizer;
import com.againspring.safety.MarketingCopyGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates Instagram marketing content.
 * Produces 200-300 character caption with up to 5 hashtags.
 */
@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class InstagramContentGenerator implements ContentGenerator {

    private final LLMProvider llmProvider;
    private final PromptSanitizer sanitizer;
    private final MarketingCopyGuard copyGuard;

    @Override
    public MarketingContent.Platform supports() {
        return MarketingContent.Platform.INSTAGRAM;
    }

    @Override
    public GenerationOutput generate(GenerationContext ctx) throws Exception {
        String sanitizedSummary = sanitizer.sanitize(ctx.simulationSummary(), "marketing-instagram");
        String prompt = buildPrompt(sanitizedSummary, ctx.relationType(), ctx.templateBody());
        String rawResponse = llmProvider.invoke(prompt, "claude-sonnet-4-6");
        String sanitizedRaw = copyGuard.sanitize(rawResponse);
        log.info("Generated Instagram content for relation type: {}", ctx.relationType());
        return GenerationOutput.fromLlmJson(sanitizedRaw);
    }

    private String buildPrompt(String simulationSummary, String relationType, String templateBody) {
        String templateSection = (templateBody != null && !templateBody.isBlank())
                ? "\n\nUse this content template as the base structure:\n" + templateBody
                : "";
        return """
                You are a marketing copywriter for "다시봄" (Again Spring), an AI conflict mediation tool.
                Tone: warm, empathetic, non-judgmental Korean. No clinical terms (상담, 치료, 정신과).
                No absolute claims (확실, 보장, 반드시). No emojis anywhere.

                Based on the conflict simulation summary below, respond with ONLY a valid JSON object:
                {
                  "caption": "Instagram caption in Korean (under 150 chars). Hook + CTA only. No emojis. No detailed info.",
                  "hashtags": ["#다시봄", "#갈등해결", "#관계회복", "#관계유형태그", "#감정태그"],
                  "slides": [
                    {"role": "COVER",   "title": "메타포 한 줄 (25자 이내)", "body": "", "visualHint": "gradient-warm"},
                    {"role": "SCENE",   "title": "그 때 그 말", "body": "대화 속 핵심 한 마디 (따옴표 포함, 40자 이내)", "visualHint": "chat-bubble"},
                    {"role": "FEELING", "title": "그 순간의 감정", "body": "감정 단어들 · 구분자 포함 (3-5개)", "visualHint": "emotion-palette"},
                    {"role": "NVC",     "title": "관찰과 욕구", "body": "관찰: ...\\n욕구: ...", "visualHint": "two-line"},
                    {"role": "CTA",     "title": "다시봄과 함께", "body": "지금 대화를 시작해보세요", "visualHint": "cta-logo"}
                  ]
                }

                Rules:
                - slides must have 5 to 6 items (add BONUS slide before CTA if simulation has extra insight).
                - Each slide title: under 25 chars. Each body: under 50 chars.
                - role values: COVER | SCENE | FEELING | NVC | CTA | BONUS
                - No emojis in title, body, caption, or hashtags.
                - Do not include markdown, code fences, or any text outside the JSON.

                Simulation Summary:
                %s

                Relationship Type: %s
                %s
                """.formatted(simulationSummary, relationType, templateSection);
    }
}
