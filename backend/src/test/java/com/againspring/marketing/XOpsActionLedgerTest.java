package com.againspring.marketing;

import com.againspring.domain.marketing.XOpsAction;
import com.againspring.repository.marketing.XOpsActionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class XOpsActionLedgerTest {

    @Mock
    private XOpsActionRepository repository;

    @InjectMocks
    private XOpsActionLedger ledger;

    private final List<XOpsAction> store = new ArrayList<>();

    @BeforeEach
    void setUp() {
        store.clear();
        when(repository.save(any(XOpsAction.class))).thenAnswer(inv -> {
            XOpsAction row = inv.getArgument(0);
            store.add(row);
            return row;
        });
        when(repository.existsByTargetTweetId(any())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            if (id == null) {
                return false;
            }
            return store.stream().anyMatch(a -> id.equals(a.getTargetTweetId()));
        });
    }

    @Test
    void alreadyHandled_trueAfterRecordPostedOrSkipped() {
        Instant now = Instant.parse("2026-08-31T00:00:00Z");
        assertThat(ledger.alreadyHandled("tw-1")).isFalse();

        ledger.recordPosted(XOpsAction.Kind.INBOUND, "tw-1", "parent-1", "our-1", "posted-1", "댓글", now);
        assertThat(ledger.alreadyHandled("tw-1")).isTrue();

        assertThat(ledger.alreadyHandled("tw-2")).isFalse();
        ledger.recordSkipped(XOpsAction.Kind.INBOUND, "tw-2", "NO_VOICE", now);
        assertThat(ledger.alreadyHandled("tw-2")).isTrue();

        assertThat(ledger.alreadyHandled("tw-3")).isFalse();
        ledger.recordFailed(XOpsAction.Kind.INBOUND, "tw-3", "LLM_ERROR", now);
        assertThat(ledger.alreadyHandled("tw-3")).isTrue();
    }

    @Test
    void countPostedToday_respectsKstDayAndIgnoresSkipped() {
        when(repository.countByKindAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            any(), any(), any(), any())).thenAnswer(inv -> {
            XOpsAction.Kind kind = inv.getArgument(0);
            XOpsAction.Status status = inv.getArgument(1);
            Instant start = inv.getArgument(2);
            Instant end = inv.getArgument(3);
            return store.stream()
                .filter(a -> a.getKind() == kind)
                .filter(a -> a.getStatus() == status)
                .filter(a -> !a.getCreatedAt().isBefore(start) && a.getCreatedAt().isBefore(end))
                .count();
        });

        ZoneId kst = ZoneId.of("Asia/Seoul");
        Instant kstMidnight = LocalDate.of(2026, 8, 31).atStartOfDay(kst).toInstant();
        Instant justBefore = kstMidnight.minusSeconds(1);
        Instant justAfter = kstMidnight.plusSeconds(1);
        Instant noonKst = LocalDate.of(2026, 8, 31).atTime(12, 0).atZone(kst).toInstant();

        ledger.recordPosted(XOpsAction.Kind.INBOUND, "prev-day", null, null, "p0", "어제", justBefore);
        ledger.recordSkipped(XOpsAction.Kind.INBOUND, "skip-today", "NO_VOICE", justAfter);
        ledger.recordPosted(XOpsAction.Kind.OUTBOUND, "other-kind", null, null, "p1", "다른 kind", justAfter);
        ledger.recordPosted(XOpsAction.Kind.INBOUND, "today-1", null, null, "p2", "오늘", justAfter);

        assertThat(ledger.countPostedToday(XOpsAction.Kind.INBOUND, noonKst)).isEqualTo(1);
    }

    @Test
    void countPostedTodayForOurPost_countsOnlyThatPostOnKstDay() {
        when(repository.countByOurPostTweetIdAndStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            any(), any(), any(), any())).thenAnswer(inv -> {
            String ourPost = inv.getArgument(0);
            XOpsAction.Status status = inv.getArgument(1);
            Instant start = inv.getArgument(2);
            Instant end = inv.getArgument(3);
            return store.stream()
                .filter(a -> ourPost.equals(a.getOurPostTweetId()))
                .filter(a -> a.getStatus() == status)
                .filter(a -> !a.getCreatedAt().isBefore(start) && a.getCreatedAt().isBefore(end))
                .count();
        });

        ZoneId kst = ZoneId.of("Asia/Seoul");
        Instant kstMidnight = LocalDate.of(2026, 8, 31).atStartOfDay(kst).toInstant();
        Instant noonKst = LocalDate.of(2026, 8, 31).atTime(12, 0).atZone(kst).toInstant();

        ledger.recordPosted(XOpsAction.Kind.OUTBOUND, "t1", null, "our-A", "p1", "하나", noonKst);
        ledger.recordPosted(XOpsAction.Kind.OUTBOUND, "t2", null, "our-A", "p2", "둘", noonKst);
        ledger.recordPosted(XOpsAction.Kind.OUTBOUND, "t3", null, "our-B", "p3", "다른 글", noonKst);
        ledger.recordSkipped(XOpsAction.Kind.OUTBOUND, "t4", "CAP", noonKst);
        ledger.recordPosted(XOpsAction.Kind.OUTBOUND, "t5", null, "our-A", "p5", "어제", kstMidnight.minusSeconds(1));

        assertThat(ledger.countPostedTodayForOurPost("our-A", noonKst)).isEqualTo(2);
        assertThat(ledger.countPostedTodayForOurPost("our-B", noonKst)).isEqualTo(1);
    }
}
