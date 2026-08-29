package com.againspring.marketing.holding;

import com.againspring.domain.community.Post;
import com.againspring.domain.marketing.MarketingHolding;
import com.againspring.domain.marketing.MarketingHoldingExclusion;
import com.againspring.domain.marketing.MarketingHoldingStatus;
import com.againspring.domain.marketing.MarketingPinFormat;
import com.againspring.marketing.MarketingQuotaService;
import com.againspring.marketing.MarketingScoreWeightService;
import com.againspring.marketing.dto.CreateJobRequest.BriefDto;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.marketing.MarketingHoldingExclusionRepository;
import com.againspring.repository.marketing.MarketingHoldingRepository;
import com.againspring.repository.marketing.MarketingHoldingRepository.DueHoldingProjection;
import com.againspring.repository.marketing.MarketingHoldingRepository.HoldingCandidateProjection;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MarketingHoldingService} (S2 board + S3 pin/soft-reserve).
 */
@ExtendWith(MockitoExtension.class)
class MarketingHoldingServiceTest {

    @Mock
    private MarketingHoldingRepository holdingRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private MarketingQuotaService quotaService;

    @Mock
    private MarketingScoreWeightService scoreWeightService;

    @Mock
    private MarketingHoldingBriefSeeder briefSeeder;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private MarketingHoldingExclusionRepository exclusionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MarketingHoldingService service;

    @BeforeEach
    void setUp() {
        // Run TransactionTemplate callbacks inline (no real DB transaction).
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
            .thenReturn(new SimpleTransactionStatus());
        lenient().doNothing().when(transactionManager).commit(any());
        lenient().doNothing().when(transactionManager).rollback(any());
        service = new MarketingHoldingService(
            holdingRepository,
            postRepository,
            quotaService,
            scoreWeightService,
            briefSeeder,
            objectMapper,
            transactionManager,
            exclusionRepository
        );
    }

    @Test
    void updateDraft_whenLocked_rejectsWithBadRequest() {
        MarketingHolding locked = MarketingHolding.builder()
            .postId("post-locked")
            .status(MarketingHoldingStatus.COMMITTED)
            .draftJson("{\"title\":\"old\"}")
            .lockedAt(Instant.parse("2026-08-08T00:00:00Z"))
            .build();
        when(holdingRepository.findById("post-locked")).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> service.updateDraft("post-locked", Map.of("title", "new")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(rse.getReason()).containsIgnoringCase("locked");
            });

        verify(holdingRepository, never()).save(any());
    }

    @Test
    void getBoard_whenInPoolFallsOutsideCutline_setsOutOfCutAndKeepsDraft() throws Exception {
        String preservedDraft = objectMapper.writeValueAsString(Map.of(
            "title", "admin-edited-title",
            "tags", List.of("keep-me")
        ));

        when(quotaService.getStatus()).thenReturn(new MarketingQuotaService.QuotaStatus(
            6, 3, 0, 0, 2L)); // cutline N = 2
        when(scoreWeightService.getWeights()).thenReturn(new MarketingScoreWeightService.Weights(
            0.1, 1.0, 0.5));

        Instant t0 = Instant.parse("2026-08-08T10:00:00Z");
        List<HoldingCandidateProjection> candidates = List.of(
            candidate("p1", 100, 10, 5, t0.plusSeconds(3)),
            candidate("p2", 50, 5, 2, t0.plusSeconds(2)),
            candidate("p3", 10, 1, 0, t0.plusSeconds(1)) // rank 3 > cutline 2
        );
        when(holdingRepository.findActiveCandidates()).thenReturn(candidates);

        MarketingHolding existing = MarketingHolding.builder()
            .postId("p3")
            .status(MarketingHoldingStatus.IN_POOL)
            .draftJson(preservedDraft)
            .createdAt(t0)
            .updatedAt(t0)
            .build();

        when(holdingRepository.findByPostIdIn(anyCollection())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Collection<String> ids = inv.getArgument(0);
            List<MarketingHolding> found = new ArrayList<>();
            if (ids.contains("p3")) {
                found.add(existing);
            }
            return found;
        });

        List<Post> posts = List.of(
            post("p1", "Post 1", t0.plusSeconds(3)),
            post("p2", "Post 2", t0.plusSeconds(2)),
            post("p3", "Post 3", t0.plusSeconds(1))
        );
        when(postRepository.findAllById(any())).thenReturn(posts);

        when(briefSeeder.seedFromPost(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            return BriefDto.builder().title("seeded-" + p.getId()).build();
        });

        stubStatusQueries(List.of(), List.of(existing));
        when(holdingRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        MarketingHoldingService.HoldingBoard board = service.getBoard();

        MarketingHoldingService.BoardItem outOfCut = board.items().stream()
            .filter(i -> "p3".equals(i.postId()))
            .findFirst()
            .orElseThrow();

        assertThat(outOfCut.status()).isEqualTo(MarketingHoldingStatus.OUT_OF_CUT);
        assertThat(outOfCut.draft()).containsEntry("title", "admin-edited-title");
        assertThat(outOfCut.draft()).containsEntry("tags", List.of("keep-me"));
        assertThat(board.meta().cutlineN()).isEqualTo(2);

        verify(briefSeeder, never()).seedFromPost(posts.get(2));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarketingHolding>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(holdingRepository).saveAll(saveCaptor.capture());
        MarketingHolding savedP3 = saveCaptor.getValue().stream()
            .filter(h -> "p3".equals(h.getPostId()))
            .findFirst()
            .orElseThrow();
        assertThat(savedP3.getStatus()).isEqualTo(MarketingHoldingStatus.OUT_OF_CUT);
        assertThat(savedP3.getDraftJson()).isEqualTo(preservedDraft);
    }

    @Test
    void getBoard_limitsDisplaySizeToTwenty() {
        when(quotaService.getStatus()).thenReturn(new MarketingQuotaService.QuotaStatus(
            6, 3, 0, 0, 6L));
        when(scoreWeightService.getWeights()).thenReturn(new MarketingScoreWeightService.Weights(
            0.1, 1.0, 0.5));

        Instant base = Instant.parse("2026-08-08T12:00:00Z");
        List<HoldingCandidateProjection> candidates = IntStream.rangeClosed(1, 25)
            .mapToObj(i -> candidate(
                "post-" + i,
                1000 - i, // descending score via views
                0,
                0,
                base.minusSeconds(i)))
            .toList();
        when(holdingRepository.findActiveCandidates()).thenReturn(candidates);
        when(holdingRepository.findByPostIdIn(anyCollection())).thenReturn(List.of());
        stubStatusQueries(List.of(), List.of());
        when(holdingRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Post> posts = IntStream.rangeClosed(1, 20)
            .mapToObj(i -> post("post-" + i, "Title " + i, base.minusSeconds(i)))
            .toList();
        when(postRepository.findAllById(any())).thenReturn(posts);
        when(briefSeeder.seedFromPost(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            return BriefDto.builder().title(p.getTitle()).build();
        });

        MarketingHoldingService.HoldingBoard board = service.getBoard();

        assertThat(board.items()).hasSize(MarketingHoldingService.BOARD_DISPLAY_LIMIT);
        assertThat(board.items()).hasSizeLessThanOrEqualTo(20);
        assertThat(board.items())
            .extracting(MarketingHoldingService.BoardItem::postId)
            .doesNotContain("post-21", "post-25");
        assertThat(board.meta().cutlineN()).isEqualTo(6);
    }

    @Test
    void getBoard_softReserve_reducesCutlineAndVideoBand() {
        // remainingPool=3, 1 VIDEO pin → cutlineN=2, videoSlots=min(3-0-1,2)=2 → wait
        // dailyVideoCap=3, videosToday=0, reservedVideos=1 → raw video=2, cutline=2 → videoSlots=2
        // Better: remaining=3, videoCap=2, 1 VIDEO pin → cutline=2, videoSlots=min(2-0-1,2)=1
        when(quotaService.getStatus()).thenReturn(new MarketingQuotaService.QuotaStatus(
            6, 2, 0, 0, 3L));
        when(scoreWeightService.getWeights()).thenReturn(new MarketingScoreWeightService.Weights(
            0.1, 1.0, 0.5));

        Instant t0 = Instant.parse("2026-08-08T10:00:00Z");
        List<HoldingCandidateProjection> candidates = List.of(
            candidate("pinned", 200, 0, 0, t0.plusSeconds(4)),
            candidate("a1", 150, 0, 0, t0.plusSeconds(3)),
            candidate("a2", 100, 0, 0, t0.plusSeconds(2)),
            candidate("a3", 50, 0, 0, t0.plusSeconds(1))
        );
        when(holdingRepository.findActiveCandidates()).thenReturn(candidates);

        MarketingHolding pin = MarketingHolding.builder()
            .postId("pinned")
            .status(MarketingHoldingStatus.PINNED)
            .pinFormat(MarketingPinFormat.VIDEO)
            .draftJson("{}")
            .build();
        MarketingHolding h1 = MarketingHolding.builder()
            .postId("a1").status(MarketingHoldingStatus.IN_POOL).draftJson("{}").build();
        MarketingHolding h2 = MarketingHolding.builder()
            .postId("a2").status(MarketingHoldingStatus.IN_POOL).draftJson("{}").build();
        MarketingHolding h3 = MarketingHolding.builder()
            .postId("a3").status(MarketingHoldingStatus.IN_POOL).draftJson("{}").build();

        when(holdingRepository.findByPostIdIn(anyCollection())).thenReturn(List.of(pin, h1, h2, h3));
        stubStatusQueries(List.of(pin), List.of(h1, h2, h3));
        when(holdingRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postRepository.findAllById(any())).thenReturn(List.of(
            post("pinned", "Pinned", t0.plusSeconds(4)),
            post("a1", "A1", t0.plusSeconds(3)),
            post("a2", "A2", t0.plusSeconds(2)),
            post("a3", "A3", t0.plusSeconds(1))
        ));

        MarketingHoldingService.HoldingBoard board = service.getBoard();

        assertThat(board.meta().remainingPool()).isEqualTo(3);
        assertThat(board.meta().cutlineN()).isEqualTo(2); // 3 - 1 pin

        Map<String, MarketingHoldingService.BoardItem> byId = board.items().stream()
            .collect(Collectors.toMap(
                MarketingHoldingService.BoardItem::postId, i -> i));

        assertThat(byId.get("pinned").status()).isEqualTo(MarketingHoldingStatus.PINNED);
        assertThat(byId.get("pinned").pinFormat()).isEqualTo("VIDEO");
        assertThat(byId.get("pinned").projectedFormat()).isEqualTo("VIDEO");

        // autoInCut: a1 (slot1 → VIDEO), a2 (slot2 → TEXT); a3 OUT
        assertThat(byId.get("a1").status()).isEqualTo(MarketingHoldingStatus.IN_POOL);
        assertThat(byId.get("a1").projectedFormat()).isEqualTo("VIDEO");
        assertThat(byId.get("a2").status()).isEqualTo(MarketingHoldingStatus.IN_POOL);
        assertThat(byId.get("a2").projectedFormat()).isEqualTo("TEXT");
        assertThat(byId.get("a3").status()).isEqualTo(MarketingHoldingStatus.OUT_OF_CUT);
        assertThat(byId.get("a3").projectedFormat()).isEqualTo("OUT_OF_CUT");
    }

    @Test
    void pin_setsPinnedAndPushesLowestAutoOutOfCut() {
        when(quotaService.getStatus()).thenReturn(new MarketingQuotaService.QuotaStatus(
            6, 3, 0, 0, 2L)); // remaining 2
        when(scoreWeightService.getWeights()).thenReturn(new MarketingScoreWeightService.Weights(
            0.1, 1.0, 0.5));

        Instant t0 = Instant.parse("2026-08-08T10:00:00Z");
        // After pin: reserved=1, cutline=1 → push rank>1 autos
        List<HoldingCandidateProjection> candidates = List.of(
            candidate("p1", 100, 0, 0, t0.plusSeconds(3)),
            candidate("p2", 50, 0, 0, t0.plusSeconds(2)),
            candidate("p3", 10, 0, 0, t0.plusSeconds(1))
        );
        when(holdingRepository.findActiveCandidates()).thenReturn(candidates);

        MarketingHolding target = MarketingHolding.builder()
            .postId("p3")
            .status(MarketingHoldingStatus.OUT_OF_CUT)
            .draftJson("{\"title\":\"t3\"}")
            .build();
        MarketingHolding top = MarketingHolding.builder()
            .postId("p1")
            .status(MarketingHoldingStatus.IN_POOL)
            .draftJson("{}")
            .build();
        MarketingHolding mid = MarketingHolding.builder()
            .postId("p2")
            .status(MarketingHoldingStatus.IN_POOL)
            .draftJson("{}")
            .build();

        when(holdingRepository.findById("p3")).thenReturn(Optional.of(target));
        stubStatusQueries(List.of(), List.of(top, mid, target));
        when(holdingRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postRepository.findById("p3")).thenReturn(Optional.of(post("p3", "P3", t0)));

        MarketingHoldingService.BoardItem item = service.pin("p3", MarketingPinFormat.TEXT);

        assertThat(item.status()).isEqualTo(MarketingHoldingStatus.PINNED);
        assertThat(item.pinFormat()).isEqualTo("TEXT");
        assertThat(item.projectedFormat()).isEqualTo("TEXT");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarketingHolding>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(holdingRepository).saveAll(saveCaptor.capture());
        List<MarketingHolding> saved = saveCaptor.getValue();
        assertThat(saved).anySatisfy(h -> {
            assertThat(h.getPostId()).isEqualTo("p3");
            assertThat(h.getStatus()).isEqualTo(MarketingHoldingStatus.PINNED);
            assertThat(h.getPinFormat()).isEqualTo(MarketingPinFormat.TEXT);
        });
        assertThat(saved).anySatisfy(h -> {
            assertThat(h.getPostId()).isEqualTo("p2");
            assertThat(h.getStatus()).isEqualTo(MarketingHoldingStatus.OUT_OF_CUT);
        });
        // p1 rank 1 stays IN_POOL (not in pushed list / still IN_POOL)
        assertThat(saved).noneMatch(h ->
            "p1".equals(h.getPostId()) && h.getStatus() == MarketingHoldingStatus.OUT_OF_CUT);
    }

    @Test
    void pin_whenAllPoolSlotsHeldByPins_returns400() {
        when(quotaService.getStatus()).thenReturn(new MarketingQuotaService.QuotaStatus(
            6, 3, 0, 0, 1L)); // remaining 1
        MarketingHolding otherPin = MarketingHolding.builder()
            .postId("other")
            .status(MarketingHoldingStatus.PINNED)
            .pinFormat(MarketingPinFormat.TEXT)
            .draftJson("{}")
            .build();
        MarketingHolding target = MarketingHolding.builder()
            .postId("p1")
            .status(MarketingHoldingStatus.IN_POOL)
            .draftJson("{}")
            .build();

        when(holdingRepository.findById("p1")).thenReturn(Optional.of(target));
        when(holdingRepository.findByStatusIn(argThat(s ->
            s != null && s.contains(MarketingHoldingStatus.PINNED))))
            .thenReturn(List.of(otherPin));

        assertThatThrownBy(() -> service.pin("p1", MarketingPinFormat.TEXT))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(rse.getReason()).containsIgnoringCase("held by pins");
            });
        verify(holdingRepository, never()).saveAll(any());
    }

    @Test
    void pin_whenVideoCapExhaustedByPins_returns400() {
        when(quotaService.getStatus()).thenReturn(new MarketingQuotaService.QuotaStatus(
            6, 1, 0, 0, 3L)); // videoCap=1, remaining=3
        MarketingHolding videoPin = MarketingHolding.builder()
            .postId("v1")
            .status(MarketingHoldingStatus.PINNED)
            .pinFormat(MarketingPinFormat.VIDEO)
            .draftJson("{}")
            .build();
        MarketingHolding target = MarketingHolding.builder()
            .postId("p1")
            .status(MarketingHoldingStatus.IN_POOL)
            .draftJson("{}")
            .build();

        when(holdingRepository.findById("p1")).thenReturn(Optional.of(target));
        when(holdingRepository.findByStatusIn(argThat(s ->
            s != null && s.contains(MarketingHoldingStatus.PINNED))))
            .thenReturn(List.of(videoPin));

        assertThatThrownBy(() -> service.pin("p1", MarketingPinFormat.VIDEO))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(rse.getReason()).containsIgnoringCase("video");
            });
        verify(holdingRepository, never()).saveAll(any());
    }

    @Test
    void unpin_releasesReserve_andSetsStatusByCutline() {
        when(quotaService.getStatus()).thenReturn(new MarketingQuotaService.QuotaStatus(
            6, 3, 0, 0, 2L)); // remaining 2; after unpin cutline=2
        when(scoreWeightService.getWeights()).thenReturn(new MarketingScoreWeightService.Weights(
            0.1, 1.0, 0.5));

        Instant t0 = Instant.parse("2026-08-08T10:00:00Z");
        List<HoldingCandidateProjection> candidates = List.of(
            candidate("p1", 100, 0, 0, t0.plusSeconds(2)),
            candidate("p2", 10, 0, 0, t0.plusSeconds(1)) // rank 2 ≤ cutline 2 → IN_POOL
        );
        when(holdingRepository.findActiveCandidates()).thenReturn(candidates);

        MarketingHolding pinned = MarketingHolding.builder()
            .postId("p2")
            .status(MarketingHoldingStatus.PINNED)
            .pinFormat(MarketingPinFormat.TEXT)
            .draftJson("{}")
            .build();

        when(holdingRepository.findById("p2")).thenReturn(Optional.of(pinned));
        when(holdingRepository.findByStatusIn(argThat(s ->
            s != null && s.contains(MarketingHoldingStatus.PINNED))))
            .thenReturn(List.of(pinned));
        when(holdingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postRepository.findById("p2")).thenReturn(Optional.of(post("p2", "P2", t0)));

        MarketingHoldingService.BoardItem item = service.unpin("p2");

        assertThat(item.status()).isEqualTo(MarketingHoldingStatus.IN_POOL);
        assertThat(item.pinFormat()).isNull();
        assertThat(pinned.getPinFormat()).isNull();
        assertThat(pinned.getStatus()).isEqualTo(MarketingHoldingStatus.IN_POOL);
    }

    @Test
    void unpin_whenOutsideNewCutline_setsOutOfCut() {
        when(quotaService.getStatus()).thenReturn(new MarketingQuotaService.QuotaStatus(
            6, 3, 0, 0, 1L)); // remaining 1; after unpin cutline=1
        when(scoreWeightService.getWeights()).thenReturn(new MarketingScoreWeightService.Weights(
            0.1, 1.0, 0.5));

        Instant t0 = Instant.parse("2026-08-08T10:00:00Z");
        List<HoldingCandidateProjection> candidates = List.of(
            candidate("p1", 100, 0, 0, t0.plusSeconds(2)),
            candidate("p2", 10, 0, 0, t0.plusSeconds(1)) // rank 2 > cutline 1
        );
        when(holdingRepository.findActiveCandidates()).thenReturn(candidates);

        MarketingHolding pinned = MarketingHolding.builder()
            .postId("p2")
            .status(MarketingHoldingStatus.PINNED)
            .pinFormat(MarketingPinFormat.VIDEO)
            .draftJson("{}")
            .build();

        when(holdingRepository.findById("p2")).thenReturn(Optional.of(pinned));
        when(holdingRepository.findByStatusIn(argThat(s ->
            s != null && s.contains(MarketingHoldingStatus.PINNED))))
            .thenReturn(List.of(pinned));
        when(holdingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postRepository.findById("p2")).thenReturn(Optional.of(post("p2", "P2", t0)));

        MarketingHoldingService.BoardItem item = service.unpin("p2");

        assertThat(item.status()).isEqualTo(MarketingHoldingStatus.OUT_OF_CUT);
        assertThat(item.projectedFormat()).isEqualTo("OUT_OF_CUT");
        assertThat(item.pinFormat()).isNull();
    }

    @Test
    void applyCutlineStatus_outOfCutOutsideCutline_staysOutOfCut() {
        assertThat(MarketingHoldingService.applyCutlineStatus(
            MarketingHoldingStatus.OUT_OF_CUT, false, 5, 2))
            .isEqualTo(MarketingHoldingStatus.OUT_OF_CUT);
    }

    @Test
    void applyCutlineStatus_inPoolOutsideCutline_becomesOutOfCut() {
        assertThat(MarketingHoldingService.applyCutlineStatus(
            MarketingHoldingStatus.IN_POOL, false, 5, 2))
            .isEqualTo(MarketingHoldingStatus.OUT_OF_CUT);
    }

    @Test
    void effectiveCutline_subtractsSoftReserve() {
        assertThat(MarketingHoldingService.effectiveCutline(6, 2)).isEqualTo(4);
        assertThat(MarketingHoldingService.effectiveCutline(2, 2)).isEqualTo(0);
        assertThat(MarketingHoldingService.effectiveCutline(1, 3)).isEqualTo(0);
    }

    @Test
    void isMariaRecordChanged_detectsError1020() {
        SQLException sql = new SQLException(
            "Record has changed since last read in table 'marketing_holding'", "HY000", 1020);
        assertThat(MarketingHoldingService.isMariaRecordChanged(new RuntimeException(sql))).isTrue();
        assertThat(MarketingHoldingService.isMariaRecordChanged(
            new JpaSystemException(new RuntimeException(sql)))).isTrue();
        assertThat(MarketingHoldingService.isMariaRecordChanged(new RuntimeException("other"))).isFalse();
    }

    @Test
    void getBoard_withVideoSlotsRemaining_projectsVideoOnTopAutos() {
        // dailyVideoCap=3, videosToday=1 (COMMITTED only) → raw video slots=2, cutline=5
        when(quotaService.getStatus()).thenReturn(new MarketingQuotaService.QuotaStatus(
            6, 3, 1, 0, 5L));
        when(scoreWeightService.getWeights()).thenReturn(new MarketingScoreWeightService.Weights(
            0.1, 1.0, 0.5));

        Instant t0 = Instant.parse("2026-08-08T10:00:00Z");
        List<HoldingCandidateProjection> candidates = List.of(
            candidate("v1", 300, 0, 0, t0.plusSeconds(5)),
            candidate("v2", 200, 0, 0, t0.plusSeconds(4)),
            candidate("t1", 100, 0, 0, t0.plusSeconds(3)),
            candidate("o1", 50, 0, 0, t0.plusSeconds(2))
        );
        when(holdingRepository.findActiveCandidates()).thenReturn(candidates);
        when(holdingRepository.findByPostIdIn(anyCollection())).thenReturn(List.of());
        stubStatusQueries(List.of(), List.of());
        when(holdingRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postRepository.findAllById(any())).thenReturn(List.of(
            post("v1", "V1", t0.plusSeconds(5)),
            post("v2", "V2", t0.plusSeconds(4)),
            post("t1", "T1", t0.plusSeconds(3)),
            post("o1", "O1", t0.plusSeconds(2))
        ));
        when(briefSeeder.seedFromPost(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            return BriefDto.builder().title(p.getTitle()).build();
        });

        MarketingHoldingService.HoldingBoard board = service.getBoard();
        Map<String, MarketingHoldingService.BoardItem> byId = board.items().stream()
            .collect(Collectors.toMap(MarketingHoldingService.BoardItem::postId, i -> i));

        assertThat(byId.get("v1").projectedFormat()).isEqualTo("VIDEO");
        assertThat(byId.get("v2").projectedFormat()).isEqualTo("VIDEO");
        assertThat(byId.get("t1").projectedFormat()).isEqualTo("TEXT");
        assertThat(byId.get("o1").projectedFormat()).isEqualTo("TEXT");
    }

    @Test
    void getBoard_prependsOverdueDueHoldings_andDoesNotDemoteThem() {
        when(quotaService.getStatus()).thenReturn(new MarketingQuotaService.QuotaStatus(
            6, 3, 0, 0, 2L));
        when(scoreWeightService.getWeights()).thenReturn(new MarketingScoreWeightService.Weights(
            0.1, 1.0, 0.5));
        Instant t0 = Instant.parse("2026-08-08T10:00:00Z");
        List<HoldingCandidateProjection> candidates = List.of(
            candidate("p1", 100, 10, 5, t0));
        when(holdingRepository.findActiveCandidates()).thenReturn(candidates);

        MarketingHolding overdue = MarketingHolding.builder()
            .postId("old")
            .status(MarketingHoldingStatus.OUT_OF_CUT)
            .draftJson("{}")
            .build();
        DueHoldingProjection due = new DueHoldingProjection() {
            @Override public String getPostId() { return "old"; }
            @Override public String getStatus() { return "OUT_OF_CUT"; }
            @Override public String getPinFormat() { return null; }
            @Override public Double getScoreSnapshot() { return null; }
            @Override public Instant getPostCreatedAt() { return t0.minus(25, ChronoUnit.HOURS); }
            @Override public Number getViewCount() { return 0; }
            @Override public Number getCommentCount() { return 0; }
            @Override public Number getVoteCount() { return 0; }
            @Override public Number getAuthorVoteCount() { return 0; }
            @Override public Number getHasPartner() { return 0; }
            @Override public String getHookText() { return null; }
        };
        when(holdingRepository.findDueHoldings(any())).thenReturn(List.of(due));

        when(holdingRepository.findByPostIdIn(anyCollection())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Collection<String> ids = inv.getArgument(0);
            List<MarketingHolding> found = new ArrayList<>();
            if (ids.contains("old")) {
                found.add(overdue);
            }
            return found;
        });
        when(postRepository.findAllById(any())).thenAnswer(inv -> {
            Collection<?> ids = inv.getArgument(0);
            List<Post> posts = new ArrayList<>();
            if (ids.contains("p1")) {
                posts.add(post("p1", "Fresh", t0));
            }
            if (ids.contains("old")) {
                posts.add(post("old", "Overdue story", t0.minus(25, ChronoUnit.HOURS)));
            }
            return posts;
        });
        when(briefSeeder.seedFromPost(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            return BriefDto.builder().title("seeded-" + p.getId()).build();
        });
        stubStatusQueries(List.of(), List.of(overdue));
        when(holdingRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        MarketingHoldingService.HoldingBoard board = service.getBoard();

        assertThat(board.items()).hasSize(2);
        assertThat(board.items().get(0).postId()).isEqualTo("old");
        assertThat(board.items().get(0).overdue()).isTrue();
        assertThat(board.items().get(0).title()).isEqualTo("Overdue story");
        assertThat(board.items().get(1).postId()).isEqualTo("p1");
        assertThat(board.items().get(1).overdue()).isFalse();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MarketingHolding>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(holdingRepository).saveAll(saveCaptor.capture());
        assertThat(saveCaptor.getValue()).noneMatch(h -> "old".equals(h.getPostId())
            && h.getStatus() != MarketingHoldingStatus.OUT_OF_CUT);
        assertThat(overdue.getStatus()).isEqualTo(MarketingHoldingStatus.OUT_OF_CUT);
    }

    @Test
    void rankCandidates_excludesNonConflictContent_andRecordsExclusionOnce() {
        when(scoreWeightService.getWeights()).thenReturn(new MarketingScoreWeightService.Weights(
            0.1, 1.0, 0.5));
        when(exclusionRepository.existsById(any())).thenReturn(false);

        List<HoldingCandidateProjection> candidates = List.of(
            candidate("post-conflict", 100, 10, 5, Instant.parse("2026-08-08T10:00:00Z"),
                "아내가 상의없이 오백만원 빌려준 걸 알았다", "남편이 몰래 오백만원을 빌려줬다는 걸 알고 화가 났다"),
            candidate("post-trivia", 50, 5, 2, Instant.parse("2026-08-08T09:00:00Z"),
                "덕혜옹주가 일본 친구한테 털어놓은 고종 독살 얘기", "1919년 총독부 명령으로 독살했다는 문건이 있다고 함"),
            candidate("post-listicle", 40, 4, 1, Instant.parse("2026-08-08T08:00:00Z"),
                "여초회사 1년 근무자가 쓰는 장단점", "장점부터 말하면... 단점도 당연히 있는데")
        );
        when(holdingRepository.findActiveCandidates()).thenReturn(candidates);

        List<MarketingHoldingService.RankedCandidate> ranked = service.rankCandidates(
            scoreWeightService.getWeights());

        assertThat(ranked)
            .extracting(MarketingHoldingService.RankedCandidate::postId)
            .containsExactly("post-conflict");

        ArgumentCaptor<MarketingHoldingExclusion> exclusionCaptor =
            ArgumentCaptor.forClass(MarketingHoldingExclusion.class);
        verify(exclusionRepository, org.mockito.Mockito.times(2)).save(exclusionCaptor.capture());
        Map<String, String> reasonsByPostId = exclusionCaptor.getAllValues().stream()
            .collect(Collectors.toMap(
                MarketingHoldingExclusion::getPostId, MarketingHoldingExclusion::getReason));
        assertThat(reasonsByPostId)
            .containsEntry("post-trivia", MarketingHoldingContentGuard.REASON_YEAR_TRIVIA_PATTERN)
            .containsEntry("post-listicle", MarketingHoldingContentGuard.REASON_PROS_CONS_LISTICLE);
    }

    @Test
    void rankCandidates_doesNotReRecordExclusionAlreadyPersisted() {
        when(exclusionRepository.existsById("post-trivia")).thenReturn(true);

        List<HoldingCandidateProjection> candidates = List.of(
            candidate("post-trivia", 50, 5, 2, Instant.parse("2026-08-08T09:00:00Z"),
                "덕혜옹주가 일본 친구한테 털어놓은 고종 독살 얘기", "1919년 독살했다는 문건이 있다고 함"));
        when(holdingRepository.findActiveCandidates()).thenReturn(candidates);

        List<MarketingHoldingService.RankedCandidate> ranked = service.rankCandidates(
            new MarketingScoreWeightService.Weights(0.1, 1.0, 0.5));

        assertThat(ranked).isEmpty();
        verify(exclusionRepository, never()).save(any());
    }

    private void stubStatusQueries(
            List<MarketingHolding> pinned,
            List<MarketingHolding> activeNonPinned) {
        when(holdingRepository.findByStatusIn(any())).thenAnswer(inv -> {
            Collection<MarketingHoldingStatus> statuses = inv.getArgument(0);
            if (statuses.contains(MarketingHoldingStatus.PINNED)
                && !statuses.contains(MarketingHoldingStatus.IN_POOL)) {
                return pinned;
            }
            return activeNonPinned;
        });
    }

    private static Post post(String id, String title, Instant createdAt) {
        return Post.builder()
            .id(id)
            .title(title)
            .createdAt(createdAt)
            .build();
    }

    private static HoldingCandidateProjection candidate(
            String id, int views, long comments, long votes, Instant createdAt) {
        return candidate(id, views, comments, votes, createdAt, null, null);
    }

    private static HoldingCandidateProjection candidate(
            String id, int views, long comments, long votes, Instant createdAt,
            String title, String bodyPublished) {
        HoldingCandidateProjection projection = mock(HoldingCandidateProjection.class);
        lenient().when(projection.getId()).thenReturn(id);
        lenient().when(projection.getViewCount()).thenReturn(views);
        lenient().when(projection.getCommentCount()).thenReturn(comments);
        lenient().when(projection.getVoteCount()).thenReturn(votes);
        lenient().when(projection.getCreatedAt()).thenReturn(createdAt);
        lenient().when(projection.getTitle()).thenReturn(title);
        lenient().when(projection.getBodyPublished()).thenReturn(bodyPublished);
        return projection;
    }
}
