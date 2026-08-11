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
 * 초대 토큰 생성, 파트너 응답·소유권·tombstone, 발행 모드 관리
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
     * 초대 토큰으로 포스트 조회 (파트너 사전정보 + 소유권)
     * GET /api/s/{token}
     * 인증 optional
     */
    @GetMapping("/s/{token}")
    @Operation(summary = "초대 링크에서 포스트 조회")
    public ResponseEntity<PostInviteDto.PostByTokenResponse> getPostByToken(
            @PathVariable String token,
            @AuthenticationPrincipal UserDetails userDetails) {
        String callerUserId = userDetails != null ? userDetails.getUsername() : null;
        PostInviteDto.PostByTokenResponse response = postInviteService.getPostByToken(token, callerUserId);
        return ResponseEntity.ok(response);
    }

    /**
     * 파트너 답변 제출 (NONE 신규 / TOMBSTONE 재작성)
     * POST /api/s/{token}/answer
     * 인증 optional — 로그인 회원이면 OWNED, 게스트면 UNOWNED
     */
    @PostMapping("/s/{token}/answer")
    @Operation(summary = "파트너 답변 제출")
    public ResponseEntity<Void> submitPartnerAnswer(
            @PathVariable String token,
            @Valid @RequestBody PostInviteDto.PartnerAnswerRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String callerUserId = userDetails != null ? userDetails.getUsername() : null;
        postInviteService.submitPartnerAnswer(
                token, callerUserId, request.getBodyRaw(), request.getUserTitle(),
                request.getCaptureSplitAfterLines());
        return ResponseEntity.ok().build();
    }

    /**
     * 미연결 상대 슬롯을 회원 계정으로 연결
     * POST /api/s/{token}/claim
     */
    @PostMapping("/s/{token}/claim")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "상대 슬롯을 내 계정으로 연결")
    public ResponseEntity<Void> claimPartner(
            @PathVariable String token,
            @AuthenticationPrincipal UserDetails userDetails) {
        postInviteService.claimPartner(token, userDetails.getUsername());
        return ResponseEntity.ok().build();
    }

    /**
     * 상대 본문 수정
     * PATCH /api/s/{token}/answer
     * unowned: 토큰만 / owned: 소유 JWT
     */
    @PatchMapping("/s/{token}/answer")
    @Operation(summary = "파트너 답변 수정")
    public ResponseEntity<Void> editPartnerAnswer(
            @PathVariable String token,
            @Valid @RequestBody PostInviteDto.PartnerAnswerRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String callerUserId = userDetails != null ? userDetails.getUsername() : null;
        postInviteService.editPartnerAnswer(
                token, callerUserId, request.getBodyRaw(), request.getUserTitle(),
                request.getCaptureSplitAfterLines());
        return ResponseEntity.ok().build();
    }

    /**
     * 상대 본문 tombstone (양쪽 tombstone 시 포스트 soft-delete)
     * DELETE /api/s/{token}/answer
     */
    @DeleteMapping("/s/{token}/answer")
    @Operation(summary = "파트너 답변 삭제(tombstone)")
    public ResponseEntity<Void> deletePartnerAnswer(
            @PathVariable String token,
            @AuthenticationPrincipal UserDetails userDetails) {
        String callerUserId = userDetails != null ? userDetails.getUsername() : null;
        postInviteService.deletePartnerAnswer(token, callerUserId);
        return ResponseEntity.ok().build();
    }

    /**
     * 발행 모드 설정 (즉시 PUBLIC). voteDurationHours는 legacy·무시.
     * PATCH /api/community/posts/{postId}/publish-mode
     */
    @PatchMapping("/community/posts/{postId}/publish-mode")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "발행 모드 설정", description = "voteDurationHours는 호환용으로 받지만 무시합니다(시한부 투표 제거).")
    public ResponseEntity<Void> setPublishMode(
            @PathVariable String postId,
            @Valid @RequestBody PostInviteDto.PublishModeRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = userDetails.getUsername();
        postInviteService.setPublishMode(postId, userId, request.getMode(), request.getVoteDurationHours());
        return ResponseEntity.ok().build();
    }

    /**
     * 즉시 발행 (visibility=PUBLIC). 공감 투표는 시한 없이 가능.
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
