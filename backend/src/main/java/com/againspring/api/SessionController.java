package com.againspring.api;

import com.againspring.api.dto.request.CreateSessionRequest;
import com.againspring.api.dto.request.JoinSessionRequest;
import com.againspring.api.dto.response.CreateSessionResponse;
import com.againspring.api.dto.response.SessionListItemResponse;
import com.againspring.api.dto.response.SessionResponse;
import com.againspring.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session API controller.
 * Handles session CRUD and join operations.
 */
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
@Tag(name = "Session", description = "Session management endpoints")
public class SessionController {

    private final SessionService sessionService;

    /**
     * Create a new session (A initiates).
     *
     * @param userDetails authenticated user
     * @param request create session request
     * @return create session response with invite token
     */
    @PostMapping
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Create session", description = "A initiates a new mediation session")
    @ApiResponse(responseCode = "201", description = "Session created",
            content = @Content(schema = @Schema(implementation = CreateSessionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "422", description = "Crisis detected in description")
    public ResponseEntity<CreateSessionResponse> createSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateSessionRequest request) {
        CreateSessionResponse response = sessionService.createSession(userDetails.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get user's sessions.
     *
     * @param userDetails authenticated user
     * @return list of user's sessions
     */
    @GetMapping("/me")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Get user's sessions", description = "List all sessions for current user")
    @ApiResponse(responseCode = "200", description = "Sessions retrieved",
            content = @Content(schema = @Schema(implementation = SessionListItemResponse.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    public ResponseEntity<List<SessionListItemResponse>> getUserSessions(
            @AuthenticationPrincipal UserDetails userDetails) {
        List<SessionListItemResponse> sessions = sessionService.getUserSessions(userDetails.getUsername());
        return ResponseEntity.ok(sessions);
    }

    /**
     * Get session details.
     *
     * @param sessionId the session ID
     * @param userDetails authenticated user
     * @return session response
     */
    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Get session details", description = "Retrieve full session information")
    @ApiResponse(responseCode = "200", description = "Session retrieved",
            content = @Content(schema = @Schema(implementation = SessionResponse.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Forbidden")
    @ApiResponse(responseCode = "404", description = "Session not found")
    public ResponseEntity<SessionResponse> getSession(
            @PathVariable("id") String sessionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        SessionResponse response = sessionService.getSession(sessionId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    /**
     * Join a session via invite token (B joins).
     * Public endpoint — no authentication required, but may be authenticated.
     *
     * @param token the invite token
     * @param request join request
     * @param userDetails authenticated user (optional)
     * @return session response
     */
    @PostMapping("/join/{token}")
    @Operation(summary = "Join session", description = "B joins session using invite token")
    @ApiResponse(responseCode = "200", description = "Session joined",
            content = @Content(schema = @Schema(implementation = SessionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "410", description = "Invite token expired or invalid")
    @ApiResponse(responseCode = "409", description = "Session already has a participant")
    public ResponseEntity<SessionResponse> joinSession(
            @PathVariable("token") String token,
            @RequestBody JoinSessionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Optional<String> userId = Optional.ofNullable(userDetails)
                .map(UserDetails::getUsername);

        SessionResponse response = sessionService.joinSession(token, request, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Delete session (soft-cancel or hard-delete).
     *
     * @param sessionId the session ID
     * @param userDetails authenticated user
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Delete session", description = "Cancel or delete a session")
    @ApiResponse(responseCode = "204", description = "Session deleted")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Forbidden")
    @ApiResponse(responseCode = "404", description = "Session not found")
    public ResponseEntity<Void> deleteSession(
            @PathVariable("id") String sessionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        sessionService.deleteSession(sessionId, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
