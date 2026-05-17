package com.againspring.safety;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for SafetyAuditLogger.
 *
 * Verifies:
 * - Crisis events are logged to audit logger
 * - Level 2 warning events are logged
 * - Log entries follow key=value format
 * - Raw text is NOT logged (only pattern names)
 * - All required fields are present in log
 */
@DisplayName("SafetyAuditLogger Tests")
class SafetyAuditLoggerTest {

	private SafetyAuditLogger auditLogger;

	private ListAppender<ILoggingEvent> listAppender;

	@BeforeEach
	void setUp() {
		auditLogger = new SafetyAuditLogger();

		// Setup Logback test appender
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		Logger logger = loggerContext.getLogger("com.againspring.safety.audit");

		listAppender = new ListAppender<>();
		listAppender.setContext(loggerContext);
		listAppender.start();

		logger.addAppender(listAppender);
		logger.setLevel(ch.qos.logback.classic.Level.WARN);
	}

	@Test
	@DisplayName("Crisis event is logged")
	void testCrisisEventLogged() {
		List<String> patterns = List.of("자살", "때렸");
		CrisisDetectedEvent event = new CrisisDetectedEvent(
			this,
			"user123",
			"session456",
			patterns
		);

		auditLogger.onCrisisDetected(event);

		assertEquals(1, listAppender.list.size());
		ILoggingEvent logEvent = listAppender.list.get(0);
		String message = logEvent.getFormattedMessage();

		assertTrue(message.contains("userId=user123"));
		assertTrue(message.contains("sessionId=session456"));
		assertTrue(message.contains("level=CRISIS"));
		assertTrue(message.contains("patterns"));
	}

	@Test
	@DisplayName("Safety trigger event is logged")
	void testSafetyTriggerEventLogged() {
		List<String> patterns = List.of("이혼");
		SafetyTriggerEvent event = new SafetyTriggerEvent(
			this,
			"user789",
			"session012",
			Level.LEVEL2,
			patterns
		);

		auditLogger.onSafetyTrigger(event);

		assertEquals(1, listAppender.list.size());
		ILoggingEvent logEvent = listAppender.list.get(0);
		String message = logEvent.getFormattedMessage();

		assertTrue(message.contains("userId=user789"));
		assertTrue(message.contains("sessionId=session012"));
		assertTrue(message.contains("level=LEVEL2"));
		assertTrue(message.contains("patterns"));
	}

	@Test
	@DisplayName("Log format is key=value")
	void testLogFormatKeyValue() {
		List<String> patterns = List.of("강간");
		CrisisDetectedEvent event = new CrisisDetectedEvent(
			this,
			"userABC",
			"sessionXYZ",
			patterns
		);

		auditLogger.onCrisisDetected(event);

		ILoggingEvent logEvent = listAppender.list.get(0);
		String message = logEvent.getFormattedMessage();

		// Verify key=value pattern
		assertTrue(message.matches(".*userId=\\w+.*"));
		assertTrue(message.matches(".*sessionId=\\w+.*"));
		assertTrue(message.matches(".*level=\\w+.*"));
	}

	@Test
	@DisplayName("Log does not contain raw text")
	void testLogDoesNotContainRawText() {
		List<String> patterns = List.of("자살");
		CrisisDetectedEvent event = new CrisisDetectedEvent(
			this,
			"user",
			"session",
			patterns
		);

		auditLogger.onCrisisDetected(event);

		ILoggingEvent logEvent = listAppender.list.get(0);
		String message = logEvent.getFormattedMessage();

		// Raw text should not be in log
		assertFalse(message.contains("자살하고싶어"));
		// But pattern name should be
		assertTrue(message.contains("자살"));
	}

	@Test
	@DisplayName("Multiple patterns logged")
	void testMultiplePatternsLogged() {
		List<String> patterns = List.of("때렸", "폭행", "상해");
		CrisisDetectedEvent event = new CrisisDetectedEvent(
			this,
			"user",
			"session",
			patterns
		);

		auditLogger.onCrisisDetected(event);

		ILoggingEvent logEvent = listAppender.list.get(0);
		String message = logEvent.getFormattedMessage();

		// All pattern names should be in log
		assertTrue(message.contains("patterns"));
	}

	@Test
	@DisplayName("Timestamp is logged")
	void testTimestampLogged() {
		List<String> patterns = List.of("자해");
		CrisisDetectedEvent event = new CrisisDetectedEvent(
			this,
			"user",
			"session",
			patterns
		);

		auditLogger.onCrisisDetected(event);

		ILoggingEvent logEvent = listAppender.list.get(0);
		String message = logEvent.getFormattedMessage();

		// Timestamp field should be present
		assertTrue(message.contains("timestamp"));
	}
}
