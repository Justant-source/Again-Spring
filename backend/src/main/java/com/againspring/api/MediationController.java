package com.againspring.api;

import com.againspring.api.dto.request.ProgressTurnRequest;
import com.againspring.api.dto.response.CurrentTurnResponse;
import com.againspring.api.dto.response.TurnResponse;
import com.againspring.service.MediationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST API for multi-turn mediation orchestration.
 * Endpoints:
 * - POST /api/sessions/{sessionId}/turns — submit turn input
 * - GET /api/sessions/{sessionId}/turns/current — get current turn state
 * - GET /api/sessions/{sessionId}/stream — SSE event stream (for real-time updates)
 *
 * TODO Phase 3-5 integration: @AuthenticationPrincipal principal type depends on your JWT impl.
 * Currently assuming org.springframework.security.core.userdetails.User with username = email.
 */
@Slf4j
@RestController
@RequestMapping("/api/sessions/{sessionId}")
@RequiredArgsConstructor
public class MediationController {

    private final MediationService mediationService;

    // SSE emitter registry: sessionId -> emitter
    private final ConcurrentHashMap<String, SseEmitter> sseEmitters = new ConcurrentHashMap<>();

    /**
     * Submit user input for current turn.
     * POST /api/sessions/{sessionId}/turns
     *
     * Request body: { "userInput": "...", "skip": false }
     */
    @PostMapping("/turns")
    public ResponseEntity<TurnResponse> progressTurn(
            @PathVariable String sessionId,
            @RequestBody ProgressTurnRequest request,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        // TODO Phase 3-5 integration: Extract userId from JWT principal
        // Currently: use principal.getUsername() (which should be email from JWT)
        String currentUserId = extractUserIdFromPrincipal(principal);

        try {
            TurnResponse response = mediationService.progressTurn(sessionId, currentUserId, request.getUserInput());

            // Emit SSE event if emitter exists
            if (sseEmitters.containsKey(sessionId)) {
                sendSseEvent(sessionId, "turn_completed", response);
            }

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Failed to progress turn for session {}: {}", sessionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    /**
     * Get current turn state.
     * GET /api/sessions/{sessionId}/turns/current
     */
    @GetMapping("/turns/current")
    public ResponseEntity<CurrentTurnResponse> getCurrentTurn(
            @PathVariable String sessionId,
            @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        String currentUserId = extractUserIdFromPrincipal(principal);

        try {
            CurrentTurnResponse response = mediationService.getCurrentTurn(sessionId, currentUserId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Failed to get current turn for session {}: {}", sessionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Subscribe to server-sent events for real-time turn updates.
     * GET /api/sessions/{sessionId}/stream
     *
     * Events emitted:
     * - turn_started: when turn begins
     * - turn_completed: when turn completes with mediator response
     * - session_completed: when session finishes
     *
     * Connection timeout: 5 minutes. If streaming is not cleanly supported by LLM bridge,
     * only turn_completed events are emitted after LLM processing finishes.
     */
    @GetMapping("/stream")
    public SseEmitter streamTurnEvents(@PathVariable String sessionId,
                                       @AuthenticationPrincipal org.springframework.security.core.userdetails.User principal) {

        String currentUserId = extractUserIdFromPrincipal(principal);

        // Create emitter with 5-minute timeout
        SseEmitter emitter = new SseEmitter(300_000L);

        // Register emitter for this session
        sseEmitters.put(sessionId, emitter);

        // Cleanup on completion/error
        emitter.onCompletion(() -> sseEmitters.remove(sessionId));
        emitter.onTimeout(() -> sseEmitters.remove(sessionId));
        emitter.onError(throwable -> sseEmitters.remove(sessionId));

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("connection_established")
                    .data("Connected to session: " + sessionId)
                    .id(String.valueOf(System.currentTimeMillis()))
                    .build());
        } catch (IOException e) {
            log.warn("Failed to send initial SSE event for session {}: {}", sessionId, e.getMessage());
            sseEmitters.remove(sessionId);
        }

        return emitter;
    }

    /**
     * Send SSE event to connected client.
     */
    private void sendSseEvent(String sessionId, String eventName, Object data) {
        SseEmitter emitter = sseEmitters.get(sessionId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data)
                        .id(String.valueOf(System.currentTimeMillis()))
                        .build());
            } catch (IOException e) {
                log.warn("Failed to send SSE event {} for session {}: {}", eventName, sessionId, e.getMessage());
                sseEmitters.remove(sessionId);
            }
        }
    }

    /**
     * Extract user ID from JWT principal.
     * TODO Phase 3-5 integration: Adjust based on your actual JWT principal structure.
     * Currently assumes: principal.getUsername() contains email, look up User by email in DB.
     */
    private String extractUserIdFromPrincipal(org.springframework.security.core.userdetails.User principal) {
        if (principal == null) {
            throw new RuntimeException("User not authenticated");
        }
        // For now, return username as ID (should be email)
        // TODO: Implement proper user lookup from JWT token claims
        return principal.getUsername();
    }
}
