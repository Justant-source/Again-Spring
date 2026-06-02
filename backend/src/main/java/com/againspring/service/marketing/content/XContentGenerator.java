package com.againspring.service.marketing.content;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.llm.LLMProvider;
import com.againspring.safety.MarketingCopyGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates X (Twitter) marketing content.
 * Produces 3-5 tweets, each under 270 characters.
 * NOTE: PromptSanitizer removed due to deletion of mediation code.
 */
@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class XContentGenerator implements ContentGenerator {

    private final LLMProvider llmProvider;
    private final MarketingCopyGuard copyGuard;

    @Override
    public MarketingContent.Platform supports() {
        return MarketingContent.Platform.X;
    }

    @Override
    public GenerationOutput generate(GenerationContext ctx) throws Exception {
        String prompt = buildPrompt(ctx.simulationSummary(), ctx.relationType(), ctx.templateBody());
        String rawResponse = llmProvider.invoke(prompt, "claude-sonnet-4-6");
        String sanitizedRaw = copyGuard.sanitize(rawResponse);
        log.info("Generated X content for relation type: {}", ctx.relationType());
        return GenerationOutput.fromLlmJson(sanitizedRaw);
    }

    private String buildPrompt(String simulationSummary, String relationType, String templateBody) {
        String templateSection = (templateBody != null && !templateBody.isBlank())
                ? "\n\nUse this content template as the base structure:\n" + templateBody
                : "";
        return """
                You are a marketing copywriter for "다시봄" (Again Spring), an AI conflict mediation tool.
                Tone: warm, empathetic, non-judgmental Korean. No clinical terms (상담, 치료, 정신과).
                No absolute claims (확실, 보장, 반드시). No emojis in quoteCard text.

                Based on the conflict simulation summary below, respond with ONLY a valid JSON object:
                {
                  "tweets": [
                    "tweet 1 text (under 270 chars, Korean)",
                    "tweet 2 text",
                    "tweet 3 text (include disclaimer: AI 기반 정서 지원 도구로, 전문 법률·의료 조언을 대신하지 않습니다.)"
                  ],
                  "quoteCard": {
                    "line1": "metaphor or key phrase from the simulation (under 30 chars)",
                    "line2": "empathy or insight sentence (under 40 chars)",
                    "attribution": "다시봄"
                  }
                }

                Rules:
                - tweets: 3 to 5 items. Each under 270 characters.
                - Include #다시봄 hashtag in the last tweet only.
                - quoteCard.line1 must come from the simulation's metaphor or core summary.
                - Do not include markdown, code fences, or any text outside the JSON.

                Simulation Summary:
                %s

                Relationship Type: %s
                %s
                """.formatted(simulationSummary, relationType, templateSection);
    }
}
