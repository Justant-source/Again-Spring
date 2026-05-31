package com.againspring.api.community;

import com.againspring.api.community.dto.*;
import com.againspring.domain.community.ThreeWayMessage;
import com.againspring.domain.community.ThreeWaySession;
import com.againspring.service.community.ThreeWayChatService;
import com.againspring.service.community.ThreeWaySessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API Controller for 3-way mediation sessions (V17 Phase 6).
 * Endpoints for session creation, joining, and messaging.
 */
@Slf4j
@RestController
@RequestMapping("/api/three-way")
@RequiredArgsConstructor
@Tag(name = "ThreeWay", description = "3-way mediation session endpoints")
public class ThreeWayController {

    private final ThreeWaySessionService sessionService;
    private final ThreeWayChatService chatService;

    @Value("${app.url:http://localhost:3000}")
    private String appUrl;

    /**
     * Create a new 3-way mediation session.
     * Party A initiates and receives an invite token for Party B.
     */
    @PostMapping
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Create 3-way session", description = "Party A initiates a new 3-way mediation session")
    @ApiResponse(responseCode = "201", description = "Session created",
        content = @Content(schema = @Schema(implementation = ThreeWaySessionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    public ResponseEntity<ThreeWaySessionResponse> createSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ThreeWayCreateRequest request) {

        String partyAUserId = userDetails.getUsername();
        ThreeWaySession session = sessionService.create(partyAUserId, request.getCategory());

        ThreeWaySessionResponse response = mapToResponse(session);
        log.info("Three-way session created: id={}, partyA={}", session.getId(), partyAUserId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Party B joins an existing 3-way session using invite token.
     */
    @PostMapping("/join/{token}")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Join 3-way session", description = "Party B joins using invite token")
    @ApiResponse(responseCode = "200", description = "Joined successfully",
        content = @Content(schema = @Schema(implementation = ThreeWaySessionResponse.class)))
    @ApiResponse(responseCode = "404", description = "Token not found or session already active")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    public ResponseEntity<ThreeWaySessionResponse> joinSession(
            @PathVariable String token,
            @AuthenticationPrincipal UserDetails userDetails) {

        String partyBUserId = userDetails.getUsername();
        ThreeWaySession session = sessionService.join(token, partyBUserId);

        ThreeWaySessionResponse response = mapToResponse(session);
        log.info("Party B joined three-way session: id={}, partyB={}", session.getId(), partyBUserId);

        return ResponseEntity.ok(response);
    }

    /**
     * Get 3-way session details.
     */
    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Get 3-way session", description = "Retrieve session details")
    @ApiResponse(responseCode = "200", description = "Session found",
        content = @Content(schema = @Schema(implementation = ThreeWaySessionResponse.class)))
    @ApiResponse(responseCode = "404", description = "Session not found")
    @ApiResponse(responseCode = "403", description = "Not a participant")
    public ResponseEntity<ThreeWaySessionResponse> getSession(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            String userId = userDetails.getUsername();
            ThreeWaySession session = sessionService.getSession(id, userId);
            return ResponseEntity.ok(mapToResponse(session));
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * Get conversation history for a 3-way session.
     */
    @GetMapping("/{id}/messages")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Get 3-way session messages", description = "Retrieve conversation history")
    @ApiResponse(responseCode = "200", description = "Messages retrieved",
        content = @Content(schema = @Schema(implementation = ThreeWayMessageResponse.class)))
    @ApiResponse(responseCode = "404", description = "Session not found")
    @ApiResponse(responseCode = "403", description = "Not a participant")
    public ResponseEntity<List<ThreeWayMessageResponse>> getMessages(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            String userId = userDetails.getUsername();
            List<ThreeWayMessage> messages = chatService.getHistory(id, userId);
            List<ThreeWayMessageResponse> responses = messages.stream()
                .map(this::mapMessageToResponse)
                .toList();
            return ResponseEntity.ok(responses);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * Send a message in a 3-way session.
     * Response is immediate; mediator response happens asynchronously.
     */
    @PostMapping("/{id}/messages")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Send 3-way message", description = "Send a message to the 3-way session")
    @ApiResponse(responseCode = "201", description = "Message sent",
        content = @Content(schema = @Schema(implementation = ThreeWayMessageResponse.class)))
    @ApiResponse(responseCode = "422", description = "Crisis detected or validation failed")
    @ApiResponse(responseCode = "404", description = "Session not found")
    @ApiResponse(responseCode = "403", description = "Not authorized")
    public ResponseEntity<?> sendMessage(
            @PathVariable String id,
            @Valid @RequestBody ThreeWayMessageRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            String userId = userDetails.getUsername();
            ThreeWayMessage message = chatService.sendUserMessage(id, request.getAuthorRole(), request.getContent(), userId);

            ThreeWayMessageResponse response = mapMessageToResponse(message);
            log.info("Three-way message sent: twsId={}, authorRole={}, msgId={}", id, request.getAuthorRole(), message.getId());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("CRISIS_DETECTED:")) {
                log.warn("Crisis detected in three-way message: sessionId={}", id);
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(new ErrorResponse("CRISIS_DETECTED", "Crisis keyword detected"));
            }
            throw e;
        }
    }

    /**
     * Get the invite URL for sharing with Party B.
     */
    @GetMapping("/{id}/invite-url")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Get invite URL", description = "Get shareable invite link for Party B")
    @ApiResponse(responseCode = "200", description = "URL retrieved",
        content = @Content(schema = @Schema(implementation = InviteUrlResponse.class)))
    @ApiResponse(responseCode = "404", description = "Session not found")
    @ApiResponse(responseCode = "403", description = "Not authorized")
    public ResponseEntity<InviteUrlResponse> getInviteUrl(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            String userId = userDetails.getUsername();
            ThreeWaySession session = sessionService.getSession(id, userId);

            String inviteUrl = appUrl + "/three-way/join/" + session.getInviteToken();
            return ResponseEntity.ok(new InviteUrlResponse(inviteUrl));

        } catch (org.springframework.security.access.AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    // ============= Private helpers =============

    private ThreeWaySessionResponse mapToResponse(ThreeWaySession session) {
        return ThreeWaySessionResponse.builder()
            .id(session.getId())
            .status(session.getStatus().name())
            .inviteToken(session.getInviteToken())
            .partyAUserId(session.getPartyAUserId())
            .partyBUserId(session.getPartyBUserId())
            .category(session.getCategory())
            .createdAt(session.getCreatedAt() != null ? session.getCreatedAt().toString() : null)
            .updatedAt(session.getUpdatedAt() != null ? session.getUpdatedAt().toString() : null)
            .build();
    }

    private ThreeWayMessageResponse mapMessageToResponse(ThreeWayMessage message) {
        return ThreeWayMessageResponse.builder()
            .id(message.getId())
            .twsId(message.getTwsId())
            .authorRole(message.getAuthorRole().name())
            .content(message.getContent())
            .createdAt(message.getCreatedAt() != null ? message.getCreatedAt().toString() : null)
            .llmModel(message.getLlmModel())
            .build();
    }

    /**
     * Simple error response DTO.
     */
    public record ErrorResponse(String code, String message) {}
}
