package com.againspring.service.community;

import com.againspring.api.community.dto.PostInviteDto;
import com.againspring.common.exception.BusinessException;
import com.againspring.domain.User;
import com.againspring.domain.community.Post;
import com.againspring.domain.enums.PostStatus;
import com.againspring.domain.enums.PostVisibility;
import com.againspring.domain.enums.PublishMode;
import com.againspring.repository.UserRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.service.ai.AiUserOutboxWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostInviteService Tests")
class PostInviteServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostService postService;

    @Mock
    private TonalizationService tonalizationService;

    @Mock
    private AnswerProcessingService answerProcessingService;

    @Mock
    private AiUserOutboxWriter aiUserOutboxWriter;

    @InjectMocks
    private PostInviteService postInviteService;

    private Post post;
    private final String POST_ID = "post-001";
    private final String AUTHOR_ID = "user-001";
    private final String PARTNER_ID = "partner-001";
    private final String MEMBER_ID = "member-002";
    private final String GUEST_ID = "guest-001";
    private final String INVITE_TOKEN = "tok_abc123456789";

    @BeforeEach
    void setUp() {
        post = Post.builder()
                .id(POST_ID)
                .authorId(AUTHOR_ID)
                .status(PostStatus.DRAFT)
                .visibility(PostVisibility.PRIVATE)
                .publishMode(PublishMode.PUBLISH_NOW)
                .bodyRaw("작성자의 이야기")
                .bodyPublished("작성자의 이야기")
                .inviteToken(INVITE_TOKEN)
                .build();
    }

    private User registered(String id) {
        return User.builder().id(id).nickname("n").isGuest(false).build();
    }

    private User guest(String id) {
        return User.builder().id(id).nickname("g").isGuest(true).build();
    }

    // ── existing submit / publish tests ─────────────────────────────────────

    @Test
    @DisplayName("submitPartnerAnswer - 파트너 답변 제출 시 partnerBodyPublished == bodyRaw")
    void submitPartnerAnswer_setsPartnerBodyPublished() {
        String partnerBodyRaw = "파트너의 답변";
        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.submitPartnerAnswer(INVITE_TOKEN, PARTNER_ID, partnerBodyRaw, null);

        verify(postRepository).save(any(Post.class));
        assertEquals(partnerBodyRaw, post.getPartnerBodyRaw());
        assertEquals(partnerBodyRaw, post.getPartnerBodyPublished());
        assertNotNull(post.getPartnerAnsweredAt());
        assertEquals(PARTNER_ID, post.getPartnerUserId());
        assertNull(post.getPartnerBodyDeletedAt());
    }

    @Test
    @DisplayName("submitPartnerAnswer - WAIT_FOR_PARTNER여도 visibility 변경 없음 (공개 게이트 아님)")
    void submitPartnerAnswer_waitForPartner_doesNotGatePublic() {
        post.setPublishMode(PublishMode.WAIT_FOR_PARTNER);
        post.setVisibility(PostVisibility.PRIVATE);
        String partnerBodyRaw = "파트너의 답변";

        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.submitPartnerAnswer(INVITE_TOKEN, PARTNER_ID, partnerBodyRaw, null);

        verify(postRepository).save(any(Post.class));
        assertEquals(PostVisibility.PRIVATE, post.getVisibility());
        assertEquals(partnerBodyRaw, post.getPartnerBodyPublished());
        verify(aiUserOutboxWriter).postRevised(post, "PARTNER_ANSWER_ADDED");
        verify(aiUserOutboxWriter, never()).postPublished(any());
    }

    @Test
    @DisplayName("submitPartnerAnswer - PUBLISH_NOW 모드: 파트너 답변 후에도 visibility 변경 없음")
    void submitPartnerAnswer_publishNow_doesNotAutoPublish() {
        post.setPublishMode(PublishMode.PUBLISH_NOW);
        post.setVisibility(PostVisibility.PRIVATE);
        String partnerBodyRaw = "파트너의 답변";

        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.submitPartnerAnswer(INVITE_TOKEN, PARTNER_ID, partnerBodyRaw, null);

        verify(postRepository).save(any(Post.class));
        assertEquals(PostVisibility.PRIVATE, post.getVisibility());
    }

    @Test
    @DisplayName("submitPartnerAnswer - 이미 PUBLIC인 글에 partner body만 부착")
    void submitPartnerAnswer_alreadyPublic_attachesBodyOnly() {
        post.setPublishMode(PublishMode.WAIT_FOR_PARTNER);
        post.setVisibility(PostVisibility.PUBLIC);
        String partnerBodyRaw = "파트너의 답변";

        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.submitPartnerAnswer(INVITE_TOKEN, PARTNER_ID, partnerBodyRaw, null);

        assertEquals(PostVisibility.PUBLIC, post.getVisibility());
        assertEquals(partnerBodyRaw, post.getPartnerBodyPublished());
        verify(aiUserOutboxWriter, never()).postPublished(any());
    }

    @Test
    @DisplayName("submitPartnerAnswer - 이미 ACTIVE면 PARTNER_ALREADY_ANSWERED")
    void submitPartnerAnswer_alreadyAnswered_throws() {
        post.setPartnerAnsweredAt(Instant.now());
        post.setPartnerBodyPublished("기존");
        String partnerBodyRaw = "파트너의 답변";

        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> postInviteService.submitPartnerAnswer(INVITE_TOKEN, PARTNER_ID, partnerBodyRaw, null)
        );

        assertEquals("PARTNER_ALREADY_ANSWERED", exception.getCode());
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    @DisplayName("submitPartnerAnswer - 작성자 본인 → AUTHOR_CANNOT_BE_PARTNER 403")
    void submitPartnerAnswer_authorCannotBePartner() {
        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> postInviteService.submitPartnerAnswer(INVITE_TOKEN, AUTHOR_ID, "답변", null));

        assertEquals("AUTHOR_CANNOT_BE_PARTNER", ex.getCode());
        assertEquals(403, ex.getHttpStatus());
    }

    @Test
    @DisplayName("submitPartnerAnswer - TOMBSTONE 재작성 시 partner_body_deleted_at clear")
    void submitPartnerAnswer_tombstoneRewrite_clearsTombstone() {
        post.setPartnerUserId("partner_old");
        post.setPartnerBodyDeletedAt(Instant.now().minusSeconds(60));
        post.setPartnerAnsweredAt(Instant.now().minusSeconds(120));
        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.submitPartnerAnswer(INVITE_TOKEN, GUEST_ID, "다시 쓴 답변", null);

        assertNull(post.getPartnerBodyDeletedAt());
        assertEquals("다시 쓴 답변", post.getPartnerBodyPublished());
        assertEquals(GUEST_ID, post.getPartnerUserId());
    }

    @Test
    @DisplayName("submitPartnerAnswer - userTitle 제공 시 업데이트")
    void submitPartnerAnswer_withUserTitle_updates() {
        String partnerBodyRaw = "파트너의 답변";
        String partnerTitle = "파트너 제목";

        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.submitPartnerAnswer(INVITE_TOKEN, PARTNER_ID, partnerBodyRaw, partnerTitle);

        verify(postRepository).save(any(Post.class));
        assertEquals(partnerTitle, post.getUserTitle());
    }

    @Test
    @DisplayName("submitPartnerAnswer - userTitle이 null이면 기존 userTitle 유지")
    void submitPartnerAnswer_userTitleNull_preservesExisting() {
        String originalTitle = "기존 제목";
        post.setUserTitle(originalTitle);
        String partnerBodyRaw = "파트너의 답변";

        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.submitPartnerAnswer(INVITE_TOKEN, PARTNER_ID, partnerBodyRaw, null);

        verify(postRepository).save(any(Post.class));
        assertEquals(originalTitle, post.getUserTitle());
    }

    @Test
    @DisplayName("submitPartnerAnswer - INVALID_INVITE_TOKEN 예외")
    void submitPartnerAnswer_invalidToken_throws() {
        when(postRepository.findByInviteToken("invalid_token")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> postInviteService.submitPartnerAnswer("invalid_token", PARTNER_ID, "답변", null)
        );

        assertEquals("INVALID_INVITE_TOKEN", exception.getCode());
        verify(postRepository).findByInviteToken("invalid_token");
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    @DisplayName("submitPartnerAnswer - 무인증 → partner_ prefix (UNOWNED)")
    void submitPartnerAnswer_anonymous_usesPartnerPrefix() {
        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.submitPartnerAnswer(INVITE_TOKEN, null, "익명 답변", null);

        assertTrue(post.getPartnerUserId().startsWith("partner_"));
    }

    // ── getPostByToken ownership ────────────────────────────────────────────

    @Test
    @DisplayName("getPostByToken - NONE + anonymous → UNOWNED, canWrite")
    void getPostByToken_none_anonymous() {
        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));

        PostInviteDto.PostByTokenResponse res = postInviteService.getPostByToken(INVITE_TOKEN, null);

        assertFalse(res.isDeleted());
        assertEquals("NONE", res.getPartnerState());
        assertEquals("UNOWNED", res.getOwnership());
        assertTrue(res.isCanWrite());
        assertFalse(res.isCanEdit());
        assertFalse(res.isCanDelete());
        assertFalse(res.isCanClaim());
        assertEquals("작성자의 이야기", res.getAuthorBodyPublished());
    }

    @Test
    @DisplayName("getPostByToken - 작성자 조회 → ownership AUTHOR, canWrite/claim false")
    void getPostByToken_author() {
        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));

        PostInviteDto.PostByTokenResponse res = postInviteService.getPostByToken(INVITE_TOKEN, AUTHOR_ID);

        assertEquals("AUTHOR", res.getOwnership());
        assertFalse(res.isCanWrite());
        assertFalse(res.isCanClaim());
    }

    @Test
    @DisplayName("getPostByToken - ACTIVE unowned(partner_) → canEdit/canDelete/canClaim")
    void getPostByToken_activeUnowned() {
        post.setPartnerUserId("partner_123");
        post.setPartnerBodyPublished("상대 본문");
        post.setPartnerAnsweredAt(Instant.now());
        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(registered(MEMBER_ID)));

        PostInviteDto.PostByTokenResponse res = postInviteService.getPostByToken(INVITE_TOKEN, MEMBER_ID);

        assertEquals("ACTIVE", res.getPartnerState());
        assertEquals("UNOWNED", res.getOwnership());
        assertFalse(res.isCanWrite());
        assertTrue(res.isCanEdit());
        assertTrue(res.isCanDelete());
        assertTrue(res.isCanClaim());
        assertEquals("상대 본문", res.getPartnerBodyPublished());
    }

    @Test
    @DisplayName("getPostByToken - ACTIVE owned by other → OWNED_BY_OTHER, no mutate")
    void getPostByToken_ownedByOther() {
        post.setPartnerUserId(MEMBER_ID);
        post.setPartnerBodyPublished("상대 본문");
        post.setPartnerAnsweredAt(Instant.now());
        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(registered(MEMBER_ID)));

        PostInviteDto.PostByTokenResponse res = postInviteService.getPostByToken(INVITE_TOKEN, "other-member");

        assertEquals("OWNED_BY_OTHER", res.getOwnership());
        assertFalse(res.isCanEdit());
        assertFalse(res.isCanDelete());
        assertFalse(res.isCanClaim());
        assertFalse(res.isCanWrite());
    }

    @Test
    @DisplayName("getPostByToken - ACTIVE owned by caller → OWNED, canEdit")
    void getPostByToken_ownedByCaller() {
        post.setPartnerUserId(MEMBER_ID);
        post.setPartnerBodyPublished("상대 본문");
        post.setPartnerAnsweredAt(Instant.now());
        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(registered(MEMBER_ID)));

        PostInviteDto.PostByTokenResponse res = postInviteService.getPostByToken(INVITE_TOKEN, MEMBER_ID);

        assertEquals("OWNED", res.getOwnership());
        assertTrue(res.isCanEdit());
        assertTrue(res.isCanDelete());
        assertFalse(res.isCanClaim());
    }

    @Test
    @DisplayName("getPostByToken - guest partnerUserId → UNOWNED")
    void getPostByToken_guestPartnerIsUnowned() {
        post.setPartnerUserId(GUEST_ID);
        post.setPartnerBodyPublished("게스트 본문");
        post.setPartnerAnsweredAt(Instant.now());
        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(userRepository.findById(GUEST_ID)).thenReturn(Optional.of(guest(GUEST_ID)));

        PostInviteDto.PostByTokenResponse res = postInviteService.getPostByToken(INVITE_TOKEN, null);

        assertEquals("UNOWNED", res.getOwnership());
        assertTrue(res.isCanEdit());
    }

    @Test
    @DisplayName("getPostByToken - deleted post → deleted true")
    void getPostByToken_deleted() {
        post.setDeletedAt(Instant.now());
        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));

        PostInviteDto.PostByTokenResponse res = postInviteService.getPostByToken(INVITE_TOKEN, null);

        assertTrue(res.isDeleted());
        assertNull(res.getAuthorBodyPublished());
        assertFalse(res.isCanWrite());
    }

    // ── claim ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("claimPartner - unowned ACTIVE → partnerUserId = member")
    void claimPartner_success() {
        post.setPartnerUserId("partner_xyz");
        post.setPartnerBodyPublished("본문");
        post.setPartnerAnsweredAt(Instant.now());
        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(registered(MEMBER_ID)));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.claimPartner(INVITE_TOKEN, MEMBER_ID);

        assertEquals(MEMBER_ID, post.getPartnerUserId());
    }

    @Test
    @DisplayName("claimPartner - author → AUTHOR_CANNOT_BE_PARTNER")
    void claimPartner_authorRejected() {
        post.setPartnerUserId("partner_xyz");
        post.setPartnerAnsweredAt(Instant.now());
        post.setPartnerBodyPublished("본문");
        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(userRepository.findById(AUTHOR_ID)).thenReturn(Optional.of(registered(AUTHOR_ID)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> postInviteService.claimPartner(INVITE_TOKEN, AUTHOR_ID));
        assertEquals("AUTHOR_CANNOT_BE_PARTNER", ex.getCode());
    }

    @Test
    @DisplayName("claimPartner - guest JWT → 403")
    void claimPartner_guestRejected() {
        when(userRepository.findById(GUEST_ID)).thenReturn(Optional.of(guest(GUEST_ID)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> postInviteService.claimPartner(INVITE_TOKEN, GUEST_ID));
        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    @Test
    @DisplayName("claimPartner - already owned by other → 409")
    void claimPartner_alreadyOwned() {
        post.setPartnerUserId(MEMBER_ID);
        post.setPartnerBodyPublished("본문");
        post.setPartnerAnsweredAt(Instant.now());
        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(userRepository.findById("member-003")).thenReturn(Optional.of(registered("member-003")));
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(registered(MEMBER_ID)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> postInviteService.claimPartner(INVITE_TOKEN, "member-003"));
        assertEquals("PARTNER_ALREADY_OWNED", ex.getCode());
    }

    // ── edit / delete ───────────────────────────────────────────────────────

    @Test
    @DisplayName("editPartnerAnswer - unowned: 토큰만으로 수정")
    void editPartnerAnswer_unowned() {
        post.setPartnerUserId("partner_xyz");
        post.setPartnerBodyPublished("old");
        post.setPartnerAnsweredAt(Instant.now());
        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.editPartnerAnswer(INVITE_TOKEN, null, "new body", null, null);

        assertEquals("new body", post.getPartnerBodyPublished());
        verify(aiUserOutboxWriter).postRevised(post, "PARTNER_ANSWER_EDITED");
    }

    @Test
    @DisplayName("editPartnerAnswer - owned: non-owner → 403")
    void editPartnerAnswer_ownedNonOwnerForbidden() {
        post.setPartnerUserId(MEMBER_ID);
        post.setPartnerBodyPublished("old");
        post.setPartnerAnsweredAt(Instant.now());
        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(registered(MEMBER_ID)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> postInviteService.editPartnerAnswer(INVITE_TOKEN, "other", "x", null, null));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    @DisplayName("deletePartnerAnswer - tombstone partner body, keep inviteToken")
    void deletePartnerAnswer_tombstone() {
        post.setPartnerUserId("partner_xyz");
        post.setPartnerBodyRaw("raw");
        post.setPartnerBodyPublished("pub");
        post.setPartnerAnsweredAt(Instant.now());
        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(postService.tombstonePartnerBody(post)).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            p.setPartnerBodyRaw(null);
            p.setPartnerBodyPublished(null);
            p.setPartnerBodyDeletedAt(Instant.now());
            return p;
        });

        postInviteService.deletePartnerAnswer(INVITE_TOKEN, null);

        assertNull(post.getPartnerBodyRaw());
        assertNull(post.getPartnerBodyPublished());
        assertNotNull(post.getPartnerBodyDeletedAt());
        assertEquals(INVITE_TOKEN, post.getInviteToken());
        assertNull(post.getDeletedAt());
        verify(postService).tombstonePartnerBody(post);
    }

    @Test
    @DisplayName("deletePartnerAnswer - author already tombstoned → fullDelete via PostService")
    void deletePartnerAnswer_bothSidesSoftDelete() {
        post.setPartnerUserId("partner_xyz");
        post.setPartnerBodyPublished("pub");
        post.setPartnerAnsweredAt(Instant.now());
        post.setAuthorBodyDeletedAt(Instant.now().minusSeconds(30));
        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(postService.tombstonePartnerBody(post)).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            p.setPartnerBodyDeletedAt(Instant.now());
            p.setDeletedAt(Instant.now());
            return p;
        });

        postInviteService.deletePartnerAnswer(INVITE_TOKEN, null);

        assertNotNull(post.getDeletedAt());
        assertNotNull(post.getPartnerBodyDeletedAt());
        assertEquals(INVITE_TOKEN, post.getInviteToken());
        verify(postService).tombstonePartnerBody(post);
    }

    // ── publish mode (시한부 투표 제거) ────────────────────────────────────

    @Test
    @DisplayName("setPublishMode - WAIT_FOR_PARTNER ≈ PUBLISH_NOW: 즉시 PUBLIC, voteCloseAt 미설정")
    void setPublishMode_waitForPartner_appliesImmediatePublic() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.setPublishMode(POST_ID, AUTHOR_ID, "WAIT_FOR_PARTNER", 24);

        assertEquals(PublishMode.WAIT_FOR_PARTNER, post.getPublishMode());
        assertNull(post.getVoteDurationHours()); // duration ignored
        assertEquals(PostVisibility.PUBLIC, post.getVisibility());
        assertNull(post.getVoteCloseAt());
        verify(aiUserOutboxWriter).postPublished(post);
    }

    @Test
    @DisplayName("setPublishMode - PUBLISH_NOW도 즉시 PUBLIC, voteCloseAt 미설정")
    void setPublishMode_publishNow_appliesImmediatePublic() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.setPublishMode(POST_ID, AUTHOR_ID, "PUBLISH_NOW", 72);

        assertEquals(PublishMode.PUBLISH_NOW, post.getPublishMode());
        assertEquals(PostVisibility.PUBLIC, post.getVisibility());
        assertNull(post.getVoteCloseAt());
        verify(aiUserOutboxWriter).postPublished(post);
    }

    @Test
    @DisplayName("setPublishMode - 이미 PUBLIC이면 postPublished 미호출")
    void setPublishMode_alreadyPublic_noRepublishEvent() {
        post.setVisibility(PostVisibility.PUBLIC);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.setPublishMode(POST_ID, AUTHOR_ID, "WAIT_FOR_PARTNER", 24);

        assertEquals(PostVisibility.PUBLIC, post.getVisibility());
        assertNull(post.getVoteCloseAt());
        verify(aiUserOutboxWriter, never()).postPublished(any());
    }

    @Test
    @DisplayName("setPublishMode - 잘못된 모드면 INVALID_PUBLISH_MODE")
    void setPublishMode_invalidMode_throws() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> postInviteService.setPublishMode(POST_ID, AUTHOR_ID, "NOPE", null)
        );
        assertEquals("INVALID_PUBLISH_MODE", ex.getCode());
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    @DisplayName("publishNow - visibility=PUBLIC, voteCloseAt 미설정")
    void publishNow_setsVisibilityPublic() {
        post.setStatus(PostStatus.DRAFT);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.publishNow(POST_ID, AUTHOR_ID);

        verify(postRepository).save(any(Post.class));
        assertEquals(PostVisibility.PUBLIC, post.getVisibility());
        assertNull(post.getVoteCloseAt());
    }

    @Test
    @DisplayName("publishNow - voteDurationHours가 있어도 voteCloseAt 미설정 (시한부 제거)")
    void publishNow_ignoresVoteDurationHours() {
        post.setVoteDurationHours(24);

        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.publishNow(POST_ID, AUTHOR_ID);

        verify(postRepository).save(any(Post.class));
        assertNull(post.getVoteCloseAt());
        assertEquals(PostVisibility.PUBLIC, post.getVisibility());
    }

    @Test
    @DisplayName("publishNow - 비작성자가 호출하면 UNAUTHORIZED 예외")
    void publishNow_unauthorizedUser_throws() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> postInviteService.publishNow(POST_ID, "other-user")
        );

        assertEquals("UNAUTHORIZED", exception.getCode());
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    @DisplayName("publishNow - POST_NOT_FOUND 예외")
    void publishNow_postNotFound_throws() {
        when(postRepository.findById("nonexistent")).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> postInviteService.publishNow("nonexistent", AUTHOR_ID)
        );

        assertEquals("POST_NOT_FOUND", exception.getCode());
        verify(postRepository).findById("nonexistent");
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    @DisplayName("publishNow - 상태 변경 없이 visibility=PUBLIC만 설정")
    void publishNow_setsVisibilityPublicAlways() {
        post.setStatus(PostStatus.VOTING);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.publishNow(POST_ID, AUTHOR_ID);

        verify(postRepository).save(any(Post.class));
        assertEquals(PostVisibility.PUBLIC, post.getVisibility());
        assertNull(post.getVoteCloseAt());
        assertEquals(PostStatus.VOTING, post.getStatus());
    }
}
