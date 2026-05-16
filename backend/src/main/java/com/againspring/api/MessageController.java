package com.againspring.api;

import com.againspring.api.dto.request.SendMessageRequest;
import com.againspring.api.dto.response.*;
import jakarta.validation.Valid;
import com.againspring.domain.enums.MessageSender;
import com.againspring.service.CancelableChatService;
import com.againspring.service.ChatService;
import com.againspring.service.SessionRoleResolver;
import com.againspring.service.SessionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.againspring.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
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

    private final CancelableChatService cancelableChatService;
    private final ChatService chatService;
    private final SessionRoleResolver roleResolver;
    private final SessionService sessionService;
    private final com.againspring.repository.MessageRepository messageRepository;

    @Value("${app.features.duo-mode:false}")
    private boolean duoModeEnabled;

    private static boolean hasTesterRole(UserDetails ud) {
        if (ud == null) return false;
        return ud.getAuthorities().stream()
                .anyMatch(a -> "ROLE_TESTER".equals(a.getAuthority()));
    }

    @PostMapping("/messages")
    @SecurityRequirement(name = "bearer-jwt")
    public ResponseEntity<ChatTurnResponse> sendMessage(
        @PathVariable String sessionId,
        @Valid @RequestBody SendMessageRequest request,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        var sender = roleResolver.resolveSender(sessionId, userDetails.getUsername());

        // 1) 사용자 메시지 즉시 저장 + 진행 중 invocation 취소 (<100ms)
        var result = cancelableChatService.acceptUserMessage(sessionId, sender, request.getContent());

        if (result.crisisLevel() != null && result.crisisLevel() == 1) {
            return ResponseEntity.status(409).body(ChatTurnResponse.crisis());
        }

        // 2) 새 LLM invocation 비동기 시작 (트랜잭션 커밋 후)
        cancelableChatService.beginInvocation(sessionId, sender);

        // mediatorMessages는 null → @JsonInclude(NON_NULL)로 JSON 제외, FE 폴링으로 수신
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

    /**
     * 새로고침 후 TypingBubble 복원용 — 본인 sender의 LLM invocation이 진행 중인지 조회.
     * 응답에 lastUserMessageAt 포함: FE가 이 시각 기준으로만 "신규 mediator 응답"을 식별하여
     * 폴링이 진행 중 typing 상태를 race로 종료시키는 것을 차단.
     */
    @GetMapping("/invocation-status")
    @SecurityRequirement(name = "bearer-jwt")
    public ResponseEntity<java.util.Map<String, Object>> getInvocationStatus(
        @PathVariable String sessionId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        var sender = roleResolver.resolveSender(sessionId, userDetails.getUsername());
        boolean inProgress = cancelableChatService.isInvocationActive(sessionId, sender);
        Instant lastUserMessageAt = messageRepository.findLastMessageAtBySender(sessionId, sender);
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("inProgress", inProgress);
        body.put("sender", sender.name());
        body.put("lastUserMessageAt", lastUserMessageAt);
        return ResponseEntity.ok(body);
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
        // V13 Phase 2: invite 조회는 TESTER 또는 duoModeEnabled 필요
        if (!duoModeEnabled && !hasTesterRole(userDetails)) {
            throw new BusinessException("DUO_MODE_DISABLED",
                    "현재 Duo 모드는 베타 준비 중이에요.", 403);
        }
        var response = sessionService.getInviteForExistingSession(sessionId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/invite")
    @SecurityRequirement(name = "bearer-jwt")
    public ResponseEntity<InviteTokenResponse> generateInvite(
        @PathVariable String sessionId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        // V13 Phase 2: invite 발급은 TESTER 또는 duoModeEnabled 필요
        if (!duoModeEnabled && !hasTesterRole(userDetails)) {
            throw new BusinessException("DUO_MODE_DISABLED",
                    "현재 Duo 모드는 베타 준비 중이에요.", 403);
        }
        var response = sessionService.generateInviteForExistingSession(sessionId, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }
}
