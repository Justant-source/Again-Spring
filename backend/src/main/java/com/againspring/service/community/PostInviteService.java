package com.againspring.service.community;

import com.againspring.api.community.dto.PostInviteDto;
import com.againspring.common.exception.BusinessException;
import com.againspring.domain.community.Post;
import com.againspring.domain.enums.PostVisibility;
import com.againspring.domain.enums.PublishMode;
import com.againspring.repository.community.PostRepository;
import com.againspring.service.ai.AiUserOutboxWriter;
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
    private final TonalizationService tonalizationService;
    private final AnswerProcessingService answerProcessingService;
    private final AiUserOutboxWriter aiUserOutboxWriter;

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
                .orElseThrow(() -> new BusinessException("POST_NOT_FOUND", "게시글을 찾을 수 없어요.", 404));

        // 작성자 권한 확인
        if (!post.getAuthorId().equals(userId)) {
            throw new BusinessException("UNAUTHORIZED", "권한이 없어요.", 403);
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
                .orElseThrow(() -> new BusinessException(
                        "INVALID_INVITE_TOKEN", "유효하지 않은 초대 링크예요.", 404));

        return PostInviteDto.PostByTokenResponse.builder()
                .postId(post.getId())
                .userTitle(post.getUserTitle())
                .authorBodyPublished(post.getBodyPublished())
                .category(post.getCategory() != null ? post.getCategory().name() : null)
                .build();
    }

    /**
     * 파트너가 답변 제출.
     * 이미 공개된 글에 partner body만 부착한다 (공개 게이트 아님).
     *
     * @param token 초대 토큰
     * @param partnerUserId 파트너 사용자 ID
     * @param bodyRaw 파트너 답변 원문
     * @param userTitle 파트너 제목 (선택사항)
     */
    public void submitPartnerAnswer(String token, String partnerUserId, String bodyRaw, String userTitle) {
        submitPartnerAnswer(token, partnerUserId, bodyRaw, userTitle, null);
    }

    public void submitPartnerAnswer(String token, String partnerUserId, String bodyRaw, String userTitle,
                                    java.util.List<Integer> captureSplitAfterLines) {
        Post post = postRepository.findByInviteToken(token)
                .orElseThrow(() -> new BusinessException(
                        "INVALID_INVITE_TOKEN", "유효하지 않은 초대 링크예요.", 404));

        // 이미 파트너 답변이 있으면 거절 (1 answer per invite)
        if (post.getPartnerAnsweredAt() != null) {
            throw new BusinessException(
                    "PARTNER_ALREADY_ANSWERED", "이미 답변이 등록된 초대예요.", 409);
        }

        post.setPartnerUserId(partnerUserId);
        post.setPartnerBodyRaw(bodyRaw);
        // 원문을 즉시 저장 — tonalization은 비동기로 덮어씀
        post.setPartnerBodyPublished(bodyRaw);
        if (userTitle != null && !userTitle.isBlank()) {
            post.setUserTitle(userTitle);
        }
        if (captureSplitAfterLines != null && !captureSplitAfterLines.isEmpty()) {
            post.setPartnerCaptureSplitAfterLines(captureSplitAfterLines);
        }

        post.setPartnerAnsweredAt(Instant.now());

        post.advanceContentRevision();
        // partner 입장 추가는 게시글 수정과 동일하게 후속 계획을 무효화한다.
        postRepository.save(post);
        aiUserOutboxWriter.postRevised(post, "PARTNER_ANSWER_ADDED");
        log.info("Partner {} submitted answer to invite {} — async processing scheduled", partnerUserId, token);

        // tonalization 비동기 처리 — HTTP 응답을 블록하지 않음
        answerProcessingService.processAsync(post.getId(), bodyRaw, userTitle);
    }

    /**
     * 발행 모드 설정. WAIT_FOR_PARTNER ≈ PUBLISH_NOW (즉시 PUBLIC + voteCloseAt).
     *
     * @param postId 포스트 ID
     * @param userId 사용자 ID (권한 확인)
     * @param mode "PUBLISH_NOW" 또는 "WAIT_FOR_PARTNER"
     * @param voteDurationHours 투표 기간 시간 (24, 72, 168, null)
     */
    public void setPublishMode(String postId, String userId, String mode, Integer voteDurationHours) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("POST_NOT_FOUND", "게시글을 찾을 수 없어요.", 404));

        if (!post.getAuthorId().equals(userId)) {
            throw new BusinessException("UNAUTHORIZED", "권한이 없어요.", 403);
        }

        try {
            PublishMode publishMode = PublishMode.valueOf(mode);
            post.setPublishMode(publishMode);
            post.setVoteDurationHours(voteDurationHours);
            boolean becamePublic = applyImmediatePublic(post);
            Post saved = postRepository.save(post);
            if (becamePublic) {
                aiUserOutboxWriter.postPublished(saved);
            }
            log.info("Set publish mode for post {}: {}", postId, mode);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_PUBLISH_MODE", "지원하지 않는 발행 모드예요.", 400);
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
                .orElseThrow(() -> new BusinessException("POST_NOT_FOUND", "게시글을 찾을 수 없어요.", 404));

        if (!post.getAuthorId().equals(userId)) {
            throw new BusinessException("UNAUTHORIZED", "권한이 없어요.", 403);
        }

        boolean becamePublic = applyImmediatePublic(post);
        Post saved = postRepository.save(post);
        if (becamePublic) {
            aiUserOutboxWriter.postPublished(saved);
        }
        log.info("Published post {} immediately", postId);
    }

    /**
     * visibility=PUBLIC + voteCloseAt 설정 (없으면 voteDurationHours 또는 72h).
     * @return true if visibility changed to PUBLIC
     */
    private boolean applyImmediatePublic(Post post) {
        boolean becamePublic = post.getVisibility() != PostVisibility.PUBLIC;
        post.setVisibility(PostVisibility.PUBLIC);
        if (post.getVoteCloseAt() == null) {
            int hours = (post.getVoteDurationHours() != null) ? post.getVoteDurationHours() : 72;
            post.setVoteCloseAt(Instant.now().plusSeconds((long) hours * 3600));
        }
        return becamePublic;
    }
}
