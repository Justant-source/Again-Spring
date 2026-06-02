package com.againspring.service.marketing.content;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.llm.LLMProvider;
import com.againspring.safety.MarketingCopyGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates Naver Blog marketing content.
 * Produces 800-1200 character blog post with structured headings.
 * NOTE: PromptSanitizer removed due to deletion of mediation code.
 */
@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class NaverBlogContentGenerator implements ContentGenerator {

    private final LLMProvider llmProvider;
    private final MarketingCopyGuard copyGuard;

    @Override
    public MarketingContent.Platform supports() {
        return MarketingContent.Platform.NAVER_BLOG;
    }

    @Override
    public GenerationOutput generate(GenerationContext ctx) throws Exception {
        String prompt = buildPrompt(ctx.simulationSummary(), ctx.relationType(), ctx.templateBody());
        String rawResponse = llmProvider.invoke(prompt, "claude-sonnet-4-6");
        String sanitizedRaw = copyGuard.sanitize(rawResponse);
        log.info("Generated Naver Blog content for relation type: {}", ctx.relationType());
        return GenerationOutput.fromLlmJson(sanitizedRaw);
    }

    private String buildPrompt(String simulationSummary, String relationType, String templateBody) {
        String templateSection = (templateBody != null && !templateBody.isBlank())
                ? "\n\nUse this content template as the base structure:\n" + templateBody
                : "";
        return """
                You are a marketing copywriter for "다시봄" (Again Spring), an AI conflict mediation tool.
                Tone: warm, empathetic Korean. No clinical terms (상담, 치료, 정신과).
                No absolute claims (확실, 보장, 반드시). No emojis. Good for SEO.

                Based on the conflict simulation summary, respond with ONLY a valid JSON object:
                {
                  "markdown": "Naver blog post in Korean markdown (400-600 chars). Include exactly these 3 image slot markers in order: <!-- IMG:chat-preview -->, <!-- IMG:report-needs-map -->, <!-- IMG:quote-card -->. Each marker on its own line with blank lines around it. Structure: 제목(#) → 도입(2-3문장) → <!-- IMG:chat-preview --> → 감정설명(2문장) → <!-- IMG:report-needs-map --> → 다시봄 소개(1문장) → <!-- IMG:quote-card --> → CTA(1문장) → 면책고지(1줄). Keep each section SHORT.",
                  "imageSlots": [
                    {"slot": "<!-- IMG:chat-preview -->",     "kind": "chat",         "quoteText": null},
                    {"slot": "<!-- IMG:report-needs-map -->", "kind": "report-needs", "quoteText": null},
                    {"slot": "<!-- IMG:quote-card -->",       "kind": "quote",        "quoteText": "핵심 문장 (25자 이내)"}
                  ],
                  "hashtags": ["#다시봄", "#갈등해결", "#관계회복", "#AI중재", "#감정정리"]
                }

                Rules:
                - Total markdown must be under 600 characters.
                - No emojis anywhere in the output.
                - Do not include code fences or any text outside the JSON.

                Simulation Summary:
                %s

                Relationship Type: %s
                %s
                """.formatted(simulationSummary, relationType, templateSection);
    }
}
