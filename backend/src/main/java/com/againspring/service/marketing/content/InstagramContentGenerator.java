package com.againspring.service.marketing.content;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.againspring.llm.LLMProvider;
import com.againspring.llm.bridge.PromptSanitizer;
import com.againspring.safety.MarketingCopyGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates Instagram marketing content from simulation summary.
 * Produces 200-300 character caption with up to 5 hashtags.
 */
@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class InstagramContentGenerator {

	private final LLMProvider llmProvider;
	private final PromptSanitizer sanitizer;
	private final MarketingCopyGuard copyGuard;

	/**
	 * Generate Instagram caption from simulation summary.
	 *
	 * @param simulationSummary Summary of the simulation (personas + outcome)
	 * @param relationType Type of relationship
	 * @return Generated Instagram caption as raw LLM text
	 * @throws Exception if LLM invocation fails
	 */
	public String generate(String simulationSummary, String relationType) throws Exception {
		String sanitizedSummary = sanitizer.sanitize(simulationSummary, "marketing-instagram");

		String prompt = buildPrompt(sanitizedSummary, relationType);
		String rawResponse = llmProvider.invoke(prompt, "claude-sonnet-4-6");

		// Apply marketing copy guard
		String sanitized = copyGuard.sanitize(rawResponse);
		log.info("Generated Instagram content for relation type: {}", relationType);

		return sanitized;
	}

	private String buildPrompt(String simulationSummary, String relationType) {
		return """
				You are a marketing copywriter for "다시봄" (Again Spring), an AI conflict mediation tool.

				Based on the following conflict simulation summary, generate an Instagram caption in Korean.
				The caption should be 200-300 characters.
				Focus on the value of conflict resolution and relationship healing.
				Reference the tool name: "다시봄 AI 갈등 중재 도구"

				Simulation Summary:
				%s

				Relationship Type: %s

				Format:
				[Caption text]

				#다시봄 #갈등해결 #관계회복 [2 relation-type specific hashtags]

				Do not use clinical terms like 상담, 치료, 정신과, or absolute claims like 확실, 보장, 반드시.
				Include the disclaimer at the end.
				""".formatted(simulationSummary, relationType);
	}
}
