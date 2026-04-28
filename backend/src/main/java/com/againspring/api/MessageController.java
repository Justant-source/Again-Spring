package com.againspring.api;

import com.againspring.api.dto.request.SendMessageRequest;
import com.againspring.api.dto.response.*;
import com.againspring.domain.enums.MessageSender;
import com.againspring.service.ChatService;
import com.againspring.service.SessionRoleResolver;
import com.againspring.service.SessionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * MessageController (V1.5 카톡식 채팅)
 */
@RestController
@RequestMapping("/api/sessions/{sessionId}")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Chat messaging endpoints")
public class MessageController {

    private final ChatService chatService;
    private final SessionRoleResolver roleResolver;
    private final SessionService sessionService;

    @PostMapping("/messages")
    @SecurityRequirement(name = "bearer-jwt")
    public ResponseEntity<ChatTurnResponse> sendMessage(
        @PathVariable String sessionId,
        @RequestBody SendMessageRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        var sender = roleResolver.resolveSender(sessionId, userDetails.getUsername());
        var result = chatService.sendUserMessage(sessionId, sender, request.getContent());

        if (result.crisisLevel() != null && result.crisisLevel() == 1) {
            return ResponseEntity.status(409).body(ChatTurnResponse.crisis());
        }
        return ResponseEntity.ok(ChatTurnResponse.from(result));
    }

    @GetMapping("/messages")
    @SecurityRequirement(name = "bearer-jwt")
    public ResponseEntity<List<MessageResponse>> getMessages(
        @PathVariable String sessionId,
        @RequestParam(required = false) Long since,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        var sender = roleResolver.resolveSender(sessionId, userDetails.getUsername());
        Instant sinceInstant = since != null ? Instant.ofEpochMilli(since) : Instant.EPOCH;
        var messages = chatService.getMyMessages(sessionId, sender, sinceInstant);
        return ResponseEntity.ok(messages.stream().map(MessageResponse::from).toList());
    }

    @GetMapping("/partner-messages")
    @SecurityRequirement(name = "bearer-jwt")
    public ResponseEntity<List<MessageMetadataResponse>> getPartnerMessages(
        @PathVariable String sessionId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        var sender = roleResolver.resolveSender(sessionId, userDetails.getUsername());
        var meta = chatService.getPartnerMessagesMetadata(sessionId, sender);
        return ResponseEntity.ok(meta.stream().map(MessageMetadataResponse::from).toList());
    }

    @GetMapping("/partner-status")
    @SecurityRequirement(name = "bearer-jwt")
    public ResponseEntity<PartnerStatusResponse> getPartnerStatus(
        @PathVariable String sessionId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        var sender = roleResolver.resolveSender(sessionId, userDetails.getUsername());
        var status = chatService.getPartnerStatus(sessionId, sender);
        return ResponseEntity.ok(PartnerStatusResponse.from(status));
    }

    @PostMapping("/finalize")
    @SecurityRequirement(name = "bearer-jwt")
    public ResponseEntity<FinalizationResponse> requestFinalize(
        @PathVariable String sessionId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        var sender = roleResolver.resolveSender(sessionId, userDetails.getUsername());
        var result = chatService.requestFinalization(sessionId, sender);
        return ResponseEntity.ok(FinalizationResponse.from(result));
    }

    @PostMapping("/finalize/agree")
    @SecurityRequirement(name = "bearer-jwt")
    public ResponseEntity<FinalizationResponse> agreeToFinalize(
        @PathVariable String sessionId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        var sender = roleResolver.resolveSender(sessionId, userDetails.getUsername());
        var result = chatService.agreeToFinalize(sessionId, sender);
        return ResponseEntity.ok(FinalizationResponse.from(result));
    }

    @PostMapping("/finalize/decline")
    @SecurityRequirement(name = "bearer-jwt")
    public ResponseEntity<Void> declineFinalize(
        @PathVariable String sessionId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        var sender = roleResolver.resolveSender(sessionId, userDetails.getUsername());
        chatService.declineFinalize(sessionId, sender);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/invite")
    @SecurityRequirement(name = "bearer-jwt")
    public ResponseEntity<InviteTokenResponse> getInvite(
        @PathVariable String sessionId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        var response = sessionService.getInviteForExistingSession(sessionId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/invite")
    @SecurityRequirement(name = "bearer-jwt")
    public ResponseEntity<InviteTokenResponse> generateInvite(
        @PathVariable String sessionId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        var response = sessionService.generateInviteForExistingSession(sessionId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }
}
