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
import com.againspring.repository.community.JurorRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.VoteOptionRepository;
import com.againspring.repository.marketing.MarketingJobRepository;
import com.againspring.service.community.CommentService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
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
    JurorRepository jurorRepository;

    @Mock
    VoteService voteService;

    @Mock
    CommentService commentService;

    @Mock
    VoteOptionRepository voteOptionRepository;

    @InjectMocks
    MarketingJobService marketingJobService;

    private static final String TEST_POST_ID = "post-123";
    private static final String TEST_JOB_ID = "remote-job-456";
    private static final List<String> TEST_TARGETS = Arrays.asList("twitter", "threads");

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
        when(jurorRepository.findByPostId(any())).thenReturn(List.of());
        when(voteService.getVoteResult(any())).thenReturn(Map.of());
        when(voteOptionRepository.findByPostIdOrderByOrderIdx(any())).thenReturn(List.of());
        when(commentService.getTopLevelComments(any())).thenReturn(List.of());

        CreateJobResponse response = CreateJobResponse.builder()
            .jobId(TEST_JOB_ID)
            .status("QUEUED")
            .build();

        when(asmClient.createJob(any(CreateJobRequest.class), any(String.class)))
            .thenReturn(response);

        MarketingJob savedJob = MarketingJob.builder()
            .id(1L)
            .remoteJobId(TEST_JOB_ID)
            .postId(TEST_POST_ID)
            .status("QUEUED")
            .autoPublish(false)
            .requestedBy("admin")
            .build();

        when(marketingJobRepository.save(any(MarketingJob.class)))
            .thenReturn(savedJob);

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
        assertThat(result.getRemoteJobId()).isEqualTo(TEST_JOB_ID);
        assertThat(result.getPostId()).isEqualTo(TEST_POST_ID);
        assertThat(result.getStatus()).isEqualTo("QUEUED");

        verify(postRepository).findById(TEST_POST_ID);
        verify(marketingJobRepository).countActivePlatformJobs(TEST_POST_ID, "twitter");
        verify(marketingJobRepository).countActivePlatformJobs(TEST_POST_ID, "threads");
        verify(asmClient).createJob(any(CreateJobRequest.class), any(String.class));
        verify(marketingJobRepository).save(any(MarketingJob.class));
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
        when(jurorRepository.findByPostId(any())).thenReturn(List.of());
        when(voteService.getVoteResult(any())).thenReturn(Map.of());
        when(voteOptionRepository.findByPostIdOrderByOrderIdx(any())).thenReturn(List.of());
        when(commentService.getTopLevelComments(any())).thenReturn(List.of());

        CreateJobResponse response = CreateJobResponse.builder()
            .jobId(TEST_JOB_ID)
            .status("QUEUED")
            .build();
        when(asmClient.createJob(any(CreateJobRequest.class), any(String.class)))
            .thenReturn(response);
        when(marketingJobRepository.save(any(MarketingJob.class)))
            .thenAnswer(inv -> inv.getArgument(0));
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
        when(jurorRepository.findByPostId(any())).thenReturn(List.of());
        when(voteService.getVoteResult(any())).thenReturn(Map.of());
        when(voteOptionRepository.findByPostIdOrderByOrderIdx(any())).thenReturn(List.of());
        when(commentService.getTopLevelComments(any())).thenReturn(List.of());

        CreateJobResponse response = CreateJobResponse.builder()
            .jobId(TEST_JOB_ID)
            .status("QUEUED")
            .build();
        when(asmClient.createJob(any(CreateJobRequest.class), any(String.class)))
            .thenReturn(response);
        when(marketingJobRepository.save(any(MarketingJob.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        doReturn("[]").when(objectMapper).writeValueAsString(any());
        when(asmProperties.getCallbackBaseUrl()).thenReturn("http://localhost:8080");

        marketingJobService.createJob(TEST_POST_ID, List.of("x_thread"), false, "admin");

        ArgumentCaptor<CreateJobRequest> captor = ArgumentCaptor.forClass(CreateJobRequest.class);
        verify(asmClient).createJob(captor.capture(), any(String.class));
        assertThat(captor.getValue().getBrief().getHasPartnerStory()).isFalse();
        assertThat(captor.getValue().getBrief().getPartnerCaptureSplitAfterLines()).isNull();
        assertThat(captor.getValue().getBrief().getPartnerPartHeightsCss()).isNull();
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
        when(jurorRepository.findByPostId(any())).thenReturn(List.of());
        when(voteService.getVoteResult(any())).thenReturn(Map.of());
        when(voteOptionRepository.findByPostIdOrderByOrderIdx(any())).thenReturn(List.of());
        when(commentService.getTopLevelComments(any())).thenReturn(List.of());

        CreateJobResponse response = CreateJobResponse.builder()
            .jobId("new-job-222")
            .status("QUEUED")
            .build();

        when(asmClient.createJob(any(CreateJobRequest.class), any(String.class)))
            .thenReturn(response);

        MarketingJob savedJob = MarketingJob.builder()
            .id(2L)
            .remoteJobId("new-job-222")
            .postId(TEST_POST_ID)
            .status("QUEUED")
            .build();

        when(marketingJobRepository.save(any(MarketingJob.class)))
            .thenReturn(savedJob);

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

        verify(marketingJobRepository).save(any(MarketingJob.class));
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

    // ── Test 5: applyCallback_updatesJobFromRemote ──────────────────────────

    @Test
    void applyCallback_updatesJobFromRemoteJobId() {
        // Given
        MarketingJob job = MarketingJob.builder()
            .id(1L)
            .remoteJobId(TEST_JOB_ID)
            .postId(TEST_POST_ID)
            .status("PUBLISHING")
            .build();

        JobCallbackPayload payload = JobCallbackPayload.builder()
            .jobId(TEST_JOB_ID)
            .status("PUBLISHED")
            .event("PUBLISHED")
            .build();

        when(marketingJobRepository.findByRemoteJobId(TEST_JOB_ID))
            .thenReturn(Optional.of(job));

        when(marketingJobRepository.save(any(MarketingJob.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        marketingJobService.applyCallback(payload);

        // Then
        assertThat(job.getStatus()).isEqualTo("PUBLISHED");

        verify(marketingJobRepository).findByRemoteJobId(TEST_JOB_ID);
        verify(marketingJobRepository).save(job);
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
}
