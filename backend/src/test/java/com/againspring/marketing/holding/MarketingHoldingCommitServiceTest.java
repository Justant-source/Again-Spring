package com.againspring.marketing.holding;

import com.againspring.domain.community.Post;
import com.againspring.domain.marketing.MarketingHolding;
import com.againspring.domain.marketing.MarketingHoldingStatus;
import com.againspring.domain.marketing.MarketingJob;
import com.againspring.domain.marketing.MarketingPinFormat;
import com.againspring.marketing.MarketingJobService;
import com.againspring.marketing.MarketingPlatformAutoService;
import com.againspring.marketing.MarketingPublishFormat;
import com.againspring.marketing.MarketingQuotaService;
import com.againspring.marketing.MarketingScoreWeightService;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.marketing.MarketingHoldingRepository;
import com.againspring.repository.marketing.MarketingHoldingRepository.DueHoldingProjection;
import com.againspring.repository.marketing.MarketingJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingHoldingCommitServiceTest {

    private static final Instant SINCE = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant T0 = Instant.parse("2026-08-06T00:00:00Z");
    private static final MarketingScoreWeightService.Weights WEIGHTS =
        new MarketingScoreWeightService.Weights(0.1, 1.0, 0.5);

    @Mock MarketingHoldingRepository holdingRepository;
    @Mock MarketingJobRepository marketingJobRepository;
    @Mock MarketingJobService marketingJobService;
    @Mock MarketingQuotaService quotaService;
    @Mock MarketingScoreWeightService scoreWeightService;
    @Mock MarketingPlatformAutoService platformAutoService;
    @Mock PostRepository postRepository;

    @InjectMocks
    MarketingHoldingCommitService service;

    private final Map<String, MarketingHolding> holdings = new HashMap<>();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        holdings.clear();
        lenient().when(holdingRepository.findById(anyString())).thenAnswer(inv ->
            Optional.ofNullable(holdings.get(inv.getArgument(0))));
        lenient().when(holdingRepository.save(any(MarketingHolding.class))).thenAnswer(inv -> {
            MarketingHolding h = inv.getArgument(0);
            holdings.put(h.getPostId(), h);
            return h;
        });
        lenient().when(postRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(marketingJobRepository.findByPostIdIn(any())).thenReturn(List.of());
    }

    @Test
    void groupTargets_videoDualPlusAloneText() {
        List<List<String>> groups = MarketingHoldingCommitService.groupTargetsIntoJobs(
            List.of("instagram_reels", "youtube_shorts", "x_thread", "instagram_feed"));
        assertThat(groups).containsExactly(
            List.of("instagram_reels", "youtube_shorts"),
            List.of("x_thread"),
            List.of("instagram_feed"));
    }

    @Test
    void commitTick_pinsFirst_thenAutoByScore_videoThenText_dropsOutsideCut() {
        // remaining=3, videoCap=2 → pin VIDEO, auto VIDEO (highest), auto TEXT, rest DROPPED
        when(quotaService.getStatus()).thenReturn(
            new MarketingQuotaService.QuotaStatus(6, 2, 0, 0, 3));
        when(scoreWeightService.getWeights()).thenReturn(WEIGHTS);
        when(platformAutoService.hasEffectiveVideoPlatforms()).thenReturn(true);

        when(holdingRepository.findDueHoldings(SINCE)).thenReturn(List.of(
            due("pin", "PINNED", "VIDEO", 1, 0, 0, T0),
            due("a", "IN_POOL", null, 100, 20, 0, T0.plusSeconds(3)),
            due("b", "IN_POOL", null, 50, 15, 0, T0.plusSeconds(2)),
            due("c", "OUT_OF_CUT", null, 100, 0, 0, T0.plusSeconds(1)),
            due("d", "IN_POOL", null, 50, 0, 0, T0)
        ));
        when(holdingRepository.findByStatusIn(EnumSet.of(MarketingHoldingStatus.PINNED)))
            .thenReturn(List.of());

        putHolding("pin", MarketingHoldingStatus.PINNED, MarketingPinFormat.VIDEO);
        putHolding("a", MarketingHoldingStatus.IN_POOL, null);
        putHolding("b", MarketingHoldingStatus.IN_POOL, null);
        putHolding("c", MarketingHoldingStatus.OUT_OF_CUT, null);
        putHolding("d", MarketingHoldingStatus.IN_POOL, null);

        when(platformAutoService.resolveTargets(MarketingPublishFormat.VIDEO))
            .thenReturn(List.of("instagram_reels", "youtube_shorts", "x_thread"));
        when(platformAutoService.resolveTargets(MarketingPublishFormat.TEXT))
            .thenReturn(List.of("x_thread", "instagram_feed"));
        when(marketingJobRepository.countActivePlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(marketingJobRepository.countAnyPlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(marketingJobService.createJob(anyString(), any(), anyBoolean(), anyString()))
            .thenAnswer(inv -> MarketingJob.builder()
                .id(1L).postId(inv.getArgument(0)).status("REQUESTED").build());

        MarketingHoldingCommitService.CommitTickResult result = service.runCommitTick(SINCE);

        assertThat(result.pinnedCommitted()).isEqualTo(1);
        assertThat(result.autoVideoCommitted()).isEqualTo(1);
        assertThat(result.autoTextCommitted()).isEqualTo(1);
        assertThat(result.dropped()).isEqualTo(2);

        verify(marketingJobService).createJob(
            eq("pin"), eq(List.of("instagram_reels", "youtube_shorts")), eq(true), anyString());
        verify(marketingJobService).createJob(
            eq("a"), eq(List.of("instagram_reels", "youtube_shorts")), eq(true), anyString());
        verify(marketingJobService).createJob(
            eq("b"), eq(List.of("x_thread")), eq(true), anyString());
        assertThat(holdings.get("c").getStatus()).isEqualTo(MarketingHoldingStatus.DROPPED);
        assertThat(holdings.get("d").getStatus()).isEqualTo(MarketingHoldingStatus.DROPPED);
        assertThat(holdings.get("a").getLockedAt()).isNotNull();
    }

    @Test
    void commitTick_videoTargets_excludeFeedWhenReelsIncluded() {
        when(quotaService.getStatus()).thenReturn(
            new MarketingQuotaService.QuotaStatus(6, 3, 0, 0, 1));
        when(scoreWeightService.getWeights()).thenReturn(WEIGHTS);
        when(platformAutoService.hasEffectiveVideoPlatforms()).thenReturn(true);
        when(holdingRepository.findDueHoldings(SINCE)).thenReturn(List.of(
            due("v1", "IN_POOL", null, 100, 10, 0, T0)));
        when(holdingRepository.findByStatusIn(EnumSet.of(MarketingHoldingStatus.PINNED)))
            .thenReturn(List.of());
        putHolding("v1", MarketingHoldingStatus.IN_POOL, null);

        when(platformAutoService.resolveTargets(MarketingPublishFormat.VIDEO))
            .thenReturn(List.of("instagram_reels", "youtube_shorts", "x_thread"));
        when(marketingJobRepository.countActivePlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(marketingJobRepository.countAnyPlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(marketingJobService.createJob(anyString(), any(), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder().id(9L).status("REQUESTED").build());

        service.runCommitTick(SINCE);

        verify(marketingJobService).createJob(
            eq("v1"), eq(List.of("instagram_reels", "youtube_shorts")), eq(true), anyString());
        verify(marketingJobService).createJob(
            eq("v1"), eq(List.of("x_thread")), eq(true), anyString());
        verify(marketingJobService, never()).createJob(
            eq("v1"), eq(List.of("instagram_feed")), anyBoolean(), anyString());
    }

    @Test
    void commitTick_noVideoPlatforms_effectiveVideoCapZero_allText() {
        when(quotaService.getStatus()).thenReturn(
            new MarketingQuotaService.QuotaStatus(6, 3, 0, 0, 2));
        when(scoreWeightService.getWeights()).thenReturn(WEIGHTS);
        when(platformAutoService.hasEffectiveVideoPlatforms()).thenReturn(false);
        when(holdingRepository.findDueHoldings(SINCE)).thenReturn(List.of(
            due("t1", "IN_POOL", null, 10, 5, 0, T0.plusSeconds(1)),
            due("t2", "IN_POOL", null, 5, 1, 0, T0)));
        when(holdingRepository.findByStatusIn(EnumSet.of(MarketingHoldingStatus.PINNED)))
            .thenReturn(List.of());
        putHolding("t1", MarketingHoldingStatus.IN_POOL, null);
        putHolding("t2", MarketingHoldingStatus.IN_POOL, null);
        when(platformAutoService.resolveTargets(MarketingPublishFormat.TEXT))
            .thenReturn(List.of("x_thread"));
        when(marketingJobRepository.countActivePlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(marketingJobRepository.countAnyPlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(marketingJobService.createJob(anyString(), any(), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder().id(1L).status("REQUESTED").build());

        MarketingHoldingCommitService.CommitTickResult result = service.runCommitTick(SINCE);

        assertThat(result.autoVideoCommitted()).isZero();
        assertThat(result.autoTextCommitted()).isEqualTo(2);
        verify(platformAutoService, never()).resolveTargets(MarketingPublishFormat.VIDEO);
    }

    @Test
    void force_ignoresDailyCap_andLocksDraft() {
        putHolding("drop1", MarketingHoldingStatus.DROPPED, null);
        when(postRepository.existsById("drop1")).thenReturn(true);
        when(platformAutoService.resolveTargets(MarketingPublishFormat.VIDEO))
            .thenReturn(List.of("instagram_reels", "x_thread"));
        when(marketingJobRepository.countActivePlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(marketingJobRepository.countAnyPlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(marketingJobService.createJob(anyString(), any(), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder().id(42L).status("REQUESTED").build());

        MarketingHoldingCommitService.ForceResult result = service.forceCommit(
            "drop1",
            MarketingHoldingCommitService.ForceMode.VIDEO_AND_TEXT,
            "admin@test");

        assertThat(result.status()).isEqualTo(MarketingHoldingStatus.COMMITTED);
        assertThat(result.jobIds()).contains(42L);
        assertThat(holdings.get("drop1").getStatus()).isEqualTo(MarketingHoldingStatus.COMMITTED);
        assertThat(holdings.get("drop1").getLockedAt()).isNotNull();
        verify(quotaService, never()).getStatus();
    }

    @Test
    void force_requestedBy_keepsAdminForcePrefixPlusJwtSubjectUuid() {
        // Prod 500: VARCHAR(32) could not store "admin:force:"(12) + UUID(36) = 48.
        String jwtSubject = "01234567-89ab-cdef-0123-456789abcdef";
        String expectedRequestedBy =
            MarketingHoldingCommitService.REQUESTED_BY_FORCE_PREFIX + jwtSubject;
        assertThat(expectedRequestedBy.length()).isGreaterThan(32);

        putHolding("drop-uuid", MarketingHoldingStatus.DROPPED, null);
        when(postRepository.existsById("drop-uuid")).thenReturn(true);
        when(platformAutoService.resolveTargets(MarketingPublishFormat.TEXT))
            .thenReturn(List.of("x_thread"));
        when(marketingJobRepository.countActivePlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(marketingJobRepository.countAnyPlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(marketingJobService.createJob(anyString(), any(), anyBoolean(), anyString()))
            .thenReturn(MarketingJob.builder().id(99L).status("REQUESTED").build());

        service.forceCommit(
            "drop-uuid",
            MarketingHoldingCommitService.ForceMode.TEXT_ONLY,
            jwtSubject);

        verify(marketingJobService).createJob(
            eq("drop-uuid"), any(), eq(true), eq(expectedRequestedBy));
        assertThat(expectedRequestedBy.length()).isLessThanOrEqualTo(128);
    }

    @Test
    void force_alreadyCommitted_rejects() {
        putHolding("c1", MarketingHoldingStatus.COMMITTED, null);
        holdings.get("c1").setLockedAt(Instant.now());

        assertThatThrownBy(() -> service.forceCommit(
            "c1", MarketingHoldingCommitService.ForceMode.TEXT_ONLY, "admin"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void commitTick_pinDeferredWhenPoolExhausted_keepsPinned() {
        when(quotaService.getStatus()).thenReturn(
            new MarketingQuotaService.QuotaStatus(6, 3, 0, 0, 0));
        when(scoreWeightService.getWeights()).thenReturn(WEIGHTS);
        when(platformAutoService.hasEffectiveVideoPlatforms()).thenReturn(true);
        when(holdingRepository.findDueHoldings(SINCE)).thenReturn(List.of(
            due("pin", "PINNED", "TEXT", 1, 0, 0, T0),
            due("out", "OUT_OF_CUT", null, 1, 0, 0, T0)));
        when(holdingRepository.findByStatusIn(EnumSet.of(MarketingHoldingStatus.PINNED)))
            .thenReturn(List.of(MarketingHolding.builder()
                .postId("pin").status(MarketingHoldingStatus.PINNED)
                .pinFormat(MarketingPinFormat.TEXT).build()));
        putHolding("pin", MarketingHoldingStatus.PINNED, MarketingPinFormat.TEXT);
        putHolding("out", MarketingHoldingStatus.OUT_OF_CUT, null);

        MarketingHoldingCommitService.CommitTickResult result = service.runCommitTick(SINCE);

        assertThat(result.pinnedDeferred()).isEqualTo(1);
        assertThat(result.pinnedCommitted()).isZero();
        assertThat(result.dropped()).isEqualTo(1);
        assertThat(holdings.get("pin").getStatus()).isEqualTo(MarketingHoldingStatus.PINNED);
        verify(marketingJobService, never()).createJob(anyString(), any(), anyBoolean(), anyString());
    }

    @Test
    void listCompleted_titleFromDraftJson_andPublicationsParsed() {
        MarketingHolding h = MarketingHolding.builder()
            .postId("s1")
            .status(MarketingHoldingStatus.COMMITTED)
            .draftJson("{\"title\":\"Draft Title\"}")
            .lockedAt(T0)
            .build();
        when(holdingRepository.findByStatusIn(
            EnumSet.of(MarketingHoldingStatus.COMMITTED, MarketingHoldingStatus.DROPPED)))
            .thenReturn(List.of(h));

        MarketingJob job = MarketingJob.builder()
            .id(1L).postId("s1").status("PUBLISHED")
            .targets("[\"instagram_reels\",\"youtube_shorts\"]")
            .publications("[{\"platform\":\"instagram_reels\",\"state\":\"published\","
                + "\"url\":\"https://instagram.com/p/abc\"},"
                + "{\"platform\":\"youtube_shorts\",\"state\":\"failed\",\"url\":null}]")
            .createdAt(T0)
            .build();
        when(marketingJobRepository.findByPostIdIn(anySet())).thenReturn(List.of(job));

        List<MarketingHoldingCommitService.CompletedItem> items =
            service.listCompleted(null, 50);

        assertThat(items).hasSize(1);
        MarketingHoldingCommitService.CompletedItem item = items.get(0);
        assertThat(item.title()).isEqualTo("Draft Title");
        assertThat(item.committedFormat()).isEqualTo("VIDEO");
        assertThat(item.jobs()).hasSize(1);
        List<MarketingHoldingCommitService.PublicationSummary> pubs = item.jobs().get(0).publications();
        assertThat(pubs).hasSize(2);
        assertThat(pubs.get(0).platform()).isEqualTo("instagram_reels");
        assertThat(pubs.get(0).state()).isEqualTo("published");
        assertThat(pubs.get(0).url()).isEqualTo("https://instagram.com/p/abc");
        assertThat(pubs.get(1).platform()).isEqualTo("youtube_shorts");
        assertThat(pubs.get(1).state()).isEqualTo("failed");
        assertThat(pubs.get(1).url()).isNull();
    }

    @Test
    void listCompleted_titleFallsBackToPostWhenDraftMissingTitle() {
        MarketingHolding h = MarketingHolding.builder()
            .postId("s2")
            .status(MarketingHoldingStatus.DROPPED)
            .draftJson("{}")
            .build();
        when(holdingRepository.findByStatusIn(
            EnumSet.of(MarketingHoldingStatus.COMMITTED, MarketingHoldingStatus.DROPPED)))
            .thenReturn(List.of(h));
        when(postRepository.findAllById(anySet())).thenReturn(List.of(
            Post.builder().id("s2").title("Live Post Title").build()));

        List<MarketingHoldingCommitService.CompletedItem> items =
            service.listCompleted(null, 50);

        assertThat(items.get(0).title()).isEqualTo("Live Post Title");
        assertThat(items.get(0).committedFormat()).isNull();
    }

    @Test
    void listCompleted_committedFormat_fallsBackToPinFormatWhenNoJobs() {
        MarketingHolding h = MarketingHolding.builder()
            .postId("s3")
            .status(MarketingHoldingStatus.DROPPED)
            .pinFormat(MarketingPinFormat.TEXT)
            .draftJson("{}")
            .build();
        when(holdingRepository.findByStatusIn(
            EnumSet.of(MarketingHoldingStatus.COMMITTED, MarketingHoldingStatus.DROPPED)))
            .thenReturn(List.of(h));

        List<MarketingHoldingCommitService.CompletedItem> items =
            service.listCompleted(null, 50);

        assertThat(items.get(0).committedFormat()).isEqualTo("TEXT");
        assertThat(items.get(0).pinFormat()).isEqualTo("TEXT");
    }

    private void putHolding(String postId, MarketingHoldingStatus status, MarketingPinFormat pin) {
        holdings.put(postId, MarketingHolding.builder()
            .postId(postId)
            .status(status)
            .pinFormat(pin)
            .draftJson("{}")
            .build());
    }

    private static DueHoldingProjection due(
            String postId, String status, String pinFormat,
            int views, long comments, long votes, Instant createdAt) {
        return new DueHoldingProjection() {
            @Override public String getPostId() { return postId; }
            @Override public String getStatus() { return status; }
            @Override public String getPinFormat() { return pinFormat; }
            @Override public Double getScoreSnapshot() { return null; }
            @Override public Instant getPostCreatedAt() { return createdAt; }
            @Override public Number getViewCount() { return views; }
            @Override public Number getCommentCount() { return comments; }
            @Override public Number getVoteCount() { return votes; }
        };
    }
}
