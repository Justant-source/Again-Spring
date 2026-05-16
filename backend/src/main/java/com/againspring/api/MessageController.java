package com.againspring.api;

import com.againspring.api.dto.request.SendMessageRequest;
import com.againspring.api.dto.response.*;
import jakarta.validation.Valid;
import com.againspring.domain.enums.MessageSender;
import com.againspring.service.CancelableChatService;
import com.againspring.service.ChatService;
import com.againspring.service.SessionRoleResolver;
import com.againspring.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@Tag(name = "Chat", description = "채팅 메시지 송수신·초대·정리 게이트")
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
    @Operation(summary = "메시지 전송", description = "사용자 메시지를 즉시 저장하고 진행 중 LLM invocation을 취소한 뒤 새 invocation을 비동기로 시작한다. 위기 감지 시 409 반환.")
    @ApiResponse(responseCode = "200", description = "메시지 저장 완료, LLM 응답은 polling으로 수신")
    @ApiResponse(responseCode = "409", description = "위기 키워드 감지 — 세션 중단, 위기 모달 표시 필요")
    @ApiResponse(responseCode = "401", description = "인증 필요")
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
    @Operation(summary = "메시지 목록 폴링", description = "since(epoch ms) 이후 본인에게 보이는 메시지를 반환한다. since 미전달 시 전체 반환.")
    @ApiResponse(responseCode = "200", description = "메시지 목록")
    @ApiResponse(responseCode = "401", description = "인증 필요")
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
    @Operation(summary = "상대방 메시지 메타데이터 조회", description = "상대방이 메시지를 전송했는지 여부(타임스탬프 등)를 확인한다. 내용은 포함되지 않는다.")
    @ApiResponse(responseCode = "200", description = "상대방 메시지 메타데이터 목록")
    @ApiResponse(responseCode = "401", description = "인증 필요")
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
    @Operation(summary = "상대방 참여 상태 조회", description = "상대방이 세션에 참여 중인지 여부를 반환한다.")
    @ApiResponse(responseCode = "200", description = "상대방 상태")
    @ApiResponse(responseCode = "401", description = "인증 필요")
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
    @Operation(summary = "LLM invocation 진행 상태 조회", description = "현재 LLM이 응답을 생성 중인지 여부와 마지막 사용자 메시지 시각을 반환한다. 새로고침 후 TypingBubble 복원에 사용.")
    @ApiResponse(responseCode = "200", description = "inProgress, sender, lastUserMessageAt 반환")
    @ApiResponse(responseCode = "401", description = "인증 필요")
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
    @Operation(summary = "정리 요청", description = "현재 사용자가 세션 정리(대화 종료)를 요청한다. 5턴 이상 대화가 있어야 요청 가능.")
    @ApiResponse(responseCode = "200", description = "정리 상태 반환")
    @ApiResponse(responseCode = "401", description = "인증 필요")
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
    @Operation(summary = "정리 동의", description = "상대방의 정리 요청에 동의한다. Duo 세션에서 양쪽 모두 동의하면 세션이 완료 처리된다.")
    @ApiResponse(responseCode = "200", description = "정리 완료 또는 대기 중 상태 반환")
    @ApiResponse(responseCode = "401", description = "인증 필요")
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
    @Operation(summary = "정리 거절", description = "상대방의 정리 요청을 거절하고 대화를 계속한다.")
    @ApiResponse(responseCode = "200", description = "정리 거절 처리 완료")
    @ApiResponse(responseCode = "401", description = "인증 필요")
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
    @Operation(summary = "초대 토큰 조회", description = "세션의 기존 초대 토큰을 조회한다. Duo 모드가 활성화되거나 TESTER 역할이 있어야 접근 가능.")
    @ApiResponse(responseCode = "200", description = "초대 토큰 반환")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "Duo 모드 비활성 (DUO_MODE_DISABLED)")
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
    @Operation(summary = "초대 토큰 발급", description = "새 초대 토큰을 발급한다. Duo 모드가 활성화되거나 TESTER 역할이 있어야 접근 가능.")
    @ApiResponse(responseCode = "200", description = "신규 초대 토큰 반환")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "Duo 모드 비활성 (DUO_MODE_DISABLED)")
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
