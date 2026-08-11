package com.againspring.api.community;

import com.againspring.api.community.dto.PostInviteDto;
import com.againspring.service.community.PostInviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * PostInviteController - C3 파트너 초대 API
 * 초대 토큰 생성, 파트너 응답, 발행 모드 관리
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Community", description = "커뮤니티 포스트·투표·초대")
public class PostInviteController {

    private final PostInviteService postInviteService;

    /**
     * 초대 토큰 생성
     * POST /api/community/posts/{postId}/invite
     */
    @PostMapping("/community/posts/{postId}/invite")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "파트너 초대 토큰 생성")
    public ResponseEntity<PostInviteDto.InviteResponse> createInvite(
            @PathVariable String postId,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = userDetails.getUsername();
        PostInviteDto.InviteResponse response = postInviteService.createInvite(postId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 초대 토큰으로 포스트 조회 (파트너 사전정보)
     * GET /api/s/{token}
     * 인증 불필요 (공개 링크)
     */
    @GetMapping("/s/{token}")
    @Operation(summary = "초대 링크에서 포스트 조회")
    public ResponseEntity<PostInviteDto.PostByTokenResponse> getPostByToken(
            @PathVariable String token) {
        PostInviteDto.PostByTokenResponse response = postInviteService.getPostByToken(token);
        return ResponseEntity.ok(response);
    }

    /**
     * 파트너 답변 제출
     * POST /api/s/{token}/answer
     * 인증 불필요 (공개 링크) — 로그인 상태면 실제 userId 사용, 아니면 익명 ID
     */
    @PostMapping("/s/{token}/answer")
    @Operation(summary = "파트너 답변 제출")
    public ResponseEntity<Void> submitPartnerAnswer(
            @PathVariable String token,
            @Valid @RequestBody PostInviteDto.PartnerAnswerRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String partnerUserId = (userDetails != null)
                ? userDetails.getUsername()
                : "partner_" + System.nanoTime();
        postInviteService.submitPartnerAnswer(
                token, partnerUserId, request.getBodyRaw(), request.getUserTitle(),
                request.getCaptureSplitAfterLines());
        return ResponseEntity.ok().build();
    }

    /**
     * 발행 모드 설정 (발행 시점 및 투표 기간)
     * PATCH /api/community/posts/{postId}/publish-mode
     */
    @PatchMapping("/community/posts/{postId}/publish-mode")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "발행 모드 설정")
    public ResponseEntity<Void> setPublishMode(
            @PathVariable String postId,
            @Valid @RequestBody PostInviteDto.PublishModeRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = userDetails.getUsername();
        postInviteService.setPublishMode(postId, userId, request.getMode(), request.getVoteDurationHours());
        return ResponseEntity.ok().build();
    }

    /**
     * 즉시 발행 (투표 시작)
     * POST /api/community/posts/{postId}/publish-now
     */
    @PostMapping("/community/posts/{postId}/publish-now")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "포스트 즉시 발행")
    public ResponseEntity<Void> publishNow(
            @PathVariable String postId,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = userDetails.getUsername();
        postInviteService.publishNow(postId, userId);
        return ResponseEntity.ok().build();
    }
}
