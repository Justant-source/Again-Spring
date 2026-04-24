package com.againspring.safety;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Keyword-based safety guard for detecting forbidden words in user input and LLM output.
 *
 * Loads a YAML configuration file at startup with keyword patterns organized by severity level:
 * - CRISIS: Immediate session termination (domestic violence, self-harm, etc.)
 * - LEVEL1: Legal risk terms, stigmatizing language
 * - LEVEL2: Clinical diagnostic terms, relationship termination encouragement
 * - LEVEL3: Judgment language (win/lose) - logged but no action
 * - LEVEL4: Relationship termination encouragement
 *
 * Matching is case-insensitive and substring-based (simple contains() for MVP).
 * To avoid false positives in Korean, all patterns are treated as plain substring matches.
 * Javadoc note: For production, consider word-boundary-aware matching or regex patterns
 * where specified, but current MVP uses simple substring matching.
 *
 * TODO Phase 7/8: Invoke scanUserInput() at every controller input point (Turn submission).
 * TODO Phase 8: Invoke scanLLMOutput() after LLM response before returning to user.
 */
@Component
@Slf4j
public class KeywordGuard {

	@Value("${app.safety.forbidden-words-path:classpath:/safety/forbidden-words.yml}")
	private String configPath;

	private List<KeywordPattern> crisisKeywords = new ArrayList<>();
	private List<KeywordPattern> level1Keywords = new ArrayList<>();
	private List<KeywordPattern> level2Keywords = new ArrayList<>();
	private List<KeywordPattern> level3Keywords = new ArrayList<>();
	private List<KeywordPattern> level4Keywords = new ArrayList<>();
	private List<OutputFilter> outputFilters = new ArrayList<>();

	@PostConstruct
	public void loadKeywords() {
		try {
			// Load YAML configuration
			Yaml yaml = new Yaml();
			String yamlContent = loadYamlFile();
			Map<String, Object> config = yaml.load(yamlContent);

			// Parse crisis keywords
			List<Map<String, Object>> crisisList = (List<Map<String, Object>>) config.get("crisis_keywords");
			if (crisisList != null) {
				for (Map<String, Object> item : crisisList) {
					KeywordPattern pattern = new KeywordPattern(
						(String) item.get("pattern"),
						Level.CRISIS,
						(String) item.get("category"),
						true // crisis flag
					);
					crisisKeywords.add(pattern);
				}
			}

			// Parse level1 keywords
			List<Map<String, Object>> level1List = (List<Map<String, Object>>) config.get("level1");
			if (level1List != null) {
				for (Map<String, Object> item : level1List) {
					KeywordPattern pattern = new KeywordPattern(
						(String) item.get("pattern"),
						Level.LEVEL1,
						(String) item.get("category"),
						(Boolean) item.getOrDefault("crisis", false)
					);
					level1Keywords.add(pattern);
				}
			}

			// Parse level2 keywords
			List<Map<String, Object>> level2List = (List<Map<String, Object>>) config.get("level2");
			if (level2List != null) {
				for (Map<String, Object> item : level2List) {
					KeywordPattern pattern = new KeywordPattern(
						(String) item.get("pattern"),
						Level.LEVEL2,
						(String) item.get("category"),
						(Boolean) item.getOrDefault("crisis", false)
					);
					level2Keywords.add(pattern);
				}
			}

			// Parse level3 keywords
			List<Map<String, Object>> level3List = (List<Map<String, Object>>) config.get("level3");
			if (level3List != null) {
				for (Map<String, Object> item : level3List) {
					KeywordPattern pattern = new KeywordPattern(
						(String) item.get("pattern"),
						Level.LEVEL3,
						(String) item.get("category"),
						(Boolean) item.getOrDefault("crisis", false)
					);
					level3Keywords.add(pattern);
				}
			}

			// Parse level4 keywords
			List<Map<String, Object>> level4List = (List<Map<String, Object>>) config.get("level4");
			if (level4List != null) {
				for (Map<String, Object> item : level4List) {
					KeywordPattern pattern = new KeywordPattern(
						(String) item.get("pattern"),
						Level.LEVEL4,
						(String) item.get("category"),
						(Boolean) item.getOrDefault("crisis", false)
					);
					level4Keywords.add(pattern);
				}
			}

			// Parse output filters
			List<Map<String, Object>> filtersList = (List<Map<String, Object>>) config.get("output_filter");
			if (filtersList != null) {
				for (Map<String, Object> item : filtersList) {
					OutputFilter filter = new OutputFilter(
						(String) item.get("term"),
						(String) item.get("replacement")
					);
					outputFilters.add(filter);
				}
			}

			log.info("Safety keywords loaded: crisis={}, level1={}, level2={}, level3={}, level4={}, filters={}",
				crisisKeywords.size(), level1Keywords.size(), level2Keywords.size(),
				level3Keywords.size(), level4Keywords.size(), outputFilters.size());

		} catch (Exception e) {
			log.error("Failed to load safety keywords configuration", e);
			throw new RuntimeException("Failed to load safety keywords", e);
		}
	}

	/**
	 * Scans user input for forbidden keywords.
	 * Returns ScanResult with the highest-severity level detected.
	 *
	 * @param text User input text
	 * @param userId User identifier for audit logging
	 * @return ScanResult with matches, blocked status, and crisis flag
	 */
	public ScanResult scanUserInput(String text, String userId) {
		if (text == null || text.isEmpty()) {
			return ScanResult.empty();
		}

		String lowerText = text.toLowerCase();
		List<ScanResult.Match> allMatches = new ArrayList<>();
		Level maxLevel = null;
		boolean hasCrisis = false;

		// Check crisis keywords first (highest priority)
		for (KeywordPattern kp : crisisKeywords) {
			int pos = findCaseInsensitivePosition(lowerText, kp.pattern.toLowerCase());
			if (pos >= 0) {
				allMatches.add(new ScanResult.Match(kp.pattern, kp.level, kp.category, true, pos));
				maxLevel = Level.max(maxLevel == null ? kp.level : maxLevel, kp.level);
				hasCrisis = true;
			}
		}

		if (hasCrisis) {
			return ScanResult.crisisResult(allMatches);
		}

		// Check level1 (legal risk, stigma)
		for (KeywordPattern kp : level1Keywords) {
			int pos = findCaseInsensitivePosition(lowerText, kp.pattern.toLowerCase());
			if (pos >= 0) {
				allMatches.add(new ScanResult.Match(kp.pattern, kp.level, kp.category, false, pos));
				maxLevel = Level.max(maxLevel == null ? kp.level : maxLevel, kp.level);
			}
		}

		if (maxLevel == Level.LEVEL1) {
			return ScanResult.blockedResult(Level.LEVEL1, allMatches);
		}

		// Check level2 (clinical terms, legal decisions)
		for (KeywordPattern kp : level2Keywords) {
			int pos = findCaseInsensitivePosition(lowerText, kp.pattern.toLowerCase());
			if (pos >= 0) {
				allMatches.add(new ScanResult.Match(kp.pattern, kp.level, kp.category, false, pos));
				maxLevel = Level.max(maxLevel == null ? kp.level : maxLevel, kp.level);
			}
		}

		if (maxLevel == Level.LEVEL2) {
			return ScanResult.warningResult(Level.LEVEL2, allMatches);
		}

		// Check level3 (judgment language)
		for (KeywordPattern kp : level3Keywords) {
			int pos = findCaseInsensitivePosition(lowerText, kp.pattern.toLowerCase());
			if (pos >= 0) {
				allMatches.add(new ScanResult.Match(kp.pattern, kp.level, kp.category, false, pos));
				maxLevel = Level.max(maxLevel == null ? kp.level : maxLevel, kp.level);
			}
		}

		if (maxLevel == Level.LEVEL3) {
			return ScanResult.warningResult(Level.LEVEL3, allMatches);
		}

		// Check level4 (relationship termination)
		for (KeywordPattern kp : level4Keywords) {
			int pos = findCaseInsensitivePosition(lowerText, kp.pattern.toLowerCase());
			if (pos >= 0) {
				allMatches.add(new ScanResult.Match(kp.pattern, kp.level, kp.category, false, pos));
				maxLevel = Level.max(maxLevel == null ? kp.level : maxLevel, kp.level);
			}
		}

		if (maxLevel == Level.LEVEL4) {
			return ScanResult.warningResult(Level.LEVEL4, allMatches);
		}

		return ScanResult.empty();
	}

	/**
	 * Scans LLM output for forbidden words and returns suggested replacements.
	 *
	 * @param text LLM output text
	 * @return ScanResult with replacement suggestions
	 */
	public ScanResult scanLLMOutput(String text) {
		if (text == null || text.isEmpty()) {
			return ScanResult.empty();
		}

		String lowerText = text.toLowerCase();
		List<ScanResult.Match> matches = new ArrayList<>();

		for (OutputFilter filter : outputFilters) {
			int pos = findCaseInsensitivePosition(lowerText, filter.term.toLowerCase());
			if (pos >= 0) {
				matches.add(new ScanResult.Match(
					filter.term,
					Level.LEVEL2, // output filters are treated as level2 for logging
					"OUTPUT_FILTER",
					false,
					pos
				));
			}
		}

		if (!matches.isEmpty()) {
			return ScanResult.warningResult(Level.LEVEL2, matches);
		}

		return ScanResult.empty();
	}

	/**
	 * Applies output filter replacements to text.
	 * Returns text with forbidden terms replaced by safe alternatives.
	 *
	 * @param text Input text
	 * @return Text with filters applied
	 */
	public String applyOutputFilter(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}

		String result = text;
		for (OutputFilter filter : outputFilters) {
			// Replace case-insensitively but preserve original text where possible
			result = replaceCaseInsensitive(result, filter.term, filter.replacement);
		}

		return result;
	}

	/**
	 * Helper method to find case-insensitive substring position.
	 */
	private int findCaseInsensitivePosition(String text, String pattern) {
		return text.indexOf(pattern);
	}

	/**
	 * Helper method to replace case-insensitively.
	 */
	private String replaceCaseInsensitive(String text, String search, String replacement) {
		StringBuilder sb = new StringBuilder();
		int lastIndex = 0;
		int index = findCaseInsensitivePosition(text.toLowerCase(), search.toLowerCase());

		while (index >= 0) {
			sb.append(text, lastIndex, index).append(replacement);
			lastIndex = index + search.length();
			index = text.toLowerCase().indexOf(search.toLowerCase(), lastIndex);
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
				ClassLoader classLoader = getClass().getClassLoader();
				java.net.URL url = classLoader.getResource(resourcePath);
				if (url == null) {
					throw new IllegalArgumentException("Cannot find resource: " + resourcePath);
				}
				return new String(java.nio.file.Files.readAllBytes(
					java.nio.file.Paths.get(url.toURI())
				));
			} else {
				return new String(java.nio.file.Files.readAllBytes(
					java.nio.file.Paths.get(configPath)
				));
			}
		} catch (Exception e) {
			log.error("Failed to load YAML file from path: {}", configPath, e);
			throw new RuntimeException("Failed to load YAML configuration", e);
		}
	}

	/**
	 * Internal model for a keyword pattern.
	 */
	private static class KeywordPattern {
		String pattern;
		Level level;
		String category;
		boolean crisis;

		KeywordPattern(String pattern, Level level, String category, boolean crisis) {
			this.pattern = pattern;
			this.level = level;
			this.category = category;
			this.crisis = crisis;
		}
	}

	/**
	 * Internal model for output filter rule.
	 */
	private static class OutputFilter {
		String term;
		String replacement;

		OutputFilter(String term, String replacement) {
			this.term = term;
			this.replacement = replacement;
		}
	}
}
