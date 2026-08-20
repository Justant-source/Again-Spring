package com.againspring.aiuser.orchestrator.service.llm;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit breaker for LLM generation failures.
 * Transitions to OPEN state when same failure reason occurs N times (default=3) consecutively.
 * Auto-transitions to half-open (ready to retry) after 30 minutes.
 * Resets on success.
 *
 * <p>Used to prevent repeated retries of the same failing prompt/condition,
 * allowing fast-fail instead of exhausting retry budgets.</p>
 */
@Slf4j
@Service
public class LlmCircuitBreaker {

    public enum State {
        CLOSED,      // Normal operation
        OPEN,        // Stop retries, fail fast
        HALF_OPEN    // Ready to retry after cooldown
    }

    @Data
    public static class Telemetry {
        private State state;
        private String reason;
        private int consecutiveFailures;
        private List<String> recentPromptHashes;
        private Instant openedAt;
        private Instant autoResetAt;
        private int totalOpens;
    }

    @Data
    private static class CircuitState {
        private State state = State.CLOSED;
        private String lastReason = null;
        private int consecutiveFailures = 0;
        private List<String> recentPromptHashes = new ArrayList<>();
        private Instant openedAt = null;
        private Instant autoResetAt = null;
        private int totalOpens = 0;
        private static final int MAX_HASH_HISTORY = 5;
        private static final int PROMPT_HASH_SIZE_LIMIT = 16;

        synchronized void recordFailure(String reason, String promptHash) {
            if (!Objects.equals(lastReason, reason)) {
                // Different reason — reset counter
                consecutiveFailures = 1;
                lastReason = reason;
                recentPromptHashes.clear();
            } else {
                // Same reason — increment counter
                consecutiveFailures++;
            }

            if (promptHash != null && promptHash.length() <= PROMPT_HASH_SIZE_LIMIT) {
                if (recentPromptHashes.size() >= MAX_HASH_HISTORY) {
                    recentPromptHashes.remove(0);
                }
                recentPromptHashes.add(promptHash);
            }
        }

        synchronized void recordSuccess() {
            if (state != State.OPEN) {
                consecutiveFailures = 0;
                lastReason = null;
                recentPromptHashes.clear();
            }
            // If OPEN, stay OPEN until auto-reset timeout
        }

        synchronized void transitionToOpen(Clock clock) {
            if (state != State.OPEN) {
                state = State.OPEN;
                openedAt = Instant.now(clock);
                autoResetAt = openedAt.plusSeconds(30 * 60);  // 30 minutes
                totalOpens++;
            }
        }

        synchronized void transitionToHalfOpen(Clock clock) {
            if (state == State.OPEN && autoResetAt != null && Instant.now(clock).isAfter(autoResetAt)) {
                state = State.HALF_OPEN;
                consecutiveFailures = 0;
            }
        }

        synchronized void reset() {
            state = State.CLOSED;
            consecutiveFailures = 0;
            lastReason = null;
            recentPromptHashes.clear();
            openedAt = null;
            autoResetAt = null;
        }
    }

    private final int strikeThreshold;
    private final AtomicReference<CircuitState> stateRef = new AtomicReference<>(new CircuitState());
    private final Clock clock;
    private volatile boolean openAlertSent = false;

    public LlmCircuitBreaker() {
        this(3, Clock.systemUTC());
    }

    public LlmCircuitBreaker(int strikeThreshold, Clock clock) {
        this.strikeThreshold = strikeThreshold;
        this.clock = clock;
    }

    /**
     * Record a generation failure.
     * If same reason occurs strikeThreshold times consecutively, transitions to OPEN.
     */
    public void recordFailure(String failureReason, String promptHash) {
        CircuitState state = stateRef.get();
        state.recordFailure(failureReason, promptHash);

        if (state.consecutiveFailures >= strikeThreshold && state.state == State.CLOSED) {
            state.transitionToOpen(clock);
            if (!openAlertSent) {
                openAlertSent = true;
                log.error("[CIRCUIT] OPEN reason={} consecutiveFailures={} promptHashes={}",
                    state.lastReason, state.consecutiveFailures, state.recentPromptHashes);
            }
        }
    }

    /**
     * Record successful generation.
     * Resets failure counter if circuit is not OPEN.
     */
    public void recordSuccess() {
        CircuitState state = stateRef.get();
        state.recordSuccess();
        if (openAlertSent && state.state != State.OPEN) {
            openAlertSent = false;
        }
    }

    /**
     * Check if circuit is currently OPEN (should skip generation).
     */
    public boolean isOpen() {
        CircuitState state = stateRef.get();
        state.transitionToHalfOpen(clock);
        return state.state == State.OPEN;
    }

    public Telemetry getTelemetry() {
        CircuitState state = stateRef.get();
        synchronized (state) {
            Telemetry t = new Telemetry();
            t.setState(state.state);
            t.setReason(state.lastReason);
            t.setConsecutiveFailures(state.consecutiveFailures);
            t.setRecentPromptHashes(new ArrayList<>(state.recentPromptHashes));
            t.setOpenedAt(state.openedAt);
            t.setAutoResetAt(state.autoResetAt);
            t.setTotalOpens(state.totalOpens);
            return t;
        }
    }

    /** Reset circuit to CLOSED state (for testing). */
    public void reset() {
        stateRef.get().reset();
        openAlertSent = false;
    }
}
