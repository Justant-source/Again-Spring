package com.againspring.service.marketing.content;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.againspring.llm.LLMProvider;
import com.againspring.llm.bridge.PromptSanitizer;
import com.againspring.safety.MarketingCopyGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates X (Twitter) marketing content from simulation summary.
 * Produces 3-5 tweets, each under 270 characters.
 */
@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class XContentGenerator {

	private final LLMProvider llmProvider;
	private final PromptSanitizer sanitizer;
	private final MarketingCopyGuard copyGuard;

	/**
	 * Generate tweet thread from simulation summary.
	 *
	 * @param simulationSummary Summary of the simulation (personas + outcome)
	 * @param relationType Type of relationship
	 * @return Generated tweet thread as raw LLM text
	 * @throws Exception if LLM invocation fails
	 */
	public String generate(String simulationSummary, String relationType) throws Exception {
		String sanitizedSummary = sanitizer.sanitize(simulationSummary, "marketing-x");

		String prompt = buildPrompt(sanitizedSummary, relationType);
		String rawResponse = llmProvider.invoke(prompt, "claude-sonnet-4-6");

		// Apply marketing copy guard
		String sanitized = copyGuard.sanitize(rawResponse);
		log.info("Generated X content for relation type: {}", relationType);

		return sanitized;
	}

	private String buildPrompt(String simulationSummary, String relationType) {
		return """
				You are a marketing copywriter for "다시봄" (Again Spring), an AI conflict mediation tool.

				Based on the following conflict simulation summary, generate a thread of 3-5 tweets in Korean.
				Each tweet must be under 270 characters.
				Focus on the value of conflict resolution and relationship healing.
				Reference the tool name: "다시봄 AI 갈등 중재 도구"

				Simulation Summary:
				%s

				Relationship Type: %s

				Generate the tweets as a numbered list (1. 2. 3. etc.).
				Include the disclaimer in the last tweet.
				Do not use clinical terms like 상담, 치료, 정신과, or absolute claims like 확실, 보장, 반드시.
				""".formatted(simulationSummary, relationType);
	}
}
