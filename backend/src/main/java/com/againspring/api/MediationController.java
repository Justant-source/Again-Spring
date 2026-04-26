package com.againspring.api;

import com.againspring.api.dto.response.InviteTokenResponse;
import com.againspring.service.ChatService;
import com.againspring.service.SessionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * MediationController (V1.5 카톡식)
 * V1.5부터 6턴 기반 로직은 제거되고, ChatService가 단순 카톡 메시지 처리를 담당
 * 이 컨트롤러는 더 이상 사용되지 않음 (MessageController 참조)
 *
 * 호환성을 위해 최소한의 구조만 유지
 */
@Slf4j
@RestController
@RequestMapping("/api/sessions/{sessionId}")
@RequiredArgsConstructor
@Tag(name = "Mediation", description = "Legacy mediation endpoints (V1.5 deprecated)")
@Deprecated
public class MediationController {

    private final SessionService sessionService;
    private final ChatService chatService;

    /**
     * 채팅 도중 초대 토큰 발급
     * POST /api/sessions/{sessionId}/invite
     */
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
