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
    public String generate(GenerationContext ctx) throws Exception {
        String sanitizedSummary = sanitizer.sanitize(ctx.simulationSummary(), "marketing-instagram");
        String prompt = buildPrompt(sanitizedSummary, ctx.relationType(), ctx.templateBody());
        String rawResponse = llmProvider.invoke(prompt, "claude-sonnet-4-6");
        String sanitized = copyGuard.sanitize(rawResponse);
        log.info("Generated Instagram content for relation type: {}", ctx.relationType());
        return sanitized;
    }

    private String buildPrompt(String simulationSummary, String relationType, String templateBody) {
        String templateSection = (templateBody != null && !templateBody.isBlank())
                ? "\n\nUse this content template as the base structure:\n" + templateBody
                : "";
        return """
                You are a marketing copywriter for "다시봄" (Again Spring), an AI conflict mediation tool.

                Based on the following conflict simulation summary, generate an Instagram caption in Korean.
                The caption should be 200-300 characters.
                Focus on the value of conflict resolution and relationship healing.
                Reference the tool name: "다시봄 AI 갈등 중재 도구"

                Simulation Summary:
                %s

                Relationship Type: %s
                %s
                Format:
                [Caption text]

                #다시봄 #갈등해결 #관계회복 [2 relation-type specific hashtags]

                Do not use clinical terms like 상담, 치료, 정신과, or absolute claims like 확실, 보장, 반드시.
                Include the disclaimer at the end.
                """.formatted(simulationSummary, relationType, templateSection);
    }
}
