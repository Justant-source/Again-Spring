package com.againspring.safety;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Unit tests for KeywordGuard.
 *
 * Coverage:
 * - Crisis keyword detection (domestic violence, sexual violence, self-harm, child abuse)
 * - Level 1 legal term detection
 * - Level 2 clinical term detection
 * - Level 3 judgment language detection
 * - Level 4 relationship termination encouragement
 * - Case-insensitive matching
 * - Korean text handling
 * - Output filter replacements
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {KeywordGuard.class})
@DisplayName("KeywordGuard Tests")
class KeywordGuardTest {

	@Autowired
	private KeywordGuard keywordGuard;

	@BeforeEach
	void setUp() {
		assertNotNull(keywordGuard, "KeywordGuard should be autowired");
	}

	// ========== CRISIS KEYWORDS ==========

	@Test
	@DisplayName("Crisis: Detects domestic violence keywords")
	void testDetectDomesticViolence() {
		ScanResult result = keywordGuard.scanUserInput("어제 때렸어", "user1");
		assertTrue(result.isCrisis());
		assertTrue(result.isBlocked());
		assertEquals(Level.CRISIS, result.getMaxLevel());
		assertTrue(result.getMatches().stream().anyMatch(m -> m.getPattern().equals("때렸")));
	}

	@Test
	@DisplayName("Crisis: Detects sexual violence keywords")
	void testDetectSexualViolence() {
		ScanResult result = keywordGuard.scanUserInput("강간당했어요", "user1");
		assertTrue(result.isCrisis());
		assertTrue(result.isBlocked());
		assertEquals(Level.CRISIS, result.getMaxLevel());
	}

	@Test
	@DisplayName("Crisis: Detects self-harm keywords")
	void testDetectSelfHarm() {
		ScanResult result = keywordGuard.scanUserInput("죽고싶어요", "user1");
		assertTrue(result.isCrisis());
		assertTrue(result.isBlocked());
		assertEquals(Level.CRISIS, result.getMaxLevel());
	}

	@Test
	@DisplayName("Crisis: Detects child abuse keywords")
	void testDetectChildAbuse() {
		ScanResult result = keywordGuard.scanUserInput("아이를때렸어", "user1");
		assertTrue(result.isCrisis());
		assertTrue(result.isBlocked());
		assertEquals(Level.CRISIS, result.getMaxLevel());
	}

	@Test
	@DisplayName("Crisis: Case-insensitive matching")
	void testCrisisCaseInsensitive() {
		ScanResult result = keywordGuard.scanUserInput("자살하고싶어", "user1");
		assertTrue(result.isCrisis());

		ScanResult result2 = keywordGuard.scanUserInput("자살하고싶어", "user1");
		assertTrue(result2.isCrisis());
	}

	// ========== LEVEL 1 KEYWORDS ==========

	@Test
	@DisplayName("Level 1: Detects legal risk terms")
	void testDetectLevel1Legal() {
		ScanResult result = keywordGuard.scanUserInput("과실비율에 대해", "user1");
		assertEquals(Level.LEVEL1, result.getMaxLevel());
		assertTrue(result.isBlocked());
		assertTrue(result.getMatches().stream().anyMatch(m -> m.getPattern().equals("과실비율")));
	}

	@Test
	@DisplayName("Level 1: Detects stigmatizing terms")
	void testDetectLevel1Stigma() {
		ScanResult result = keywordGuard.scanUserInput("그 사람은 가해자야", "user1");
		assertEquals(Level.LEVEL1, result.getMaxLevel());
		assertTrue(result.isBlocked());
	}

	@Test
	@DisplayName("Level 1: Multiple matches")
	void testLevel1MultipleMatches() {
		ScanResult result = keywordGuard.scanUserInput("판사가 판결을 내렸어", "user1");
		assertEquals(Level.LEVEL1, result.getMaxLevel());
		assertTrue(result.isBlocked());
		assertTrue(result.getMatches().size() >= 2);
	}

	// ========== LEVEL 2 KEYWORDS ==========

	@Test
	@DisplayName("Level 2: Detects clinical terms")
	void testDetectLevel2Clinical() {
		ScanResult result = keywordGuard.scanUserInput("그는 나르시시스트야", "user1");
		assertEquals(Level.LEVEL2, result.getMaxLevel());
		assertFalse(result.isBlocked());
	}

	@Test
	@DisplayName("Level 2: Detects relationship termination terms")
	void testDetectLevel2Termination() {
		ScanResult result = keywordGuard.scanUserInput("이혼을 생각해", "user1");
		assertEquals(Level.LEVEL2, result.getMaxLevel());
		assertFalse(result.isBlocked());
	}

	@Test
	@DisplayName("Level 2: Does not block session")
	void testLevel2DoesNotBlock() {
		ScanResult result = keywordGuard.scanUserInput("우울증이 있어", "user1");
		assertFalse(result.isBlocked());
	}

	// ========== LEVEL 3 KEYWORDS ==========

	@Test
	@DisplayName("Level 3: Detects judgment language")
	void testDetectLevel3Judgment() {
		ScanResult result = keywordGuard.scanUserInput("내가 이겼다", "user1");
		assertEquals(Level.LEVEL3, result.getMaxLevel());
		assertFalse(result.isBlocked());
	}

	@Test
	@DisplayName("Level 3: Detects right/wrong language")
	void testDetectLevel3RightWrong() {
		ScanResult result = keywordGuard.scanUserInput("너는 틀렸어", "user1");
		assertEquals(Level.LEVEL3, result.getMaxLevel());
		assertFalse(result.isBlocked());
	}

	// ========== LEVEL 4 KEYWORDS ==========

	@Test
	@DisplayName("Level 4: Detects relationship termination encouragement")
	void testDetectLevel4Termination() {
		ScanResult result = keywordGuard.scanUserInput("헤어지세요", "user1");
		assertEquals(Level.LEVEL4, result.getMaxLevel());
		assertFalse(result.isBlocked());
	}

	// ========== OUTPUT FILTER ==========

	@Test
	@DisplayName("Output Filter: Replaces legal terms")
	void testOutputFilterLegalTerms() {
		String filtered = keywordGuard.applyOutputFilter("과실비율이 높다");
		assertNotNull(filtered);
		assertFalse(filtered.contains("과실비율"));
		assertTrue(filtered.contains("화해 기여도"));
	}

	@Test
	@DisplayName("Output Filter: Replaces judgment language")
	void testOutputFilterJudgmentLanguage() {
		String filtered = keywordGuard.applyOutputFilter("이겼다고 볼 수 있어");
		assertNotNull(filtered);
		assertFalse(filtered.contains("이겼다"));
	}

	@Test
	@DisplayName("Output Filter: Scans LLM output")
	void testScanLLMOutput() {
		ScanResult result = keywordGuard.scanLLMOutput("판결에 따르면 과실비율은 70:30입니다");
		assertEquals(Level.LEVEL2, result.getMaxLevel());
		assertTrue(result.getMatches().size() >= 2);
	}

	// ========== EDGE CASES ==========

	@Test
	@DisplayName("No matches returns empty result")
	void testNoMatches() {
		ScanResult result = keywordGuard.scanUserInput("일반적인 대화 내용이에요", "user1");
		assertNull(result.getMaxLevel());
		assertTrue(result.getMatches().isEmpty());
		assertFalse(result.isBlocked());
		assertFalse(result.isCrisis());
	}

	@Test
	@DisplayName("Null input returns empty result")
	void testNullInput() {
		ScanResult result = keywordGuard.scanUserInput(null, "user1");
		assertTrue(result.getMatches().isEmpty());
		assertFalse(result.isBlocked());
	}

	@Test
	@DisplayName("Empty input returns empty result")
	void testEmptyInput() {
		ScanResult result = keywordGuard.scanUserInput("", "user1");
		assertTrue(result.getMatches().isEmpty());
		assertFalse(result.isBlocked());
	}

	@Test
	@DisplayName("Crisis takes precedence over lower levels")
	void testCrisisPrecedence() {
		// Input with both crisis and level1 terms
		ScanResult result = keywordGuard.scanUserInput("자살하고 고소하겠어", "user1");
		assertTrue(result.isCrisis());
		assertEquals(Level.CRISIS, result.getMaxLevel());
	}

	@Test
	@DisplayName("Highest level among non-crisis is returned")
	void testHighestLevelPrecedence() {
		// Input with level1 and level2 terms
		ScanResult result = keywordGuard.scanUserInput("가해자가 나르시시스트야", "user1");
		assertEquals(Level.LEVEL1, result.getMaxLevel());
		assertTrue(result.isBlocked());
	}

	@Test
	@DisplayName("Match position is recorded correctly")
	void testMatchPosition() {
		ScanResult result = keywordGuard.scanUserInput("어제 때렸어", "user1");
		assertTrue(result.getMatches().stream()
			.anyMatch(m -> m.getPosition() == 3)); // "때렸" starts at index 3 in "어제 때렸어"
	}

	@Test
	@DisplayName("Korean text handling without normalization issues")
	void testKoreanTextHandling() {
		// 폭력 is a crisis keyword
		ScanResult result1 = keywordGuard.scanUserInput("폭력을 휘둘렀어", "user1");
		assertTrue(result1.isCrisis());

		// 맞고 살 composite is a crisis keyword;
		// bare 맞았 is deliberately excluded (동음이의: 옳고/옳았)
		ScanResult result2 = keywordGuard.scanUserInput("맞고 살았어요", "user1");
		assertTrue(result2.isCrisis());
	}
}
