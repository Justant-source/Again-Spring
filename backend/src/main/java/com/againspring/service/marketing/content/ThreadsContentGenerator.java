package com.againspring.service.marketing.content;

import com.againspring.domain.marketing.MarketingContent;
import com.againspring.llm.LLMProvider;
import com.againspring.safety.MarketingCopyGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ThreadsContentGenerator implements ContentGenerator {

    private final LLMProvider llmProvider;
    private final MarketingCopyGuard copyGuard;

    @Override
    public MarketingContent.Platform supports() {
        return MarketingContent.Platform.THREADS;
    }

    @Override
    public GenerationOutput generate(GenerationContext ctx) throws Exception {
        String prompt = buildPrompt(ctx.simulationSummary(), ctx.relationType(), ctx.templateBody());
        String rawResponse = llmProvider.invoke(prompt, "claude-sonnet-4-6");
        String sanitized = copyGuard.sanitize(rawResponse);
        log.info("Generated Threads content for relation type: {}", ctx.relationType());
        return GenerationOutput.textOnly(sanitized);
    }

    private String buildPrompt(String simulationSummary, String relationType, String templateBody) {
        String templateSection = (templateBody != null && !templateBody.isBlank())
                ? "\n\nUse this content template as the base structure:\n" + templateBody
                : "";
        return """
                You are a marketing copywriter for "다시봄" (Again Spring), an AI conflict mediation tool.

                Based on the following conflict simulation summary, write a single Threads post in Korean.
                The post must be under 300 characters.
                Include exactly 3 relevant hashtags at the end.
                Focus on the value of open communication and relationship healing.
                Reference the tool: "다시봄 AI 갈등 중재 도우미"

                Simulation Summary:
                %s

                Relationship Type: %s
                %s
                Do not use clinical terms like 상담, 치료, 정신과, or absolute claims like 확실, 보장, 반드시.
                Do not use emojis.
                """.formatted(simulationSummary, relationType, templateSection);
    }
}
