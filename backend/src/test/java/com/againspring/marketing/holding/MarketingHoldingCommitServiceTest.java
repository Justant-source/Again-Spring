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
import com.againspring.marketing.MarketingThemeBoostService;
import com.againspring.notification.TelegramNotifier;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingHoldingCommitServiceTest {

    private static final Instant SINCE = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant T0 = Instant.parse("2026-08-06T00:00:00Z");

    private static final MarketingScoreWeightService.AllPlatformWeights PLATFORM_WEIGHTS =
        new MarketingScoreWeightService.AllPlatformWeights(
            MarketingScoreWeightService.defaultsX(),
            MarketingScoreWeightService.defaultsFeed(),
            MarketingScoreWeightService.defaultsReels(),
            MarketingScoreWeightService.defaultsShorts());

    @Mock MarketingHoldingRepository holdingRepository;
    @Mock MarketingJobRepository marketingJobRepository;
    @Mock MarketingJobService marketingJobService;
    @Mock MarketingQuotaService quotaService;
    @Mock MarketingScoreWeightService scoreWeightService;
    @Mock MarketingThemeBoostService themeBoostService;
    @Mock MarketingPlatformAutoService platformAutoService;
    @Mock PostRepository postRepository;
    @Mock PlatformTransactionManager transactionManager;
    @Mock TelegramNotifier telegramNotifier;

    @InjectMocks
    MarketingHoldingCommitService service;

    private final Map<String, MarketingHolding> holdings = new HashMap<>();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        holdings.clear();
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
            .thenReturn(new SimpleTransactionStatus());
        lenient().doNothing().when(transactionManager).commit(any());
        lenient().doNothing().when(transactionManager).rollback(any());
        lenient().when(holdingRepository.findById(anyString())).thenAnswer(inv ->
            Optional.ofNullable(holdings.get(inv.getArgument(0))));
        lenient().when(holdingRepository.save(any(MarketingHolding.class))).thenAnswer(inv -> {
            MarketingHolding h = inv.getArgument(0);
            holdings.put(h.getPostId(), h);
            return h;
        });
        lenient().when(postRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(marketingJobRepository.findByPostIdIn(any())).thenReturn(List.of());
        lenient().when(scoreWeightService.getPlatformWeights()).thenReturn(PLATFORM_WEIGHTS);
        // Default shadow on → theme boost not applied (allocation identical to pre-Phase-3).
        lenient().when(themeBoostService.isShadow()).thenReturn(true);
        lenient().when(themeBoostService.getBoost(any(), any(), any())).thenReturn(1.0);
        lenient().when(platformAutoService.listEnabledPlatforms()).thenReturn(List.of(
            "x_thread", "instagram_feed", "instagram_reels", "youtube_shorts"));
    }

    @Test
    void groupTargets_eachPlatformAlone_noDualVideo() {
        List<List<String>> groups = MarketingHoldingCommitService.groupTargetsIntoJobs(
            List.of("instagram_reels", "youtube_shorts", "x_thread", "instagram_feed"));
        assertThat(groups).containsExactly(
            List.of("instagram_reels"),
            List.of("youtube_shorts"),
            List.of("x_thread"),
            List.of("instagram_feed"));
    }

    @Test
    void commitTick_selectsIndependentlyPerPlatform_sameStoryAllowed() {
        // Remaining 1 each. Story "hot" wins all except IG exclusivity picks reels over feed.
        when(quotaService.remainingCapsMutable()).thenReturn(remaining(1, 1, 1, 1));
        when(holdingRepository.findDueHoldings(SINCE)).thenReturn(List.of(
            due("hot", "IN_POOL", null, 100, 50, 40, 20, 1, "훅", T0.plusSeconds(3)),
            due("other", "IN_POOL", null, 10, 5, 5, 2, 0, null, T0)
        ));
        when(holdingRepository.findByStatusIn(EnumSet.of(MarketingHoldingStatus.PINNED)))
            .thenReturn(List.of());
        putHolding("hot", MarketingHoldingStatus.IN_POOL, null);
        putHolding("other", MarketingHoldingStatus.IN_POOL, null);
        stubJobs();

        MarketingHoldingCommitService.CommitTickResult result = service.runCommitTick(SINCE);

        assertThat(result.autoCommitted()).isGreaterThanOrEqualTo(1);
        assertThat(holdings.get("hot").getStatus()).isEqualTo(MarketingHoldingStatus.COMMITTED);
        // Separate jobs for reels and shorts (not dual)
        verify(marketingJobService).createJob(eq("hot"), eq(List.of("instagram_reels")), eq(true), anyString());
        verify(marketingJobService).createJob(eq("hot"), eq(List.of("youtube_shorts")), eq(true), anyString());
        verify(marketingJobService).createJob(eq("hot"), eq(List.of("x_thread")), eq(true), anyString());
        // IG exclusivity: tie-ish / reels preferred when both would win — feed goes to other or skipped
        verify(marketingJobService, never()).createJob(
            eq("hot"), eq(List.of("instagram_feed")), anyBoolean(), anyString());
        assertThat(holdings.get("hot").getPlatformRankSnapshot())
            .contains("instagram_reels", "youtube_shorts", "x_thread");
    }

    @Test
    void commitTick_pinsFirst_thenAutos_dropsRest() {
        when(quotaService.remainingCapsMutable()).thenReturn(remaining(1, 0, 1, 0));
        when(holdingRepository.findDueHoldings(SINCE)).thenReturn(List.of(
            due("pin", "PINNED", "VIDEO", 1, 0, 0, 0, 0, null, T0),
            due("a", "IN_POOL", null, 100, 20, 10, 5, 0, null, T0.plusSeconds(2)),
            due("b", "OUT_OF_CUT", null, 50, 0, 0, 0, 0, null, T0)
        ));
        when(holdingRepository.findByStatusIn(EnumSet.of(MarketingHoldingStatus.PINNED)))
            .thenReturn(List.of());
        putHolding("pin", MarketingHoldingStatus.PINNED, MarketingPinFormat.VIDEO);
        putHolding("a", MarketingHoldingStatus.IN_POOL, null);
        putHolding("b", MarketingHoldingStatus.OUT_OF_CUT, null);
        stubJobs();

        MarketingHoldingCommitService.CommitTickResult result = service.runCommitTick(SINCE);

        assertThat(result.pinnedCommitted()).isEqualTo(1);
        assertThat(holdings.get("pin").getStatus()).isEqualTo(MarketingHoldingStatus.COMMITTED);
        // VIDEO pin takes reels (and x if remaining) — x remaining was 1, consumed by pin
        verify(marketingJobService).createJob(eq("pin"), eq(List.of("instagram_reels")), eq(true), anyString());
        assertThat(holdings.get("b").getStatus()).isEqualTo(MarketingHoldingStatus.DROPPED);
    }

    @Test
    void commitTick_pinDeferredWhenCapsExhausted_keepsPinned() {
        when(quotaService.remainingCapsMutable()).thenReturn(remaining(0, 0, 0, 0));
        when(holdingRepository.findDueHoldings(SINCE)).thenReturn(List.of(
            due("pin", "PINNED", "TEXT", 1, 0, 0, 0, 0, null, T0),
            due("out", "OUT_OF_CUT", null, 1, 0, 0, 0, 0, null, T0)));
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
    void commitTick_createJobFailure_defersSelectedAuto_doesNotDrop_andContinues() {
        when(quotaService.remainingCapsMutable()).thenReturn(remaining(1, 0, 1, 0));
        when(holdingRepository.findDueHoldings(SINCE)).thenReturn(List.of(
            due("hot", "IN_POOL", null, 100, 20, 10, 5, 0, null, T0.plusSeconds(2)),
            due("other", "OUT_OF_CUT", null, 10, 0, 0, 0, 0, null, T0)
        ));
        when(holdingRepository.findByStatusIn(EnumSet.of(MarketingHoldingStatus.PINNED)))
            .thenReturn(List.of());
        putHolding("hot", MarketingHoldingStatus.IN_POOL, null);
        putHolding("other", MarketingHoldingStatus.OUT_OF_CUT, null);
        when(marketingJobRepository.countActivePlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(marketingJobRepository.countAnyPlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(marketingJobService.createJob(anyString(), any(), anyBoolean(), anyString()))
            .thenThrow(new DataIntegrityViolationException("Column 'scheduled_publish_at' cannot be null"));

        MarketingHoldingCommitService.CommitTickResult result = service.runCommitTick(SINCE);

        assertThat(result.autoCommitted()).isZero();
        assertThat(result.autoDeferred()).isEqualTo(1);
        assertThat(result.dropped()).isEqualTo(1);
        assertThat(holdings.get("hot").getStatus()).isEqualTo(MarketingHoldingStatus.IN_POOL);
        assertThat(holdings.get("other").getStatus()).isEqualTo(MarketingHoldingStatus.DROPPED);
        verify(telegramNotifier).send(org.mockito.ArgumentMatchers.contains("hot"));
    }

    @Test
    void commitTick_createJobFailureOnAuto_stillCommitsPin() {
        when(quotaService.remainingCapsMutable()).thenReturn(remaining(1, 1, 1, 1));
        when(holdingRepository.findDueHoldings(SINCE)).thenReturn(List.of(
            due("pin", "PINNED", "TEXT", 1, 0, 0, 0, 0, null, T0),
            due("hot", "IN_POOL", null, 100, 20, 10, 5, 0, null, T0.plusSeconds(2))
        ));
        when(holdingRepository.findByStatusIn(EnumSet.of(MarketingHoldingStatus.PINNED)))
            .thenReturn(List.of());
        putHolding("pin", MarketingHoldingStatus.PINNED, MarketingPinFormat.TEXT);
        putHolding("hot", MarketingHoldingStatus.IN_POOL, null);
        when(marketingJobRepository.countActivePlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(marketingJobRepository.countAnyPlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(marketingJobService.createJob(anyString(), any(), anyBoolean(), anyString()))
            .thenAnswer(inv -> {
                String postId = inv.getArgument(0);
                if ("hot".equals(postId)) {
                    throw new DataIntegrityViolationException("Column 'scheduled_publish_at' cannot be null");
                }
                return MarketingJob.builder().id(1L).postId(postId).status("REQUESTED").build();
            });

        MarketingHoldingCommitService.CommitTickResult result = service.runCommitTick(SINCE);

        assertThat(result.pinnedCommitted()).isEqualTo(1);
        assertThat(result.autoDeferred()).isEqualTo(1);
        assertThat(holdings.get("pin").getStatus()).isEqualTo(MarketingHoldingStatus.COMMITTED);
        assertThat(holdings.get("hot").getStatus()).isEqualTo(MarketingHoldingStatus.IN_POOL);
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
        verify(quotaService, never()).remainingCapsMutable();
    }

    @Test
    void force_requestedBy_keepsAdminForcePrefixPlusJwtSubjectUuid() {
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
    void force_alreadyCommitted_enqueuesMissingVideoTargets_asSeparateJobs() {
        putHolding("c1", MarketingHoldingStatus.COMMITTED, null);
        holdings.get("c1").setLockedAt(Instant.now());
        when(postRepository.existsById("c1")).thenReturn(true);
        when(platformAutoService.resolveTargets(MarketingPublishFormat.VIDEO))
            .thenReturn(List.of("instagram_reels", "youtube_shorts", "x_thread", "instagram_feed"));
        when(marketingJobRepository.countAnyPlatformJobs(eq("c1"), eq("x_thread"))).thenReturn(1L);
        when(marketingJobRepository.countAnyPlatformJobs(eq("c1"), eq("instagram_feed"))).thenReturn(1L);
        when(marketingJobRepository.countAnyPlatformJobs(eq("c1"), eq("instagram_reels"))).thenReturn(0L);
        when(marketingJobRepository.countAnyPlatformJobs(eq("c1"), eq("youtube_shorts"))).thenReturn(0L);
        when(marketingJobRepository.countActivePlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(marketingJobService.createJob(eq("c1"), any(), eq(true), anyString()))
            .thenReturn(MarketingJob.builder().id(77L).status("REQUESTED").build());

        MarketingHoldingCommitService.ForceResult result = service.forceCommit(
            "c1",
            MarketingHoldingCommitService.ForceMode.VIDEO_AND_TEXT,
            "admin");

        assertThat(result.jobIds()).hasSize(2);
        verify(marketingJobService).createJob(eq("c1"), eq(List.of("instagram_reels")), eq(true), anyString());
        verify(marketingJobService).createJob(eq("c1"), eq(List.of("youtube_shorts")), eq(true), anyString());
        verify(marketingJobService, never()).createJob(eq("c1"), eq(List.of("x_thread")), anyBoolean(), anyString());
        verify(marketingJobService, times(2)).createJob(eq("c1"), any(), eq(true), anyString());
    }

    @Test
    void force_alreadyCommitted_allTargetsExist_rejects() {
        putHolding("c2", MarketingHoldingStatus.COMMITTED, null);
        holdings.get("c2").setLockedAt(Instant.now());
        when(postRepository.existsById("c2")).thenReturn(true);
        when(platformAutoService.resolveTargets(MarketingPublishFormat.TEXT))
            .thenReturn(List.of("x_thread"));
        when(marketingJobRepository.countAnyPlatformJobs(eq("c2"), eq("x_thread"))).thenReturn(1L);
        when(marketingJobRepository.countActivePlatformJobs(anyString(), anyString())).thenReturn(0L);

        assertThatThrownBy(() -> service.forceCommit(
            "c2", MarketingHoldingCommitService.ForceMode.TEXT_ONLY, "admin"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("No new jobs");
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
            .targets("[\"instagram_reels\"]")
            .publications("[{\"platform\":\"instagram_reels\",\"state\":\"published\","
                + "\"url\":\"https://instagram.com/p/abc\"}]")
            .createdAt(T0)
            .build();
        when(marketingJobRepository.findByPostIdIn(anySet())).thenReturn(List.of(job));

        List<MarketingHoldingCommitService.CompletedItem> items =
            service.listCompleted(null, 50);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).title()).isEqualTo("Draft Title");
        assertThat(items.get(0).committedFormat()).isEqualTo("VIDEO");
        assertThat(items.get(0).jobs().get(0).publications().get(0).url())
            .isEqualTo("https://instagram.com/p/abc");
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

    private void stubJobs() {
        when(marketingJobRepository.countActivePlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(marketingJobRepository.countAnyPlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(marketingJobService.createJob(anyString(), any(), anyBoolean(), anyString()))
            .thenAnswer(inv -> MarketingJob.builder()
                .id(1L).postId(inv.getArgument(0)).status("REQUESTED").build());
    }

    private static Map<String, Integer> remaining(int x, int feed, int reels, int shorts) {
        Map<String, Integer> m = new HashMap<>();
        m.put("x_thread", x);
        m.put("instagram_feed", feed);
        m.put("instagram_reels", reels);
        m.put("youtube_shorts", shorts);
        return m;
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
            int views, long comments, long votes, long authorVotes,
            int hasPartner, String hookText, Instant createdAt) {
        return new DueHoldingProjection() {
            @Override public String getPostId() { return postId; }
            @Override public String getStatus() { return status; }
            @Override public String getPinFormat() { return pinFormat; }
            @Override public Double getScoreSnapshot() { return null; }
            @Override public Instant getPostCreatedAt() { return createdAt; }
            @Override public Number getViewCount() { return views; }
            @Override public Number getCommentCount() { return comments; }
            @Override public Number getVoteCount() { return votes; }
            @Override public Number getAuthorVoteCount() { return authorVotes; }
            @Override public Number getHasPartner() { return hasPartner; }
            @Override public String getHookText() { return hookText; }
        };
    }
}
