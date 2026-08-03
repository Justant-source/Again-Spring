package com.againspring.service.community;

import com.againspring.common.exception.BusinessException;
import com.againspring.domain.community.Post;
import com.againspring.domain.enums.PostStatus;
import com.againspring.domain.enums.PostVisibility;
import com.againspring.domain.enums.PublishMode;
import com.againspring.repository.community.JurorRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.VoteOptionRepository;
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
    private JurorRepository jurorRepository;

    @Mock
    private VoteOptionRepository voteOptionRepository;

    @Mock
    private JuryService juryService;

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
    }

    @Test
    @DisplayName("submitPartnerAnswer - WAIT_FOR_PARTNER여도 visibility/voteCloseAt 변경 없음 (공개 게이트 아님)")
    void submitPartnerAnswer_waitForPartner_doesNotGatePublic() {
        post.setPublishMode(PublishMode.WAIT_FOR_PARTNER);
        post.setVisibility(PostVisibility.PRIVATE);
        post.setVoteCloseAt(null);
        post.setVoteDurationHours(24);
        String partnerBodyRaw = "파트너의 답변";

        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.submitPartnerAnswer(INVITE_TOKEN, PARTNER_ID, partnerBodyRaw, null);

        verify(postRepository).save(any(Post.class));
        assertEquals(PostVisibility.PRIVATE, post.getVisibility());
        assertNull(post.getVoteCloseAt());
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
        Instant existingClose = Instant.now().plusSeconds(72L * 3600);
        post.setPublishMode(PublishMode.WAIT_FOR_PARTNER);
        post.setVisibility(PostVisibility.PUBLIC);
        post.setVoteCloseAt(existingClose);
        String partnerBodyRaw = "파트너의 답변";

        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.submitPartnerAnswer(INVITE_TOKEN, PARTNER_ID, partnerBodyRaw, null);

        assertEquals(PostVisibility.PUBLIC, post.getVisibility());
        assertEquals(existingClose, post.getVoteCloseAt());
        assertEquals(partnerBodyRaw, post.getPartnerBodyPublished());
        verify(aiUserOutboxWriter, never()).postPublished(any());
    }

    @Test
    @DisplayName("submitPartnerAnswer - 이미 파트너 답변 있으면 PARTNER_ALREADY_ANSWERED 예외")
    void submitPartnerAnswer_alreadyAnswered_throws() {
        post.setPartnerAnsweredAt(Instant.now());
        String partnerBodyRaw = "파트너의 답변";

        when(postRepository.findByInviteToken(INVITE_TOKEN)).thenReturn(Optional.of(post));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> postInviteService.submitPartnerAnswer(INVITE_TOKEN, PARTNER_ID, partnerBodyRaw, null)
        );

        assertEquals("PARTNER_ALREADY_ANSWERED", exception.getCode());
        verifyNoMoreInteractions(postRepository);
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
    @DisplayName("setPublishMode - WAIT_FOR_PARTNER ≈ PUBLISH_NOW: 즉시 PUBLIC + voteCloseAt")
    void setPublishMode_waitForPartner_appliesImmediatePublic() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        Instant before = Instant.now();
        postInviteService.setPublishMode(POST_ID, AUTHOR_ID, "WAIT_FOR_PARTNER", 24);

        assertEquals(PublishMode.WAIT_FOR_PARTNER, post.getPublishMode());
        assertEquals(24, post.getVoteDurationHours());
        assertEquals(PostVisibility.PUBLIC, post.getVisibility());
        assertNotNull(post.getVoteCloseAt());
        long expected = before.plusSeconds(24L * 3600).getEpochSecond();
        assertTrue(Math.abs(post.getVoteCloseAt().getEpochSecond() - expected) <= 1);
        verify(aiUserOutboxWriter).postPublished(post);
    }

    @Test
    @DisplayName("setPublishMode - PUBLISH_NOW도 즉시 PUBLIC + voteCloseAt")
    void setPublishMode_publishNow_appliesImmediatePublic() {
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.setPublishMode(POST_ID, AUTHOR_ID, "PUBLISH_NOW", 72);

        assertEquals(PublishMode.PUBLISH_NOW, post.getPublishMode());
        assertEquals(PostVisibility.PUBLIC, post.getVisibility());
        assertNotNull(post.getVoteCloseAt());
        verify(aiUserOutboxWriter).postPublished(post);
    }

    @Test
    @DisplayName("setPublishMode - 이미 PUBLIC이면 postPublished 미호출, voteCloseAt 유지")
    void setPublishMode_alreadyPublic_noRepublishEvent() {
        Instant existingClose = Instant.now().plusSeconds(48L * 3600);
        post.setVisibility(PostVisibility.PUBLIC);
        post.setVoteCloseAt(existingClose);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.setPublishMode(POST_ID, AUTHOR_ID, "WAIT_FOR_PARTNER", 24);

        assertEquals(PostVisibility.PUBLIC, post.getVisibility());
        assertEquals(existingClose, post.getVoteCloseAt());
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
    @DisplayName("publishNow - visibility=PUBLIC, voteCloseAt 설정")
    void publishNow_setsVisibilityPublic() {
        post.setStatus(PostStatus.DRAFT);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.publishNow(POST_ID, AUTHOR_ID);

        verify(postRepository).save(any(Post.class));
        assertEquals(PostVisibility.PUBLIC, post.getVisibility());
        assertNotNull(post.getVoteCloseAt());
    }

    @Test
    @DisplayName("publishNow - voteDurationHours=24일 때 voteCloseAt = now + 24h 근사")
    void publishNow_withVoteDurationHours_usesCorrectDuration() {
        post.setVoteDurationHours(24);
        Instant beforePublish = Instant.now();

        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.publishNow(POST_ID, AUTHOR_ID);

        verify(postRepository).save(any(Post.class));
        assertNotNull(post.getVoteCloseAt());
        long expectedSeconds = beforePublish.plusSeconds(24L * 3600).getEpochSecond();
        long actualSeconds = post.getVoteCloseAt().getEpochSecond();
        assertTrue(Math.abs(actualSeconds - expectedSeconds) <= 1,
                "voteCloseAt should be approximately 24 hours from now");
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
        verifyNoMoreInteractions(postRepository);
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
    @DisplayName("publishNow - 상태 변경 없이 visibility/voteCloseAt 설정")
    void publishNow_setsVisibilityAndVoteCloseAtAlways() {
        post.setStatus(PostStatus.VOTING);
        when(postRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postInviteService.publishNow(POST_ID, AUTHOR_ID);

        verify(postRepository).save(any(Post.class));
        assertEquals(PostVisibility.PUBLIC, post.getVisibility());
        assertNotNull(post.getVoteCloseAt());
    }
}
