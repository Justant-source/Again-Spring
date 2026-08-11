package com.againspring.service.community;

import com.againspring.api.community.dto.PostInviteDto;
import com.againspring.common.exception.BusinessException;
import com.againspring.domain.community.Post;
import com.againspring.domain.enums.PostVisibility;
import com.againspring.domain.enums.PublishMode;
import com.againspring.repository.UserRepository;
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
 * 초대 토큰 생성, 파트너 응답·소유권·tombstone, 발행 모드 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PostInviteService {

    public static final String PARTNER_STATE_NONE = "NONE";
    public static final String PARTNER_STATE_ACTIVE = "ACTIVE";
    public static final String PARTNER_STATE_TOMBSTONE = "TOMBSTONE";

    public static final String OWNERSHIP_UNOWNED = "UNOWNED";
    public static final String OWNERSHIP_OWNED = "OWNED";
    public static final String OWNERSHIP_OWNED_BY_OTHER = "OWNED_BY_OTHER";
    public static final String OWNERSHIP_AUTHOR = "AUTHOR";

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostService postService;
    private final TonalizationService tonalizationService;
    private final AnswerProcessingService answerProcessingService;
    private final AiUserOutboxWriter aiUserOutboxWriter;

    @Value("${app.url:https://againspring.net}")
    private String appUrl;

    /**
     * 포스트에 대한 초대 토큰을 생성하고 초대 링크 반환
     */
    public PostInviteDto.InviteResponse createInvite(String postId, String userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("POST_NOT_FOUND", "게시글을 찾을 수 없어요.", 404));

        if (!post.getAuthorId().equals(userId)) {
            throw new BusinessException("UNAUTHORIZED", "권한이 없어요.", 403);
        }

        if (post.getInviteToken() != null) {
            return PostInviteDto.InviteResponse.builder()
                    .inviteToken(post.getInviteToken())
                    .inviteUrl(appUrl + "/s/" + post.getInviteToken())
                    .build();
        }

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
     * 초대 토큰으로 포스트 조회 (파트너 사전정보 + 소유권/권한).
     *
     * @param token 초대 토큰
     * @param callerUserId 요청자 ID (optional, JWT 있을 때)
     */
    @Transactional(readOnly = true)
    public PostInviteDto.PostByTokenResponse getPostByToken(String token, String callerUserId) {
        Post post = postRepository.findByInviteToken(token)
                .orElseThrow(() -> new BusinessException(
                        "INVALID_INVITE_TOKEN", "유효하지 않은 초대 링크예요.", 404));

        boolean deleted = post.getDeletedAt() != null;
        if (deleted) {
            return PostInviteDto.PostByTokenResponse.builder()
                    .postId(post.getId())
                    .deleted(true)
                    .partnerState(resolvePartnerState(post))
                    .ownership(resolveOwnership(post, callerUserId))
                    .canWrite(false)
                    .canEdit(false)
                    .canDelete(false)
                    .canClaim(false)
                    .build();
        }

        String partnerState = resolvePartnerState(post);
        String ownership = resolveOwnership(post, callerUserId);
        boolean partnerUnowned = isPartnerSlotUnowned(post);
        boolean isAuthor = OWNERSHIP_AUTHOR.equals(ownership);
        boolean isOwner = OWNERSHIP_OWNED.equals(ownership);
        boolean registeredCaller = isRegisteredMember(callerUserId);

        boolean canWrite = !isAuthor
                && (PARTNER_STATE_NONE.equals(partnerState)
                    || (PARTNER_STATE_TOMBSTONE.equals(partnerState)
                        && (partnerUnowned || isOwner)));
        boolean canEdit = !isAuthor
                && PARTNER_STATE_ACTIVE.equals(partnerState)
                && (partnerUnowned || isOwner);
        boolean canDelete = canEdit;
        boolean canClaim = !isAuthor
                && registeredCaller
                && partnerUnowned
                && (PARTNER_STATE_ACTIVE.equals(partnerState)
                    || PARTNER_STATE_TOMBSTONE.equals(partnerState));

        return PostInviteDto.PostByTokenResponse.builder()
                .postId(post.getId())
                .userTitle(post.getUserTitle())
                .authorBodyPublished(post.getAuthorBodyDeletedAt() != null ? null : post.getBodyPublished())
                .category(post.getCategory() != null ? post.getCategory().name() : null)
                .deleted(false)
                .partnerState(partnerState)
                .ownership(ownership)
                .partnerBodyPublished(PARTNER_STATE_ACTIVE.equals(partnerState)
                        ? post.getPartnerBodyPublished() : null)
                .canWrite(canWrite)
                .canEdit(canEdit)
                .canDelete(canDelete)
                .canClaim(canClaim)
                .build();
    }

    /** 하위 호환: 무인증 조회 */
    @Transactional(readOnly = true)
    public PostInviteDto.PostByTokenResponse getPostByToken(String token) {
        return getPostByToken(token, null);
    }

    /**
     * 파트너가 답변 제출 (NONE 신규 / TOMBSTONE 재작성).
     */
    public void submitPartnerAnswer(String token, String partnerUserId, String bodyRaw, String userTitle) {
        submitPartnerAnswer(token, partnerUserId, bodyRaw, userTitle, null);
    }

    public void submitPartnerAnswer(String token, String callerUserId, String bodyRaw, String userTitle,
                                    java.util.List<Integer> captureSplitAfterLines) {
        Post post = postRepository.findByInviteToken(token)
                .orElseThrow(() -> new BusinessException(
                        "INVALID_INVITE_TOKEN", "유효하지 않은 초대 링크예요.", 404));

        if (post.getDeletedAt() != null) {
            throw new BusinessException("POST_DELETED", "삭제된 게시글이에요.", 410);
        }

        if (callerUserId != null && post.getAuthorId().equals(callerUserId)) {
            throw new BusinessException(
                    "AUTHOR_CANNOT_BE_PARTNER", "작성자는 상대 슬롯에 답변할 수 없어요.", 403);
        }

        String partnerState = resolvePartnerState(post);
        if (PARTNER_STATE_ACTIVE.equals(partnerState)) {
            throw new BusinessException(
                    "PARTNER_ALREADY_ANSWERED", "이미 답변이 등록된 초대예요.", 409);
        }

        if (PARTNER_STATE_TOMBSTONE.equals(partnerState)) {
            // owned tombstone: only owner may rewrite
            if (!isPartnerSlotUnowned(post)
                    && (callerUserId == null || !callerUserId.equals(post.getPartnerUserId()))) {
                throw new BusinessException("FORBIDDEN", "상대 글을 다시 작성할 권한이 없어요.", 403);
            }
        }

        String partnerUserId = resolvePartnerUserIdForWrite(callerUserId);
        post.setPartnerUserId(partnerUserId);
        post.setPartnerBodyRaw(bodyRaw);
        post.setPartnerBodyPublished(bodyRaw);
        post.setPartnerBodyDeletedAt(null);
        if (userTitle != null && !userTitle.isBlank()) {
            post.setUserTitle(userTitle);
        }
        if (captureSplitAfterLines != null && !captureSplitAfterLines.isEmpty()) {
            post.setPartnerCaptureSplitAfterLines(captureSplitAfterLines);
        }

        post.setPartnerAnsweredAt(Instant.now());

        post.advanceContentRevision();
        postRepository.save(post);
        aiUserOutboxWriter.postRevised(post, "PARTNER_ANSWER_ADDED");
        log.info("Partner {} submitted answer to invite {} — async processing scheduled", partnerUserId, token);

        answerProcessingService.processAsync(post.getId(), bodyRaw, userTitle);
    }

    /**
     * 미연결 상대 슬롯을 로그인 회원 계정으로 연결.
     */
    public void claimPartner(String token, String userId) {
        if (userId == null || !isRegisteredMember(userId)) {
            throw new BusinessException("UNAUTHORIZED", "회원 로그인 후 연결할 수 있어요.", 403);
        }

        Post post = postRepository.findByInviteToken(token)
                .orElseThrow(() -> new BusinessException(
                        "INVALID_INVITE_TOKEN", "유효하지 않은 초대 링크예요.", 404));

        if (post.getDeletedAt() != null) {
            throw new BusinessException("POST_DELETED", "삭제된 게시글이에요.", 410);
        }

        if (post.getAuthorId().equals(userId)) {
            throw new BusinessException(
                    "AUTHOR_CANNOT_BE_PARTNER", "작성자는 상대 슬롯을 연결할 수 없어요.", 403);
        }

        String partnerState = resolvePartnerState(post);
        if (PARTNER_STATE_NONE.equals(partnerState)) {
            throw new BusinessException("NOTHING_TO_CLAIM", "연결할 상대 글이 없어요.", 409);
        }

        if (!isPartnerSlotUnowned(post)) {
            if (userId.equals(post.getPartnerUserId())) {
                return; // idempotent
            }
            throw new BusinessException("PARTNER_ALREADY_OWNED", "이미 다른 계정에 연결된 상대 글이에요.", 409);
        }

        post.setPartnerUserId(userId);
        postRepository.save(post);
        log.info("Partner slot claimed for invite {} by user {}", token, userId);
    }

    /**
     * 상대 본문 수정 (ACTIVE only).
     * unowned: 토큰 capability / owned: 소유 JWT.
     */
    public void editPartnerAnswer(String token, String callerUserId, String bodyRaw, String userTitle,
                                  java.util.List<Integer> captureSplitAfterLines) {
        Post post = postRepository.findByInviteToken(token)
                .orElseThrow(() -> new BusinessException(
                        "INVALID_INVITE_TOKEN", "유효하지 않은 초대 링크예요.", 404));

        if (post.getDeletedAt() != null) {
            throw new BusinessException("POST_DELETED", "삭제된 게시글이에요.", 410);
        }

        if (callerUserId != null && post.getAuthorId().equals(callerUserId)) {
            throw new BusinessException(
                    "AUTHOR_CANNOT_BE_PARTNER", "작성자는 상대 글을 수정할 수 없어요.", 403);
        }

        if (!PARTNER_STATE_ACTIVE.equals(resolvePartnerState(post))) {
            throw new BusinessException("PARTNER_NOT_EDITABLE", "수정할 상대 글이 없어요.", 409);
        }

        if (!canMutatePartnerBody(post, callerUserId)) {
            throw new BusinessException("FORBIDDEN", "상대 글을 수정할 권한이 없어요.", 403);
        }

        post.setPartnerBodyRaw(bodyRaw);
        post.setPartnerBodyPublished(bodyRaw);
        if (userTitle != null && !userTitle.isBlank()) {
            post.setUserTitle(userTitle);
        }
        if (captureSplitAfterLines != null && !captureSplitAfterLines.isEmpty()) {
            post.setPartnerCaptureSplitAfterLines(captureSplitAfterLines);
        }
        post.advanceContentRevision();
        postRepository.save(post);
        aiUserOutboxWriter.postRevised(post, "PARTNER_ANSWER_EDITED");
        answerProcessingService.processAsync(post.getId(), bodyRaw, userTitle);
        log.info("Partner answer edited for invite {}", token);
    }

    /**
     * 상대 본문 tombstone. 작성자 본문이 이미 tombstone이면 포스트 soft-delete + 댓글 soft-delete.
     * inviteToken은 유지.
     */
    public void deletePartnerAnswer(String token, String callerUserId) {
        Post post = postRepository.findByInviteToken(token)
                .orElseThrow(() -> new BusinessException(
                        "INVALID_INVITE_TOKEN", "유효하지 않은 초대 링크예요.", 404));

        if (post.getDeletedAt() != null) {
            throw new BusinessException("POST_DELETED", "삭제된 게시글이에요.", 410);
        }

        if (callerUserId != null && post.getAuthorId().equals(callerUserId)) {
            throw new BusinessException(
                    "AUTHOR_CANNOT_BE_PARTNER", "작성자는 이 경로로 상대 글을 삭제할 수 없어요.", 403);
        }

        if (!PARTNER_STATE_ACTIVE.equals(resolvePartnerState(post))) {
            throw new BusinessException("PARTNER_NOT_DELETABLE", "삭제할 상대 글이 없어요.", 409);
        }

        if (!canMutatePartnerBody(post, callerUserId)) {
            throw new BusinessException("FORBIDDEN", "상대 글을 삭제할 권한이 없어요.", 403);
        }

        // 작성자 삭제 경로와 동일: PostService.tombstonePartnerBody → 양쪽이면 hard-delete comments
        postService.tombstonePartnerBody(post);
        log.info("Partner answer tombstoned for invite {} (post {})", token, post.getId());
    }

    /**
     * 발행 모드 설정. WAIT_FOR_PARTNER ≈ PUBLISH_NOW (즉시 PUBLIC).
     * {@code voteDurationHours}는 API 호환용으로 받지만 무시한다 (시한부 투표 제거).
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
            // voteDurationHours / voteCloseAt: 시한부 투표 제거 — 저장·설정하지 않음
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
     * 즉시 발행 (visibility=PUBLIC). 공감 투표는 시한 없이 가능.
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

    /** visibility=PUBLIC만 설정. voteCloseAt 미설정. */
    private boolean applyImmediatePublic(Post post) {
        boolean becamePublic = post.getVisibility() != PostVisibility.PUBLIC;
        post.setVisibility(PostVisibility.PUBLIC);
        return becamePublic;
    }

    String resolvePartnerState(Post post) {
        if (post.getPartnerBodyDeletedAt() != null) {
            return PARTNER_STATE_TOMBSTONE;
        }
        if (post.getPartnerAnsweredAt() != null
                || (post.getPartnerBodyPublished() != null && !post.getPartnerBodyPublished().isBlank())
                || (post.getPartnerBodyRaw() != null && !post.getPartnerBodyRaw().isBlank())) {
            return PARTNER_STATE_ACTIVE;
        }
        return PARTNER_STATE_NONE;
    }

    String resolveOwnership(Post post, String callerUserId) {
        if (callerUserId != null && callerUserId.equals(post.getAuthorId())) {
            return OWNERSHIP_AUTHOR;
        }
        if (isPartnerSlotUnowned(post)) {
            return OWNERSHIP_UNOWNED;
        }
        if (callerUserId != null && callerUserId.equals(post.getPartnerUserId())) {
            return OWNERSHIP_OWNED;
        }
        return OWNERSHIP_OWNED_BY_OTHER;
    }

    /**
     * 미연결(unowned) 판정: partnerUserId null / partner_ prefix / guest / soft-deleted.
     */
    boolean isPartnerSlotUnowned(Post post) {
        String partnerUserId = post.getPartnerUserId();
        if (partnerUserId == null || partnerUserId.isBlank()) {
            return true;
        }
        if (partnerUserId.startsWith("partner_")) {
            return true;
        }
        return userRepository.findById(partnerUserId)
                .map(u -> u.isGuest() || u.getDeletedAt() != null)
                .orElse(true);
    }

    private boolean canMutatePartnerBody(Post post, String callerUserId) {
        if (isPartnerSlotUnowned(post)) {
            return true; // token capability
        }
        return callerUserId != null && callerUserId.equals(post.getPartnerUserId());
    }

    private boolean isRegisteredMember(String userId) {
        if (userId == null || userId.isBlank() || userId.startsWith("partner_")) {
            return false;
        }
        return userRepository.findById(userId)
                .map(u -> !u.isGuest() && u.getDeletedAt() == null)
                .orElse(false);
    }

    private String resolvePartnerUserIdForWrite(String callerUserId) {
        if (callerUserId == null || callerUserId.isBlank()) {
            return "partner_" + System.nanoTime();
        }
        // guest JWT → keep guest id (unowned); registered → member id (owned)
        return callerUserId;
    }

}
