package com.againspring.marketing;

import com.againspring.domain.community.Post;
import com.againspring.domain.community.PostComment;
import com.againspring.domain.community.VoteOption;
import com.againspring.domain.marketing.MarketingJob;
import com.againspring.marketing.AsmProperties;
import com.againspring.marketing.dto.AsmJobView;
import com.againspring.marketing.dto.CreateJobRequest;
import com.againspring.marketing.dto.CreateJobResponse;
import com.againspring.marketing.dto.JobCallbackPayload;
import com.againspring.domain.User;
import com.againspring.repository.UserRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.VoteOptionRepository;
import com.againspring.repository.marketing.MarketingJobRepository;
import com.againspring.service.community.CommentService;
import com.againspring.service.community.SibomPlanItem;
import com.againspring.service.community.VideoVariantService;
import com.againspring.service.community.VoteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;
import com.fasterxml.jackson.core.JsonProcessingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MarketingJobService}.
 * Tests job creation with idempotency, status application, and callback handling.
 */
@ExtendWith(MockitoExtension.class)
class MarketingJobServiceTest {

    @Mock
    MarketingJobRepository marketingJobRepository;

    @Mock
    AsmClient asmClient;

    @Mock
    PostRepository postRepository;

    @Mock
    ObjectMapper objectMapper;

    @Mock
    AsmProperties asmProperties;

    @Mock
    VoteService voteService;

    @Mock
    CommentService commentService;

    @Mock
    VoteOptionRepository voteOptionRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    VideoVariantService videoVariantService;

    @Mock
    com.againspring.notification.TelegramNotifier telegramNotifier;

    @Mock
    com.againspring.marketing.MarketingLlmAuthGuard llmAuthGuard;

    @Mock
    MarketingPublishSlotService marketingPublishSlotService;

    @InjectMocks
    MarketingJobService marketingJobService;

    private static final String TEST_POST_ID = "post-123";
    private static final String TEST_JOB_ID = "remote-job-456";
    private static final List<String> TEST_TARGETS = Arrays.asList("twitter", "threads");

    /** Assigns a local id on first save so utm_campaign=story_{id} can be built. */
    private void stubSaveAssignsId(long id) {
        when(marketingJobRepository.save(any(MarketingJob.class))).thenAnswer(inv -> {
            MarketingJob job = inv.getArgument(0);
            if (job.getId() == null) {
                job.setId(id);
            }
            return job;
        });
    }

    // ── Test 1: createJob_success ───────────────────────────────────────────

    @Test
    void createJob_success_createsJobWithIdempotencyKey() throws JsonProcessingException {
        // Given
        Post post = Post.builder()
            .id(TEST_POST_ID)
            .title("Test Conflict")
            .bodyPublished("This is a test conflict scenario")
            .build();

        when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(post));
        when(asmProperties.isEnabled()).thenReturn(true);

        // Idempotency check: no active job for requested platforms
        when(marketingJobRepository.countActivePlatformJobs(eq(TEST_POST_ID), eq("twitter")))
            .thenReturn(0L);
        when(marketingJobRepository.countActivePlatformJobs(eq(TEST_POST_ID), eq("threads")))
            .thenReturn(0L);

        // Mock community data services
        when(voteService.getVoteResult(any())).thenReturn(Map.of());
        when(voteOptionRepository.findByPostIdOrderByOrderIdx(any())).thenReturn(List.of());
        when(commentService.getTopLevelComments(any())).thenReturn(List.of());

        CreateJobResponse response = CreateJobResponse.builder()
            .jobId(TEST_JOB_ID)
            .status("QUEUED")
            .build();

        when(asmClient.createJob(any(CreateJobRequest.class), any(String.class)))
            .thenReturn(response);

        stubSaveAssignsId(1L);

        doReturn("[]").when(objectMapper).writeValueAsString(any());
        when(asmProperties.getCallbackBaseUrl()).thenReturn("http://localhost:8080");
        Instant slot = Instant.parse("2026-08-16T11:30:00Z");
        when(marketingPublishSlotService.nextSlotForTargets(any(), any())).thenReturn(slot);

        // When
        MarketingJob result = marketingJobService.createJob(
            TEST_POST_ID,
            TEST_TARGETS,
            false,
            "admin"
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getRemoteJobId()).isEqualTo(TEST_JOB_ID);
        assertThat(result.getPostId()).isEqualTo(TEST_POST_ID);
        assertThat(result.getStatus()).isEqualTo("QUEUED");
        assertThat(result.getScheduledPublishAt()).isEqualTo(slot);

        verify(postRepository).findById(TEST_POST_ID);
        verify(marketingJobRepository).countActivePlatformJobs(TEST_POST_ID, "twitter");
        verify(marketingJobRepository).countActivePlatformJobs(TEST_POST_ID, "threads");
        verify(asmClient).createJob(any(CreateJobRequest.class), any(String.class));
        verify(marketingJobRepository, times(2)).save(any(MarketingJob.class));
    }

    @Test
    void createJob_autoPublish_passesThroughToAsmWithoutLocalSlot() throws JsonProcessingException {
        Post post = Post.builder()
            .id(TEST_POST_ID)
            .title("Evening slot")
            .bodyPublished("body")
            .build();

        when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(post));
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.countActivePlatformJobs(eq(TEST_POST_ID), eq("x_thread")))
            .thenReturn(0L);
        when(voteService.getVoteResult(any())).thenReturn(Map.of());
        when(voteOptionRepository.findByPostIdOrderByOrderIdx(any())).thenReturn(List.of());
        when(commentService.getTopLevelComments(any())).thenReturn(List.of());
        when(asmProperties.getCallbackBaseUrl()).thenReturn("http://localhost:8080");
        doReturn("[]").when(objectMapper).writeValueAsString(any());

        when(asmClient.createJob(any(CreateJobRequest.class), any(String.class)))
            .thenReturn(CreateJobResponse.builder().jobId(TEST_JOB_ID).status("QUEUED").build());
        stubSaveAssignsId(10L);

        MarketingJob result = marketingJobService.createJob(
            TEST_POST_ID, List.of("x_thread"), true, "system:holding-commit-trigger");

        assertThat(result.getAutoPublish()).isTrue();
        assertThat(result.getScheduledPublishAt()).isNull();
        assertThat(result.getOriginalScheduledAt()).isNull();

        ArgumentCaptor<CreateJobRequest> captor = ArgumentCaptor.forClass(CreateJobRequest.class);
        verify(asmClient).createJob(captor.capture(), any(String.class));
        assertThat(captor.getValue().getOptions().isAutoPublish()).isTrue();
    }

    @Test
    void createJob_pairedPost_includesPartnerCaptureHints() throws JsonProcessingException {
        Instant answered = Instant.parse("2026-08-04T10:00:00Z");
        String partnerBody = String.join("\n",
                "상대 문장1", "상대 문장2", "상대 문장3", "상대 문장4",
                "상대 문장5", "상대 문장6", "상대 문장7", "상대 문장8",
                "상대 문장9", "상대 문장10", "상대 문장11", "상대 문장12",
                "상대 문장13", "상대 문장14", "상대 문장15");
        Post post = Post.builder()
            .id(TEST_POST_ID)
            .title("양면 사연")
            .bodyPublished("작성자 짧은 본문입니다.")
            .partnerBodyPublished(partnerBody)
            .partnerAnsweredAt(answered)
            .build();

        when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(post));
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.countActivePlatformJobs(eq(TEST_POST_ID), eq("x_thread")))
            .thenReturn(0L);
        when(voteService.getVoteResult(any())).thenReturn(Map.of());
        when(voteOptionRepository.findByPostIdOrderByOrderIdx(any())).thenReturn(List.of());
        when(commentService.getTopLevelComments(any())).thenReturn(List.of());

        CreateJobResponse response = CreateJobResponse.builder()
            .jobId(TEST_JOB_ID)
            .status("QUEUED")
            .build();
        when(asmClient.createJob(any(CreateJobRequest.class), any(String.class)))
            .thenReturn(response);
        stubSaveAssignsId(7L);
        doReturn("[]").when(objectMapper).writeValueAsString(any());
        when(asmProperties.getCallbackBaseUrl()).thenReturn("http://localhost:8080");

        marketingJobService.createJob(TEST_POST_ID, List.of("x_thread"), false, "admin");

        ArgumentCaptor<CreateJobRequest> captor = ArgumentCaptor.forClass(CreateJobRequest.class);
        verify(asmClient).createJob(captor.capture(), any(String.class));
        CreateJobRequest.BriefDto brief = captor.getValue().getBrief();
        assertThat(brief.getHasPartnerStory()).isTrue();
        assertThat(brief.getPartnerCaptureSplitAfterLines()).isNotNull();
        assertThat(brief.getPartnerCaptureSplitAfterLines()).isNotEmpty();
        assertThat(brief.getPartnerPartHeightsCss()).isNotNull();
        assertThat(brief.getPartnerCaptureBlockCount()).isGreaterThan(0);
        assertThat(brief.getAuthorBody()).isEqualTo("작성자 짧은 본문입니다.");
        assertThat(brief.getPartnerBody()).isEqualTo(partnerBody);
    }

    @Test
    void createJob_soloPost_hasPartnerStoryFalse() throws JsonProcessingException {
        Post post = Post.builder()
            .id(TEST_POST_ID)
            .title("솔로")
            .bodyPublished("작성자만")
            .build();

        when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(post));
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.countActivePlatformJobs(eq(TEST_POST_ID), eq("x_thread")))
            .thenReturn(0L);
        when(voteService.getVoteResult(any())).thenReturn(Map.of());
        when(voteOptionRepository.findByPostIdOrderByOrderIdx(any())).thenReturn(List.of());
        when(commentService.getTopLevelComments(any())).thenReturn(List.of());

        CreateJobResponse response = CreateJobResponse.builder()
            .jobId(TEST_JOB_ID)
            .status("QUEUED")
            .build();
        when(asmClient.createJob(any(CreateJobRequest.class), any(String.class)))
            .thenReturn(response);
        stubSaveAssignsId(8L);
        doReturn("[]").when(objectMapper).writeValueAsString(any());
        when(asmProperties.getCallbackBaseUrl()).thenReturn("http://localhost:8080");

        marketingJobService.createJob(TEST_POST_ID, List.of("x_thread"), false, "admin");

        ArgumentCaptor<CreateJobRequest> captor = ArgumentCaptor.forClass(CreateJobRequest.class);
        verify(asmClient).createJob(captor.capture(), any(String.class));
        assertThat(captor.getValue().getBrief().getHasPartnerStory()).isFalse();
        assertThat(captor.getValue().getBrief().getPartnerCaptureSplitAfterLines()).isNull();
        assertThat(captor.getValue().getBrief().getPartnerPartHeightsCss()).isNull();
        assertThat(captor.getValue().getBrief().getAuthorBody()).isEqualTo("작성자만");
        assertThat(captor.getValue().getBrief().getPartnerBody()).isNull();
    }

    @Test
    void createJob_enrichesTopCommentsWithFullBodyTop3ByLikeCount() throws JsonProcessingException {
        String longPostBody = "가".repeat(400); // longer than side_a's 300-char cap
        String longCommentBody = "나".repeat(150); // longer than the old 100-char comment cap
        Instant commentCreatedAt = Instant.parse("2026-08-05T09:00:00Z");
        Post post = Post.builder()
            .id(TEST_POST_ID)
            .title("Test Conflict")
            .bodyPublished(longPostBody)
            .authorId("post-author")
            .partnerUserId("post-partner")
            .build();

        PostComment low = PostComment.builder().authorId("user-low").body("낮은 좋아요 댓글").likeCount(1).build();
        PostComment high = PostComment.builder().authorId("post-author").body(longCommentBody).likeCount(10)
            .createdAt(commentCreatedAt).build();
        PostComment mid = PostComment.builder().authorId("post-partner").body("중간 좋아요 댓글").likeCount(5).build();
        PostComment lowest = PostComment.builder().authorId("user-lowest").body("최하위 댓글").likeCount(0).build();

        when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(post));
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.countActivePlatformJobs(eq(TEST_POST_ID), eq("x_thread")))
            .thenReturn(0L);
        when(voteService.getVoteResult(any())).thenReturn(Map.of());
        when(voteOptionRepository.findByPostIdOrderByOrderIdx(any())).thenReturn(List.of());
        when(commentService.getTopLevelComments(TEST_POST_ID)).thenReturn(List.of(low, high, mid, lowest));
        when(userRepository.findById("post-author"))
            .thenReturn(Optional.of(User.builder().id("post-author").nickname("작성자닉").build()));
        when(userRepository.findById("post-partner"))
            .thenReturn(Optional.of(User.builder().id("post-partner").nickname("상대방닉").build()));

        CreateJobResponse response = CreateJobResponse.builder().jobId(TEST_JOB_ID).status("QUEUED").build();
        when(asmClient.createJob(any(CreateJobRequest.class), any(String.class))).thenReturn(response);
        stubSaveAssignsId(9L);
        doReturn("[]").when(objectMapper).writeValueAsString(any());
        when(asmProperties.getCallbackBaseUrl()).thenReturn("http://localhost:8080");

        marketingJobService.createJob(TEST_POST_ID, List.of("x_thread"), false, "admin");

        ArgumentCaptor<CreateJobRequest> captor = ArgumentCaptor.forClass(CreateJobRequest.class);
        verify(asmClient).createJob(captor.capture(), any(String.class));
        CreateJobRequest.BriefDto brief = captor.getValue().getBrief();

        // authorBody must not be truncated (unlike side_a's 300-char cap)
        assertThat(brief.getAuthorBody()).isEqualTo(longPostBody);
        assertThat(brief.getSideA()).hasSize(300);

        // top 2 by likeCount desc, full body (no 100-char truncation)
        assertThat(brief.getTopComments()).hasSize(2);

        CreateJobRequest.TopCommentDto topComment = brief.getTopComments().get(0);
        assertThat(topComment.getBody()).isEqualTo(longCommentBody);
        assertThat(topComment.getLikeCount()).isEqualTo(10);
        // author = resolved nickname, not raw authorId
        assertThat(topComment.getAuthor()).isEqualTo("작성자닉");
        assertThat(topComment.getAuthorId()).isEqualTo("post-author");
        assertThat(topComment.getCreatedAt()).isEqualTo(commentCreatedAt);
        assertThat(topComment.getSide()).isEqualTo("author");

        CreateJobRequest.TopCommentDto secondComment = brief.getTopComments().get(1);
        assertThat(secondComment.getLikeCount()).isEqualTo(5);
        assertThat(secondComment.getAuthor()).isEqualTo("상대방닉");
        assertThat(secondComment.getSide()).isEqualTo("partner");

    }

    @Test
    void createJob_normalizesJsonEscapedTopCommentsBeforeSendingToAsm() throws Exception {
        Post post = Post.builder()
            .id(TEST_POST_ID)
            .title("제목")
            .bodyPublished("본문")
            .build();
        String escapedBody = "\\u" + "c774\\u" + "b7f0 \\u" + "b313\\uae00\\\\\\n\\ub2e4\\uc74c \\uc904";
        String escapedNickname = "\\u" + "b2c9\\ub124\\uc784";
        PostComment comment = PostComment.builder()
            .authorId("commenter")
            .body(escapedBody)
            .likeCount(1)
            .build();

        when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(post));
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.countActivePlatformJobs(TEST_POST_ID, "x_thread")).thenReturn(0L);
        when(voteService.getVoteResult(TEST_POST_ID)).thenReturn(Map.of());
        when(voteOptionRepository.findByPostIdOrderByOrderIdx(TEST_POST_ID)).thenReturn(List.of());
        when(commentService.getTopLevelComments(TEST_POST_ID)).thenReturn(List.of(comment));
        when(userRepository.findById("commenter"))
            .thenReturn(Optional.of(User.builder().id("commenter").nickname(escapedNickname).build()));
        when(asmClient.createJob(any(CreateJobRequest.class), any(String.class)))
            .thenReturn(CreateJobResponse.builder().jobId(TEST_JOB_ID).status("QUEUED").build());
        stubSaveAssignsId(1L);
        doReturn("[]").when(objectMapper).writeValueAsString(any());
        when(asmProperties.getCallbackBaseUrl()).thenReturn("http://localhost:8080");

        marketingJobService.createJob(TEST_POST_ID, List.of("x_thread"), false, "admin");

        ArgumentCaptor<CreateJobRequest> captor = ArgumentCaptor.forClass(CreateJobRequest.class);
        verify(asmClient).createJob(captor.capture(), any(String.class));

        ObjectMapper outboundMapper = new ObjectMapper();
        String outboundJson = outboundMapper.writeValueAsString(captor.getValue());
        var topComment = outboundMapper.readTree(outboundJson)
            .path("brief").path("top_comments").get(0);
        assertThat(topComment.path("author").asText()).isEqualTo("닉네임");
        assertThat(topComment.path("body").asText()).isEqualTo("이런 댓글\n다음 줄");
        assertThat(outboundJson).doesNotContain("\\\\u");
        assertThat(outboundJson).doesNotContain("\\\\\\n");
    }


    @Test
    void createJob_xThread_attachesUtmToPostUrlAndOptions() throws JsonProcessingException {
        Post post = Post.builder()
            .id(TEST_POST_ID)
            .title("UTM")
            .bodyPublished("body")
            .build();
        when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(post));
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.countActivePlatformJobs(eq(TEST_POST_ID), eq("x_thread")))
            .thenReturn(0L);
        when(voteService.getVoteResult(any())).thenReturn(Map.of());
        when(voteOptionRepository.findByPostIdOrderByOrderIdx(any())).thenReturn(List.of());
        when(commentService.getTopLevelComments(any())).thenReturn(List.of());
        when(asmClient.createJob(any(CreateJobRequest.class), any(String.class)))
            .thenReturn(CreateJobResponse.builder().jobId(TEST_JOB_ID).status("QUEUED").build());
        stubSaveAssignsId(42L);
        doReturn("[]").when(objectMapper).writeValueAsString(any());
        when(asmProperties.getCallbackBaseUrl()).thenReturn("http://localhost:8080");

        marketingJobService.createJob(TEST_POST_ID, List.of("x_thread"), false, "admin");

        ArgumentCaptor<CreateJobRequest> captor = ArgumentCaptor.forClass(CreateJobRequest.class);
        verify(asmClient).createJob(captor.capture(), any(String.class));
        CreateJobRequest req = captor.getValue();
        String expected = MarketingUtmUrls.buildUrl(TEST_POST_ID, "x", "story_42");
        assertThat(req.getBrief().getPostUrl()).isEqualTo(expected);
        assertThat(req.getOptions().getUtmCampaign()).isEqualTo("story_42");
        assertThat(req.getOptions().getPostUrls()).containsEntry("x_thread", expected);
    }

    @Test
    void createJob_multiTargetVideo_setsPerPlatformPostUrls_primaryYoutube() throws JsonProcessingException {
        Post post = Post.builder()
            .id(TEST_POST_ID)
            .title("dual")
            .bodyPublished("body")
            .build();
        when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(post));
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.countActivePlatformJobs(eq(TEST_POST_ID), eq("instagram_reels")))
            .thenReturn(0L);
        when(marketingJobRepository.countActivePlatformJobs(eq(TEST_POST_ID), eq("youtube_shorts")))
            .thenReturn(0L);
        when(voteService.getVoteResult(any())).thenReturn(Map.of());
        when(voteOptionRepository.findByPostIdOrderByOrderIdx(any())).thenReturn(List.of());
        when(commentService.getTopLevelComments(any())).thenReturn(List.of());
        when(asmClient.createJob(any(CreateJobRequest.class), any(String.class)))
            .thenReturn(CreateJobResponse.builder().jobId(TEST_JOB_ID).status("QUEUED").build());
        stubSaveAssignsId(11L);
        doReturn("[]").when(objectMapper).writeValueAsString(any());
        when(asmProperties.getCallbackBaseUrl()).thenReturn("http://localhost:8080");
        when(videoVariantService.generate(any(), any(), any(), any(), eq(true), eq(true), any()))
            .thenReturn(new VideoVariantService.Variants(
                "릴스훅", "릴스대본 공감 비율은?", 30,
                "쇼츠훅", "쇼츠대본 댓글로", 45,
                List.of(plan("side-glance"), plan("stunned"), plan("drained"), plan("indignant")),
                List.of(plan("side-glance"), plan("stunned"), plan("drained"), plan("indignant"), plan("relieved"))
            ));

        marketingJobService.createJob(
            TEST_POST_ID,
            List.of("instagram_reels", "youtube_shorts"),
            false,
            "admin"
        );

        ArgumentCaptor<CreateJobRequest> captor = ArgumentCaptor.forClass(CreateJobRequest.class);
        verify(asmClient).createJob(captor.capture(), any(String.class));
        CreateJobRequest req = captor.getValue();
        Map<String, String> urls = req.getOptions().getPostUrls();
        assertThat(urls.get("youtube_shorts")).contains("utm_source=youtube");
        assertThat(urls.get("instagram_reels")).contains("utm_source=instagram");
        assertThat(req.getBrief().getPostUrl()).isEqualTo(urls.get("youtube_shorts"));
        assertThat(req.getOptions().getUtmCampaign()).isEqualTo("story_11");
        assertThat(req.getOptions().getPriority()).isEqualTo("MARKETING_CRITICAL");
        assertThat(req.getOptions().isPreScripted()).isTrue();
        assertThat(req.getOptions().getRenderProfile()).isEqualTo("marketing_fast");
        assertThat(req.getOptions().getDeadlineAt()).isNotBlank();
        var schedulingOptions = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(req))
            .path("options");
        assertThat(schedulingOptions.path("priority").asText()).isEqualTo("MARKETING_CRITICAL");
        assertThat(schedulingOptions.path("pre_scripted").asBoolean()).isTrue();
        assertThat(schedulingOptions.path("render_profile").asText()).isEqualTo("marketing_fast");
        assertThat(schedulingOptions.path("deadline_at").asText()).isNotBlank();
        assertThat(req.getBrief().getHookReels()).isEqualTo("릴스훅");
        assertThat(req.getBrief().getHookShorts()).isEqualTo("쇼츠훅");
        assertThat(req.getBrief().getScriptReels()).contains("공감");
        assertThat(req.getBrief().getScriptShorts()).contains("댓글");
        assertThat(req.getBrief().getMaxDurationReelsSec()).isEqualTo(30);
        assertThat(req.getBrief().getMaxDurationShortsSec()).isEqualTo(45);
        assertThat(req.getBrief().getMaxDurationSec()).isNull(); // dual → per-platform fields
        assertThat(req.getBrief().getMetaphorId()).isNull();
        assertThat(req.getBrief().getMetaphorIds()).isNull();
        assertThat(req.getBrief().getSibomPlan()).isNull(); // dual → channel fields only
        assertThat(req.getBrief().getSibomPlanReels()).hasSize(4);
        assertThat(req.getBrief().getSibomPlanShorts()).hasSize(5);
        verify(videoVariantService).generate(any(), any(), any(), any(), eq(true), eq(true), any());
    }

    @Test
    void createJob_shortsAlone_wiresSibomCandidatesAndActivePlan_omitsMetaphor() throws JsonProcessingException {
        Post post = Post.builder()
            .id(TEST_POST_ID)
            .title("shorts")
            .bodyPublished("body")
            .metaphorId("empty-chair")
            .metaphorIds(List.of("empty-chair", "tangled-thread"))
            .sibomCandidates(List.of("waiting-reply", "drained"))
            .build();
        when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(post));
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.countActivePlatformJobs(eq(TEST_POST_ID), eq("youtube_shorts")))
            .thenReturn(0L);
        when(voteService.getVoteResult(any())).thenReturn(Map.of());
        when(voteOptionRepository.findByPostIdOrderByOrderIdx(any())).thenReturn(List.of());
        when(commentService.getTopLevelComments(any())).thenReturn(List.of());
        when(asmClient.createJob(any(CreateJobRequest.class), any(String.class)))
            .thenReturn(CreateJobResponse.builder().jobId(TEST_JOB_ID).status("QUEUED").build());
        stubSaveAssignsId(13L);
        doReturn("[]").when(objectMapper).writeValueAsString(any());
        when(asmProperties.getCallbackBaseUrl()).thenReturn("http://localhost:8080");

        when(videoVariantService.generate(any(), any(), any(), any(), eq(false), eq(true), any()))
            .thenReturn(new VideoVariantService.Variants(
                null, null, null,
                "쇼츠훅", "쇼츠대본 댓글", 45,
                List.of(), List.of(plan("waiting-reply"), plan("drained"), plan("stunned"), plan("indignant"), plan("relieved"))
            ));

        marketingJobService.createJob(TEST_POST_ID, List.of("youtube_shorts"), false, "admin");

        ArgumentCaptor<CreateJobRequest> captor = ArgumentCaptor.forClass(CreateJobRequest.class);
        verify(asmClient).createJob(captor.capture(), any(String.class));
        CreateJobRequest.BriefDto brief = captor.getValue().getBrief();
        assertThat(brief.getMetaphorId()).isNull();
        assertThat(brief.getMetaphorIds()).isNull();
        assertThat(brief.getSibomCandidates()).containsExactly("waiting-reply", "drained");
        assertThat(brief.getSibomPlan()).hasSize(5);
        assertThat(brief.getSibomPlan().get(0).getImageId()).isEqualTo("waiting-reply");
        assertThat(brief.getSibomPlan().get(0).getRole()).isEqualTo("punch");
        assertThat(brief.getSibomPlanShorts()).hasSize(5);
        assertThat(brief.getSibomPlanReels()).isNull();
        assertThat(brief.getMaxDurationSec()).isEqualTo(45);
        verify(videoVariantService).generate(
            any(), any(), any(), any(), eq(false), eq(true), eq(List.of("waiting-reply", "drained")));
    }

    @Test
    void createJob_videoWithEmptySibomPlan_persistsFailureAndDoesNotCallAsm() throws JsonProcessingException {
        Post post = Post.builder().id(TEST_POST_ID).title("video").bodyPublished("body").build();
        when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(post));
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.countActivePlatformJobs(TEST_POST_ID, "instagram_reels")).thenReturn(0L);
        when(voteService.getVoteResult(any())).thenReturn(Map.of());
        when(voteOptionRepository.findByPostIdOrderByOrderIdx(any())).thenReturn(List.of());
        when(commentService.getTopLevelComments(any())).thenReturn(List.of());
        stubSaveAssignsId(77L);
        doReturn("{\"reels_guarded_plan_count\":0}").when(objectMapper).writeValueAsString(any());
        when(videoVariantService.generate(any(), any(), any(), any(), eq(true), eq(false), any()))
            .thenReturn(new VideoVariantService.Variants("h", "s", 30, null, null, null, List.of(), List.of()));

        MarketingJob result = marketingJobService.createJob(
            TEST_POST_ID, List.of("instagram_reels"), true, "system");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getFailureCode()).isEqualTo("SIBOM_PLAN_EMPTY");
        assertThat(result.getGenerationDiagnostics()).contains("reels_guarded_plan_count");
        verify(asmClient, never()).createJob(any(CreateJobRequest.class), anyString());
    }

    private static SibomPlanItem plan(String imageId) {
        return new SibomPlanItem("punch", imageId, "", 0, "small", "punch");
    }

    @Test
    void createJob_xThread_skipsVideoVariants() throws JsonProcessingException {
        Post post = Post.builder()
            .id(TEST_POST_ID)
            .title("text only")
            .bodyPublished("body")
            .build();
        when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(post));
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.countActivePlatformJobs(eq(TEST_POST_ID), eq("x_thread")))
            .thenReturn(0L);
        when(voteService.getVoteResult(any())).thenReturn(Map.of());
        when(voteOptionRepository.findByPostIdOrderByOrderIdx(any())).thenReturn(List.of());
        when(commentService.getTopLevelComments(any())).thenReturn(List.of());
        when(asmClient.createJob(any(CreateJobRequest.class), any(String.class)))
            .thenReturn(CreateJobResponse.builder().jobId(TEST_JOB_ID).status("QUEUED").build());
        stubSaveAssignsId(12L);
        doReturn("[]").when(objectMapper).writeValueAsString(any());
        when(asmProperties.getCallbackBaseUrl()).thenReturn("http://localhost:8080");

        marketingJobService.createJob(TEST_POST_ID, List.of("x_thread"), false, "admin");

        verify(videoVariantService, never()).generate(
            any(), any(), any(), any(), any(Boolean.class), any(Boolean.class), any());
        ArgumentCaptor<CreateJobRequest> captor = ArgumentCaptor.forClass(CreateJobRequest.class);
        verify(asmClient).createJob(captor.capture(), any(String.class));
        assertThat(captor.getValue().getBrief().getHookReels()).isNull();
        assertThat(captor.getValue().getBrief().getScriptShorts()).isNull();
        assertThat(captor.getValue().getBrief().getMetaphorId()).isNull();
        assertThat(captor.getValue().getBrief().getSibomPlan()).isNull();
    }

    @Test
    void createJob_duplicateActiveJob_throwsIllegalStateException() {
        // Given
        when(asmProperties.isEnabled()).thenReturn(true);
        when(marketingJobRepository.countActivePlatformJobs(eq(TEST_POST_ID), eq("twitter")))
            .thenReturn(1L);

        // When / Then
        assertThatThrownBy(() -> marketingJobService.createJob(
            TEST_POST_ID,
            TEST_TARGETS,
            false,
            "admin"
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("이미 처리 중인");

        verify(marketingJobRepository).countActivePlatformJobs(TEST_POST_ID, "twitter");
    }

    // ── Test 3: createJob_allowsNewJobAfterTerminal ─────────────────────────

    @Test
    void createJob_allowsNewJobAfterTerminal_succeeds() throws JsonProcessingException {
        // Given
        Post post = Post.builder()
            .id(TEST_POST_ID)
            .title("Test Conflict")
            .bodyPublished("Content")
            .build();

        when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(post));
        when(asmProperties.isEnabled()).thenReturn(true);

        // Terminal jobs are not counted as active
        when(marketingJobRepository.countActivePlatformJobs(eq(TEST_POST_ID), eq("twitter")))
            .thenReturn(0L);
        when(marketingJobRepository.countActivePlatformJobs(eq(TEST_POST_ID), eq("threads")))
            .thenReturn(0L);

        // Mock community data services
        when(voteService.getVoteResult(any())).thenReturn(Map.of());
        when(voteOptionRepository.findByPostIdOrderByOrderIdx(any())).thenReturn(List.of());
        when(commentService.getTopLevelComments(any())).thenReturn(List.of());

        CreateJobResponse response = CreateJobResponse.builder()
            .jobId("new-job-222")
            .status("QUEUED")
            .build();

        when(asmClient.createJob(any(CreateJobRequest.class), any(String.class)))
            .thenReturn(response);

        stubSaveAssignsId(2L);

        doReturn("[]").when(objectMapper).writeValueAsString(any());
        when(asmProperties.getCallbackBaseUrl()).thenReturn("http://localhost:8080");

        // When
        MarketingJob result = marketingJobService.createJob(
            TEST_POST_ID,
            TEST_TARGETS,
            false,
            "admin"
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getRemoteJobId()).isEqualTo("new-job-222");

        verify(marketingJobRepository, times(2)).save(any(MarketingJob.class));
    }

    // ── Test 4: applyPoll_updatesStatus ────────────────────────────────────

    @Test
    void applyPoll_updatesJobFieldsAndResetsFailCount() throws JsonProcessingException {
        // Given
        MarketingJob job = MarketingJob.builder()
            .id(1L)
            .remoteJobId(TEST_JOB_ID)
            .postId(TEST_POST_ID)
            .status("RUNNING")
            .pollFailCount(3) // had some failures
            .build();

        Map<String, Object> artifactsMap = new java.util.HashMap<>();
        artifactsMap.put("artifact1", "value1");
        artifactsMap.put("artifact2", "value2");

        AsmJobView view = AsmJobView.builder()
            .status("READY")
            .phase("completion")
            .progress(100.0)
            .artifacts(artifactsMap)
            .publications(Arrays.asList())
            .build();

        doReturn("[]").when(objectMapper).writeValueAsString(any());
        when(marketingJobRepository.save(any(MarketingJob.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        marketingJobService.applyPoll(job, view);

        // Then
        assertThat(job.getStatus()).isEqualTo("READY");
        assertThat(job.getPhase()).isEqualTo("completion");
        assertThat(job.getProgress()).isEqualTo(100.0);
        assertThat(job.getPollFailCount()).isZero();

        verify(marketingJobRepository).save(job);
    }

    @Test
    void applyPoll_preservesStructuredRendererFailureContract() throws JsonProcessingException {
        MarketingJob job = MarketingJob.builder()
            .id(1L).remoteJobId(TEST_JOB_ID).postId(TEST_POST_ID).status("RUNNING").build();
        AsmJobView view = AsmJobView.builder()
            .status("FAILED")
            .failureCode("DURATION_RENDER_EXCEEDED")
            .failureStage("RENDER")
            .retryable(true)
            .errorSummary("최종 렌더 길이가 플랫폼 상한을 초과했습니다.")
            .diagnostics(Map.of("final_duration_ms", 48100))
            .actualDurationMs(48100L)
            .build();
        doReturn("{}").when(objectMapper).writeValueAsString(any());
        when(marketingJobRepository.save(any(MarketingJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        marketingJobService.applyPoll(job, view);

        assertThat(job.getFailureCode()).isEqualTo("DURATION_RENDER_EXCEEDED");
        assertThat(job.getFailureStage()).isEqualTo("RENDER");
        assertThat(job.getRetryable()).isTrue();
        assertThat(job.getErrorSummary()).contains("플랫폼 상한");
        assertThat(job.getActualDurationMs()).isEqualTo(48100L);
    }

    @Test
    void applyPoll_toFailed_alertsOnceWithPublicationError() throws JsonProcessingException {
        MarketingJob job = MarketingJob.builder()
            .id(777L)
            .remoteJobId(TEST_JOB_ID)
            .postId(TEST_POST_ID)
            .targets("[\"x_thread\"]")
            .status("RUNNING")
            .build();

        AsmJobView view = AsmJobView.builder()
            .status("FAILED")
            .phase("CAPTURE")
            .progress(0.0)
            .publications(List.of(Map.of(
                "platform", "instagram_reels",
                "state", "FAILED",
                "error", "UPLOAD_NOT_ACCEPTED: create dialog still empty")))
            .build();

        when(marketingJobRepository.save(any(MarketingJob.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        marketingJobService.applyPoll(job, view);

        assertThat(job.getStatus()).isEqualTo("FAILED");
        assertThat(job.getErrorMessage())
            .contains("instagram_reels")
            .contains("UPLOAD_NOT_ACCEPTED");
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(telegramNotifier).send(message.capture());
        assertThat(message.getValue()).contains("예약 마케팅 게시 실패").contains("UPLOAD_NOT_ACCEPTED");

        // A repeated poll with the same terminal state must not page the operator again.
        marketingJobService.applyPoll(job, view);
        verify(telegramNotifier).send(anyString());
    }

    @Test
    void applyPoll_waggleBotProcessingTimeout_waitsForSameRemoteJobInsteadOfFailing()
            throws JsonProcessingException {
        MarketingJob job = MarketingJob.builder()
            .id(778L).remoteJobId(TEST_JOB_ID).postId(TEST_POST_ID).status("RUNNING").build();
        AsmJobView timedOut = AsmJobView.builder()
            .status("FAILED").phase("TTS 합성").progress(0.7)
            .error("WaggleBot poll timeout after 1800.0s jobId=10026593 last={status=PROCESSING}")
            .build();
        when(marketingJobRepository.save(any(MarketingJob.class))).thenAnswer(inv -> inv.getArgument(0));

        marketingJobService.applyPoll(job, timedOut);

        assertThat(job.getStatus()).isEqualTo("WAITING_EXTERNAL");
        assertThat(job.getRemoteStatus()).isEqualTo("FAILED");
        assertThat(job.getProcessingDetail()).contains("WaggleBot poll timeout");
        assertThat(job.getErrorMessage()).isNull();
        assertThat(job.getWaitingExternalSince()).isNotNull();
        verify(telegramNotifier, never()).send(anyString());

        AsmJobView ready = AsmJobView.builder().status("READY").phase("done").progress(1.0).build();
        marketingJobService.applyPoll(job, ready);

        assertThat(job.getStatus()).isEqualTo("READY");
        assertThat(job.getErrorMessage()).isNull();
        assertThat(job.getProcessingDetail()).isNull();
        assertThat(job.getWaitingExternalSince()).isNull();
    }

    @Test
    void applyPoll_missingRemoteStatus_preservesWaitingExternalState() throws JsonProcessingException {
        MarketingJob job = MarketingJob.builder()
            .id(779L).remoteJobId(TEST_JOB_ID).postId(TEST_POST_ID).status("WAITING_EXTERNAL")
            .remoteStatus("FAILED").processingDetail("WaggleBot poll timeout")
            .waitingExternalSince(Instant.now()).slaBreachedAt(Instant.now()).build();
        when(marketingJobRepository.save(any(MarketingJob.class))).thenAnswer(inv -> inv.getArgument(0));

        marketingJobService.applyPoll(job, AsmJobView.builder().phase("FFmpeg 렌더링").build());

        assertThat(job.getStatus()).isEqualTo("WAITING_EXTERNAL");
        assertThat(job.getRemoteStatus()).isEqualTo("FAILED");
        assertThat(job.getProcessingDetail()).isEqualTo("WaggleBot poll timeout");
    }

    // ── Test 5: applyCallback_updatesJobFromRemote ──────────────────────────

    @Test
    void applyCallback_updatesJobFromRemoteJobId() throws JsonProcessingException {
        // Given
        MarketingJob job = MarketingJob.builder()
            .id(1L)
            .remoteJobId(TEST_JOB_ID)
            .postId(TEST_POST_ID)
            .status("PUBLISHING")
            .build();

        Map<String, Object> pub = new java.util.LinkedHashMap<>();
        pub.put("platform", "x_thread");
        pub.put("state", "published");
        pub.put("url", "https://x.com/againspring/status/123");

        JobCallbackPayload payload = JobCallbackPayload.builder()
            .jobId(TEST_JOB_ID)
            .status("PUBLISHED")
            .event("PUBLISHED")
            .publications(List.of(pub))
            .build();

        when(marketingJobRepository.findByRemoteJobId(TEST_JOB_ID))
            .thenReturn(Optional.of(job));

        when(marketingJobRepository.save(any(MarketingJob.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        doReturn("[{\"platform\":\"x_thread\",\"state\":\"published\",\"url\":\"https://x.com/againspring/status/123\"}]")
            .when(objectMapper).writeValueAsString(any());

        // When
        marketingJobService.applyCallback(payload);

        // Then
        assertThat(job.getStatus()).isEqualTo("PUBLISHED");
        assertThat(job.getPublications()).contains("https://x.com/againspring/status/123");

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(telegramNotifier).send(message.capture());
        assertThat(message.getValue())
            .contains("예약 마케팅 게시 완료")
            .contains("https://x.com/againspring/status/123");

        verify(marketingJobRepository).findByRemoteJobId(TEST_JOB_ID);
        verify(marketingJobRepository).save(job);
    }

    @Test
    void applyCallback_publishedPairedShorts_alertsPartnerCommentAndManualPin() throws JsonProcessingException {
        MarketingJob job = MarketingJob.builder()
            .id(2L)
            .remoteJobId(TEST_JOB_ID)
            .postId(TEST_POST_ID)
            .status("PUBLISHING")
            .build();
        Map<String, Object> partnerComment = new java.util.LinkedHashMap<>();
        partnerComment.put("state", "PUBLISHED");
        partnerComment.put("comment_id", "Ugxyz");
        partnerComment.put("url", "https://www.youtube.com/watch?v=video123&lc=Ugxyz");
        Map<String, Object> publication = new java.util.LinkedHashMap<>();
        publication.put("platform", "youtube_shorts");
        publication.put("state", "PUBLISHED");
        publication.put("url", "https://www.youtube.com/shorts/video123");
        publication.put("partner_comment", partnerComment);
        JobCallbackPayload payload = JobCallbackPayload.builder()
            .jobId(TEST_JOB_ID)
            .status("PUBLISHED")
            .publications(List.of(publication))
            .build();

        when(marketingJobRepository.findByRemoteJobId(TEST_JOB_ID)).thenReturn(Optional.of(job));
        when(marketingJobRepository.save(any(MarketingJob.class))).thenAnswer(inv -> inv.getArgument(0));
        doReturn("[]").when(objectMapper).writeValueAsString(any());

        marketingJobService.applyCallback(payload);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(telegramNotifier).send(message.capture());
        assertThat(message.getValue())
            .contains("상대방 사연 댓글: https://www.youtube.com/watch?v=video123&lc=Ugxyz")
            .contains("YouTube Studio에서 댓글 고정 필요");
    }

    // ── Test 6: applyCallback_unknownJobId_noOp ────────────────────────────

    @Test
    void applyCallback_unknownJobId_doesNotThrow() {
        // Given
        JobCallbackPayload payload = JobCallbackPayload.builder()
            .jobId("unknown-job-999")
            .status("PUBLISHED")
            .event("PUBLISHED")
            .build();

        when(marketingJobRepository.findByRemoteJobId("unknown-job-999"))
            .thenReturn(Optional.empty());

        // When / Then — should not throw
        marketingJobService.applyCallback(payload);

        verify(marketingJobRepository).findByRemoteJobId("unknown-job-999");
        // No save should occur
        verify(marketingJobRepository, org.mockito.Mockito.never()).save(any(MarketingJob.class));
    }

    // ── PUBLISHED callback/poll no longer auto-enqueues youtube_shorts ────────
    // Video (Reels+Shorts) is created by XThreadPublishTriggerScheduler at 24h.

    @Test
    void applyCallback_publishedXThread_doesNotTriggerYoutubeShorts() throws JsonProcessingException {
        MarketingJob job = MarketingJob.builder()
            .id(1L)
            .remoteJobId(TEST_JOB_ID)
            .postId(TEST_POST_ID)
            .status("PUBLISHING")
            .targets("[\"x_thread\"]")
            .build();

        JobCallbackPayload payload = JobCallbackPayload.builder()
            .jobId(TEST_JOB_ID)
            .status("PUBLISHED")
            .event("PUBLISHED")
            .build();

        when(marketingJobRepository.findByRemoteJobId(TEST_JOB_ID)).thenReturn(Optional.of(job));
        when(marketingJobRepository.save(any(MarketingJob.class))).thenAnswer(inv -> inv.getArgument(0));

        marketingJobService.applyCallback(payload);

        verify(asmClient, never()).createJob(any(CreateJobRequest.class), any(String.class));
        verify(commentService, never()).getTopLevelComments(any());
        verify(marketingJobRepository, never()).countAnyPlatformJobs(any(), any());
        verify(marketingJobRepository, times(1)).save(any(MarketingJob.class));
    }

    @Test
    void applyCallback_publishedInstagramFeed_doesNotTriggerYoutubeShorts() throws JsonProcessingException {
        MarketingJob job = MarketingJob.builder()
            .id(1L)
            .remoteJobId(TEST_JOB_ID)
            .postId(TEST_POST_ID)
            .status("PUBLISHING")
            .targets("[\"instagram_feed\"]")
            .build();

        JobCallbackPayload payload = JobCallbackPayload.builder()
            .jobId(TEST_JOB_ID)
            .status("PUBLISHED")
            .event("PUBLISHED")
            .build();

        when(marketingJobRepository.findByRemoteJobId(TEST_JOB_ID)).thenReturn(Optional.of(job));
        when(marketingJobRepository.save(any(MarketingJob.class))).thenAnswer(inv -> inv.getArgument(0));

        marketingJobService.applyCallback(payload);

        verify(asmClient, never()).createJob(any(CreateJobRequest.class), any(String.class));
        verify(marketingJobRepository, times(1)).save(any(MarketingJob.class));
    }

    @Test
    void applyPoll_toPublishedOnXThread_doesNotTriggerYoutubeShorts() throws JsonProcessingException {
        MarketingJob job = MarketingJob.builder()
            .id(1L)
            .remoteJobId(TEST_JOB_ID)
            .postId(TEST_POST_ID)
            .status("PUBLISHING")
            .targets("[\"x_thread\"]")
            .build();

        AsmJobView view = AsmJobView.builder()
            .status("PUBLISHED")
            .phase("done")
            .progress(100.0)
            .artifacts(Map.of())
            .publications(List.of())
            .build();

        when(marketingJobRepository.save(any(MarketingJob.class))).thenAnswer(inv -> inv.getArgument(0));

        marketingJobService.applyPoll(job, view);

        verify(asmClient, never()).createJob(any(CreateJobRequest.class), any(String.class));
        verify(commentService, never()).getTopLevelComments(any());
        verify(marketingJobRepository, times(1)).save(any(MarketingJob.class));
    }

    /**
     * Test that failJob() sets all required fields: stage, code, retryable, errorSummary, errorMessage
     */
    @Test
    void failJobRecordsAllFailureFields() {
        MarketingJob job = MarketingJob.builder()
            .id(123L)
            .postId("post-123")
            .status("RUNNING")
            .targets("[\"youtube_shorts\"]")
            .generationAttempt(1)
            .build();

        when(marketingJobRepository.save(any(MarketingJob.class))).thenAnswer(inv -> inv.getArgument(0));

        marketingJobService.failJob(job, MarketingFailureStage.QUALITY_GATE, "SIBOM_PLAN_TOO_SHORT", false,
            "Video variant quality gate failed: only 3 items, need 4");

        ArgumentCaptor<MarketingJob> captor = ArgumentCaptor.forClass(MarketingJob.class);
        verify(marketingJobRepository).save(captor.capture());

        MarketingJob saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("FAILED");
        assertThat(saved.getFailureStage()).isEqualTo("AS:QUALITY_GATE");
        assertThat(saved.getFailureCode()).isEqualTo("SIBOM_PLAN_TOO_SHORT");
        assertThat(saved.getRetryable()).isFalse();
        assertThat(saved.getErrorMessage()).contains("quality gate failed");
        assertThat(saved.getErrorSummary()).isNotNull();
    }

    /**
     * Test that failJob() sends Telegram notification
     */
    @Test
    void failJobSendsTelegramNotification() {
        MarketingJob job = MarketingJob.builder()
            .id(456L)
            .postId("post-456")
            .status("RUNNING")
            .targets("[\"instagram_reels\"]")
            .build();

        when(marketingJobRepository.save(any(MarketingJob.class))).thenAnswer(inv -> inv.getArgument(0));

        marketingJobService.failJob(job, MarketingFailureStage.ASM_CREATE, "ASM_TIMEOUT", true,
            "ASM request timeout after 30 seconds");

        verify(telegramNotifier).send(anyString());
    }

    /**
     * Test that failJob() handles null stage/code by logging error and still sending alert (Force III - prevent silence)
     */
    @Test
    void failJobSendsAlertWhenStageIsNull() {
        MarketingJob job = MarketingJob.builder()
            .id(789L)
            .postId("post-789")
            .status("RUNNING")
            .targets("[\"x_thread\"]")
            .build();

        when(marketingJobRepository.save(any(MarketingJob.class))).thenAnswer(inv -> inv.getArgument(0));

        // Call failJob with null stage (code defect)
        marketingJobService.failJob(job, null, null, false, "Unknown error");

        // Verify save was still called
        verify(marketingJobRepository).save(any(MarketingJob.class));
        // Verify Telegram alert was sent (침묵 방지)
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramNotifier, times(2)).send(msgCaptor.capture());
        // Check that one of the messages contains the "코드 결함" marker
        assertThat(msgCaptor.getAllValues().stream()
            .anyMatch(msg -> msg.contains("코드 결함")))
            .isTrue();
    }
}
