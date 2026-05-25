package com.againspring.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Marketing-specific copy guard for forbidden words in generated content.
 * Loads Level B forbidden words from marketing-forbidden-words.yml.
 * Applied only to generated marketing content (X, Instagram, Naver Blog).
 * Does not affect user input validation.
 */
@Component
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@Slf4j
public class MarketingCopyGuard {

	@Value("${app.safety.marketing-forbidden-words-path:classpath:/safety/marketing-forbidden-words.yml}")
	private String configPath;

	private List<String> clinicalTerms = new ArrayList<>();
	private List<String> absoluteClaims = new ArrayList<>();
	private List<String> substituteTerms = new ArrayList<>();

	private static final String DISCLAIMER = "\n※ 다시봄은 전문 심리상담을 대체하지 않습니다. 위기 상황 시 정신건강 위기상담전화(1577-0199), 자살예방상담전화(1393), 여성긴급전화(1366)를 이용하세요.";

	@PostConstruct
	public void loadForbiddenWords() {
		try {
			Yaml yaml = new Yaml();
			String yamlContent = loadYamlFile();
			Map<String, Object> config = yaml.load(yamlContent);

			// Parse level_b section
			Map<String, Object> levelB = (Map<String, Object>) config.get("level_b");
			if (levelB != null) {
				List<String> clinical = (List<String>) levelB.get("clinical_terms");
				if (clinical != null) {
					clinicalTerms.addAll(clinical);
				}

				List<String> absolute = (List<String>) levelB.get("absolute_claims");
				if (absolute != null) {
					absoluteClaims.addAll(absolute);
				}

				List<String> substitute = (List<String>) levelB.get("substitute_terms");
				if (substitute != null) {
					substituteTerms.addAll(substitute);
				}
			}

			log.info("Marketing forbidden words loaded: clinical={}, absolute={}, substitute={}",
					clinicalTerms.size(), absoluteClaims.size(), substituteTerms.size());

		} catch (Exception e) {
			log.error("Failed to load marketing forbidden words configuration", e);
			throw new RuntimeException("Failed to load marketing forbidden words", e);
		}
	}

	/**
	 * Checks if content contains any Level B forbidden words.
	 *
	 * @param content Marketing content to check
	 * @return true if violations found, false otherwise
	 */
	public boolean hasViolations(String content) {
		if (content == null || content.isEmpty()) {
			return false;
		}

		String lowerContent = content.toLowerCase();

		for (String term : clinicalTerms) {
			if (lowerContent.contains(term.toLowerCase())) {
				return true;
			}
		}

		for (String claim : absoluteClaims) {
			if (lowerContent.contains(claim.toLowerCase())) {
				return true;
			}
		}

		for (String term : substituteTerms) {
			if (lowerContent.contains(term.toLowerCase())) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Sanitizes content by masking forbidden words and appending disclaimer if missing.
	 *
	 * @param content Marketing content to sanitize
	 * @return Sanitized content with masked words and disclaimer
	 */
	public String sanitize(String content) {
		if (content == null || content.isEmpty()) {
			return DISCLAIMER;
		}

		String result = content;

		// Mask clinical terms
		for (String term : clinicalTerms) {
			result = replaceCaseInsensitive(result, term, "[검토필요]");
		}

		// Mask absolute claims
		for (String claim : absoluteClaims) {
			result = replaceCaseInsensitive(result, claim, "[검토필요]");
		}

		// Mask substitute terms
		for (String term : substituteTerms) {
			result = replaceCaseInsensitive(result, term, "[검토필요]");
		}

		// Append disclaimer if not already present
		if (!result.contains("다시봄은 전문 심리상담을 대체하지 않습니다")) {
			result = result + DISCLAIMER;
		}

		return result;
	}

	/**
	 * Helper method to replace case-insensitively.
	 */
	private String replaceCaseInsensitive(String text, String search, String replacement) {
		StringBuilder sb = new StringBuilder();
		int lastIndex = 0;
		String lowerText = text.toLowerCase();
		String lowerSearch = search.toLowerCase();
		int index = lowerText.indexOf(lowerSearch);

		while (index >= 0) {
			sb.append(text, lastIndex, index).append(replacement);
			lastIndex = index + search.length();
			index = lowerText.indexOf(lowerSearch, lastIndex);
		}

		sb.append(text.substring(lastIndex));
		return sb.toString();
	}

	/**
	 * Helper method to load YAML file from resources.
	 */
	private String loadYamlFile() {
		try {
			if (configPath.startsWith("classpath:")) {
				String resourcePath = configPath.replace("classpath:", "");
				if (resourcePath.startsWith("/")) {
					resourcePath = resourcePath.substring(1);
				}
				ClassLoader classLoader = getClass().getClassLoader();
				java.net.URL url = classLoader.getResource(resourcePath);
				if (url == null) {
					throw new IllegalArgumentException("Cannot find resource: " + resourcePath);
				}
				return new String(java.nio.file.Files.readAllBytes(
						java.nio.file.Paths.get(url.toURI())));
			} else {
				return new String(java.nio.file.Files.readAllBytes(
						java.nio.file.Paths.get(configPath)));
			}
		} catch (Exception e) {
			log.error("Failed to load YAML file from path: {}", configPath, e);
			throw new RuntimeException("Failed to load YAML configuration", e);
		}
	}
}
