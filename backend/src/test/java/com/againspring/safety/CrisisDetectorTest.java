package com.againspring.safety;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit tests for CrisisDetector.
 *
 * Coverage:
 * - Crisis detection returns proper response with hotlines
 * - Non-crisis input returns null
 * - Application event is published on crisis detection
 * - Event contains correct metadata (userId, sessionId, patterns)
 * - Response has required crisis resource fields
 */
@SpringBootTest
@DisplayName("CrisisDetector Tests")
class CrisisDetectorTest {

	@Autowired
	private CrisisDetector crisisDetector;

	@Autowired
	private KeywordGuard keywordGuard;

	@MockBean
	private ApplicationEventPublisher eventPublisher;

	@BeforeEach
	void setUp() {
		assertNotNull(crisisDetector, "CrisisDetector should be autowired");
		reset(eventPublisher);
	}

	@Test
	@DisplayName("Crisis detection returns CrisisResponse")
	void testCrisisDetectionReturnResponse() {
		CrisisResponse response = crisisDetector.detect(
			"자살하고싶어",
			"session123",
			"user123"
		);

		assertNotNull(response);
		assertEquals("중요한 안내", response.getTitle());
		assertTrue(response.getMessage().contains("전문 기관"));
	}

	@Test
	@DisplayName("Crisis response includes Korean hotlines")
	void testCrisisResponseHotlines() {
		CrisisResponse response = crisisDetector.detect(
			"폭력을 당했어",
			"session123",
			"user123"
		);

		assertNotNull(response);
		assertNotNull(response.getHotlines());
		assertTrue(response.getHotlines().size() > 0);

		// Check for key hotlines
		assertTrue(response.getHotlines().stream()
			.anyMatch(h -> h.getNumber().equals("1366"))); // 여성긴급전화
		assertTrue(response.getHotlines().stream()
			.anyMatch(h -> h.getNumber().equals("1393"))); // 자살예방
	}

	@Test
	@DisplayName("Crisis response has recommended action")
	void testCrisisResponseAction() {
		CrisisResponse response = crisisDetector.detect(
			"강간당했어요",
			"session123",
			"user123"
		);

		assertNotNull(response);
		assertEquals("FORCE_END", response.getRecommendedAction());
		assertEquals("TERMINATE", response.getSessionAction());
	}

	@Test
	@DisplayName("Non-crisis returns null")
	void testNonCrisisReturnsNull() {
		CrisisResponse response = crisisDetector.detect(
			"일반적인 대화 내용",
			"session123",
			"user123"
		);

		assertNull(response);
	}

	@Test
	@DisplayName("Level 2 warning does not trigger crisis response")
	void testLevel2DoesNotTriggerCrisis() {
		CrisisResponse response = crisisDetector.detect(
			"나르시시스트라고 생각해",
			"session123",
			"user123"
		);

		assertNull(response);
		verify(eventPublisher, never()).publishEvent(any(CrisisDetectedEvent.class));
	}

	@Test
	@DisplayName("Crisis publishes application event")
	void testCrisisPublishesEvent() {
		crisisDetector.detect(
			"자살해야겠어",
			"session456",
			"user456"
		);

		verify(eventPublisher, times(1)).publishEvent(any(CrisisDetectedEvent.class));
	}

	@Test
	@DisplayName("Event contains correct metadata")
	void testEventMetadata() {
		crisisDetector.detect(
			"때렸어요",
			"session789",
			"user789"
		);

		verify(eventPublisher).publishEvent(argThat(event ->
			event instanceof CrisisDetectedEvent &&
				((CrisisDetectedEvent) event).getUserId().equals("user789") &&
				((CrisisDetectedEvent) event).getSessionId().equals("session789") &&
				!((CrisisDetectedEvent) event).getMatchedPatterns().isEmpty()
		));
	}

	@Test
	@DisplayName("Event does not include raw text, only pattern names")
	void testEventDoesNotIncludeRawText() {
		crisisDetector.detect(
			"때렸어요",
			"session789",
			"user789"
		);

		verify(eventPublisher).publishEvent(argThat(event ->
			event instanceof CrisisDetectedEvent &&
				!((CrisisDetectedEvent) event).getMatchedPatterns().isEmpty() &&
				((CrisisDetectedEvent) event).getMatchedPatterns().stream()
					.noneMatch(p -> p.contains("때렸어요"))
		));
	}

	@Test
	@DisplayName("Multiple crisis keywords in one input")
	void testMultipleCrisisKeywords() {
		CrisisResponse response = crisisDetector.detect(
			"때리고 자살하고 싶어",
			"session999",
			"user999"
		);

		assertNotNull(response);

		verify(eventPublisher).publishEvent(argThat(event ->
			event instanceof CrisisDetectedEvent &&
				((CrisisDetectedEvent) event).getMatchedPatterns().size() >= 1
		));
	}
}
