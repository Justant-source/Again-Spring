package com.againspring.aiuser.llm.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Rolling-window rate limiter for PARSE_FAIL events in structured generation (worker side).
 *
 * <p>Tracks failures in a sliding time window, alerts when count crosses threshold,
 * then suppresses alerts for a cooldown period. Thread-safe for concurrent LLM invocations.
 *
 * <p>Note: This is a duplicate of the orchestrator-side rate limiter, following the LlmStatsLogger
 * precedent of not crossing module boundaries. Usage is identical to the orchestrator version.
 *
 * <p>Usage:
 * <pre>
 * if (rateLimiter.recordFailure(correlationId, errorMsg, rawResponse)) {
 *     // Alert should be sent
 *     notifier.parseFailureRateAlert(...);
 * }
 * </pre>
 */
@Slf4j
@Service
public class ParseFailureRateLimiter {

    private static class FailureRecord {
        final Instant timestamp;
        final String errorMsg;
        final String rawResponse;

        FailureRecord(String errorMsg, String rawResponse) {
            this.timestamp = Instant.now();
            this.errorMsg = errorMsg;
            this.rawResponse = rawResponse;
        }
    }

    private final Map<String, FailureRecord> failuresByCorrelationId = new ConcurrentHashMap<>();
    private Instant lastAlertTime = null;
    private final ReentrantReadWriteLock alertLock = new ReentrantReadWriteLock();

    /**
     * Record a PARSE_FAIL event. Returns true if alert should be sent (threshold crossed and not in cooldown).
     *
     * @param correlationId Unique ID for this generation attempt
     * @param errorMsg Error message from LLM parse failure
     * @param rawResponse Raw LLM response that failed to parse
     * @return true if an alert should be sent (threshold crossed), false otherwise
     */
    public boolean recordFailure(String correlationId, String errorMsg, String rawResponse) {
        if (correlationId == null) {
            correlationId = "unknown";
        }
        failuresByCorrelationId.put(correlationId, new FailureRecord(errorMsg, rawResponse));
        // Cleanup old entries outside the window happens in countRecentFailures()
        return false;  // Parent logic (StructuredGenerationService) will call shouldAlertAndMarkSuppressed()
    }

    /**
     * Check if an alert should be sent based on failure count and cooldown state.
     * If true, marks the alert as sent and starts the cooldown period.
     *
     * <p>This separation from {@link #recordFailure(String, String, String)} allows
     * callers to aggregate multiple failures before deciding to alert.
     *
     * @param threshold Number of failures required to trigger alert
     * @param windowMinutes Time window in minutes to look back
     * @param cooldownMinutes Cooldown duration in minutes after alert sent
     * @return true if alert should be sent, false if threshold not met or in cooldown
     */
    public boolean shouldAlertAndMarkSuppressed(int threshold, int windowMinutes, int cooldownMinutes) {
        int recentCount = countRecentFailures(windowMinutes);

        if (recentCount < threshold) {
            return false;  // Threshold not reached
        }

        alertLock.writeLock().lock();
        try {
            Instant now = Instant.now();
            if (lastAlertTime != null) {
                long elapsedSeconds = now.getEpochSecond() - lastAlertTime.getEpochSecond();
                long cooldownSeconds = (long) cooldownMinutes * 60;
                if (elapsedSeconds < cooldownSeconds) {
                    log.debug("[parse-fail-rate] Alert suppressed by cooldown: {} of {} seconds elapsed",
                        elapsedSeconds, cooldownSeconds);
                    return false;  // Still in cooldown
                }
            }
            lastAlertTime = now;
            log.info("[parse-fail-rate] Alert triggered: {} failures in {} min (threshold: {})",
                recentCount, windowMinutes, threshold);
            return true;
        } finally {
            alertLock.writeLock().unlock();
        }
    }

    /**
     * Count failures recorded within the last {@code windowMinutes}.
     * Also cleans up entries older than the window.
     *
     * @param windowMinutes Time window in minutes
     * @return Number of failures in the window
     */
    public int countRecentFailures(int windowMinutes) {
        Instant cutoff = Instant.now().minusSeconds((long) windowMinutes * 60);
        int count = 0;

        for (var entry : failuresByCorrelationId.entrySet()) {
            if (entry.getValue().timestamp.isAfter(cutoff)) {
                count++;
            } else {
                // Lazy cleanup: remove old entries
                failuresByCorrelationId.remove(entry.getKey());
            }
        }

        return count;
    }

    /**
     * Get the most recent failure snippet for alerting (e.g. truncated rawResponse).
     * Useful for including in alert messages.
     *
     * @param windowMinutes Time window in minutes
     * @return Snippet from most recent failure, or empty string if none
     */
    public String getRecentFailureSnippet(int windowMinutes) {
        Instant cutoff = Instant.now().minusSeconds((long) windowMinutes * 60);
        String mostRecent = "";
        Instant mostRecentTime = Instant.MIN;

        for (var entry : failuresByCorrelationId.entrySet()) {
            FailureRecord record = entry.getValue();
            if (record.timestamp.isAfter(cutoff) && record.timestamp.isAfter(mostRecentTime)) {
                mostRecent = record.rawResponse != null ? record.rawResponse : record.errorMsg;
                mostRecentTime = record.timestamp;
            }
        }

        return mostRecent;
    }

    /**
     * Reset all tracked failures (useful for testing or admin operations).
     */
    public void reset() {
        failuresByCorrelationId.clear();
        alertLock.writeLock().lock();
        try {
            lastAlertTime = null;
        } finally {
            alertLock.writeLock().unlock();
        }
    }
}
