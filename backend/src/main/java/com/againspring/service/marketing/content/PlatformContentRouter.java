package com.againspring.service.marketing.content;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.againspring.domain.marketing.MarketingContent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Routes content generation requests to the appropriate platform generator.
 */
@Component
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PlatformContentRouter {

	private final XContentGenerator xGenerator;
	private final InstagramContentGenerator instagramGenerator;
	private final NaverBlogContentGenerator naverBlogGenerator;

	/**
	 * Route content generation to the appropriate platform generator.
	 *
	 * @param platform Target platform (X, INSTAGRAM, NAVER_BLOG)
	 * @param simulationSummary Summary of the simulation
	 * @param relationType Type of relationship
	 * @return Generated content as raw LLM text
	 * @throws Exception if LLM invocation fails or platform is unsupported
	 */
	public String generate(MarketingContent.Platform platform, String simulationSummary,
			String relationType) throws Exception {
		return switch (platform) {
		case X -> xGenerator.generate(simulationSummary, relationType);
		case INSTAGRAM -> instagramGenerator.generate(simulationSummary, relationType);
		case NAVER_BLOG -> naverBlogGenerator.generate(simulationSummary, relationType);
		default -> {
			log.error("Unsupported platform: {}", platform);
			throw new IllegalArgumentException("Unsupported platform: " + platform);
		}
		};
	}
}
