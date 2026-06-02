package com.againspring.service.community;

import com.againspring.api.community.dto.PostInviteDto;
import com.againspring.domain.community.Post;
import com.againspring.domain.enums.PostStatus;
import com.againspring.domain.enums.PublishMode;
import com.againspring.repository.community.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * PostInviteService - C3 파트너 초대 서비스
 * 초대 토큰 생성, 파트너 응답 처리, 발행 모드 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PostInviteService {

    private final PostRepository postRepository;

    @Value("${app.url:https://againspring.net}")
    private String appUrl;

    /**
     * 포스트에 대한 초대 토큰을 생성하고 초대 링크 반환
     *
     * @param postId 포스트 ID
     * @param userId 초대자 ID (권한 확인용)
     * @return 초대 응답 {inviteToken, inviteUrl}
     */
    public PostInviteDto.InviteResponse createInvite(String postId, String userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("POST_NOT_FOUND"));

        // 작성자 권한 확인
        if (!post.getAuthorId().equals(userId)) {
            throw new IllegalArgumentException("UNAUTHORIZED");
        }

        // 이미 초대 토큰이 있으면 재사용
        if (post.getInviteToken() != null) {
            return PostInviteDto.InviteResponse.builder()
                    .inviteToken(post.getInviteToken())
                    .inviteUrl(appUrl + "/s/" + post.getInviteToken())
                    .build();
        }

        // 새 토큰 생성: tok_ + UUID 12자
        String inviteToken = "tok_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        post.setInviteToken(inviteToken);
        postRepository.save(post);

        log.info("Created invite token for post {}: {}", postId, inviteToken);

        return PostInviteDto.InviteResponse.builder()
                .inviteToken(inviteToken)
                .inviteUrl(appUrl + "/s/" + inviteToken)
                .build();
    }

    /**
     * 초대 토큰으로 포스트 조회 (파트너 사전정보)
     *
     * @param token 초대 토큰
     * @return 포스트 프리뷰 {postId, userTitle, authorBodyPublished, category}
     */
    public PostInviteDto.PostByTokenResponse getPostByToken(String token) {
        Post post = postRepository.findByInviteToken(token)
                .orElseThrow(() -> new IllegalArgumentException("INVALID_INVITE_TOKEN"));

        return PostInviteDto.PostByTokenResponse.builder()
                .postId(post.getId())
                .userTitle(post.getUserTitle())
                .authorBodyPublished(post.getBodyPublished())
                .category(post.getCategory() != null ? post.getCategory().name() : null)
                .build();
    }

    /**
     * 파트너가 답변 제출
     *
     * @param token 초대 토큰
     * @param partnerUserId 파트너 사용자 ID
     * @param bodyRaw 파트너 답변 원문
     * @param userTitle 파트너 제목 (선택사항)
     */
    public void submitPartnerAnswer(String token, String partnerUserId, String bodyRaw, String userTitle) {
        Post post = postRepository.findByInviteToken(token)
                .orElseThrow(() -> new IllegalArgumentException("INVALID_INVITE_TOKEN"));

        // 이미 파트너 답변이 있으면 거절 (1 answer per invite)
        if (post.getPartnerAnsweredAt() != null) {
            throw new IllegalArgumentException("PARTNER_ALREADY_ANSWERED");
        }

        post.setPartnerUserId(partnerUserId);
        post.setPartnerBodyRaw(bodyRaw);
        post.setPartnerAnsweredAt(Instant.now());

        // userTitle이 제공되면 업데이트 (선택사항)
        if (userTitle != null && !userTitle.isBlank()) {
            post.setUserTitle(userTitle);
        }

        postRepository.save(post);
        log.info("Partner {} submitted answer to invite {}", partnerUserId, token);
    }

    /**
     * 발행 모드 설정 (발행 시점 및 투표 기간)
     *
     * @param postId 포스트 ID
     * @param userId 사용자 ID (권한 확인)
     * @param mode "PUBLISH_NOW" 또는 "WAIT_FOR_PARTNER"
     * @param voteDurationHours 투표 기간 시간 (24, 72, 168, null)
     */
    public void setPublishMode(String postId, String userId, String mode, Integer voteDurationHours) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("POST_NOT_FOUND"));

        if (!post.getAuthorId().equals(userId)) {
            throw new IllegalArgumentException("UNAUTHORIZED");
        }

        try {
            PublishMode publishMode = PublishMode.valueOf(mode);
            post.setPublishMode(publishMode);
            post.setVoteDurationHours(voteDurationHours);
            postRepository.save(post);
            log.info("Set publish mode for post {}: {}", postId, mode);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("INVALID_PUBLISH_MODE");
        }
    }

    /**
     * 즉시 발행 (투표 시작)
     *
     * @param postId 포스트 ID
     * @param userId 사용자 ID (권한 확인)
     */
    public void publishNow(String postId, String userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("POST_NOT_FOUND"));

        if (!post.getAuthorId().equals(userId)) {
            throw new IllegalArgumentException("UNAUTHORIZED");
        }

        // 상태를 VOTING으로 전환 (이미 VOTING이면 유지)
        if (PostStatus.DRAFT.equals(post.getStatus())) {
            post.setStatus(PostStatus.VOTING);
            postRepository.save(post);
            log.info("Published post {} immediately", postId);
        }
    }
}
