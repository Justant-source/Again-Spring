package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.XFollowAccount;
import com.againspring.domain.marketing.XFollowEvent;
import com.againspring.domain.marketing.XOpsAction;
import com.againspring.repository.ai.SystemSettingRepository;
import com.againspring.repository.marketing.XFollowAccountRepository;
import com.againspring.repository.marketing.XFollowEventRepository;
import com.againspring.repository.marketing.XOpsActionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class XFollowTrackerServiceTest {

    private final SystemSettingRepository settings = mock(SystemSettingRepository.class);
    private final AsmClient asm = mock(AsmClient.class);
    private final XFollowAccountRepository accounts = mock(XFollowAccountRepository.class);
    private final XFollowEventRepository events = mock(XFollowEventRepository.class);
    private final XOpsActionRepository ops = mock(XOpsActionRepository.class);
    private final XFollowTrackerService service = new XFollowTrackerService(settings, asm, accounts, events, ops,
        new TransactionTemplate(mock(PlatformTransactionManager.class)));

    private final Instant now = Instant.parse("2026-11-06T00:00:00Z");
    private final List<XFollowAccount> accountStore = new ArrayList<>();
    private final List<XFollowEvent> eventStore = new ArrayList<>();
    private final List<XOpsAction> follows = new ArrayList<>();
    private final List<XOpsAction> outbound = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(accounts.findAll()).thenAnswer(i -> new ArrayList<>(accountStore));
        when(accounts.save(any())).thenAnswer(i -> {
            XFollowAccount a = i.getArgument(0);
            if (!accountStore.contains(a)) {
                accountStore.add(a);
            }
            return a;
        });
        when(events.save(any())).thenAnswer(i -> {
            eventStore.add(i.getArgument(0));
            return i.getArgument(0);
        });
        when(events.existsByHandleAndDirectionAndKindAndOccurredAt(any(), any(), any(), any())).thenAnswer(i ->
            eventStore.stream().anyMatch(e -> e.getHandle().equals(i.getArgument(0))
                && e.getDirection() == i.getArgument(1) && e.getKind() == i.getArgument(2)
                && e.getOccurredAt().equals(i.getArgument(3))));
        when(ops.findByStatusAndKindInAndCreatedAtGreaterThanEqual(any(), anyCollection(), any())).thenAnswer(i -> {
            java.util.Collection<XOpsAction.Kind> kinds = i.getArgument(1);
            return kinds.contains(XOpsAction.Kind.FOLLOW) ? follows : outbound;
        });
        when(asm.getXEngagers()).thenReturn(new AsmClient.XEngagers("us", true, List.of()));
    }

    private static AsmClient.XFollowingEntry entry(String h, boolean followsYou, int order) {
        return new AsmClient.XFollowingEntry(h, followsYou, false, order);
    }

    private void snapshot(boolean complete, AsmClient.XFollowingEntry... es) {
        when(asm.getXFollowingSnapshot()).thenReturn(new AsmClient.XFollowingSnapshot("us", complete, List.of(es)));
    }

    private void ledgerFollow(String handle, Instant at) {
        follows.add(XOpsAction.builder().kind(XOpsAction.Kind.FOLLOW).status(XOpsAction.Status.POSTED)
            .targetTweetId("follow:" + handle).createdAt(at).build());
    }

    private XFollowAccount acct(String h) {
        return accountStore.stream().filter(a -> a.getHandle().equals(h)).findFirst().orElseThrow();
    }

    private long count(String h, XFollowEvent.Direction d, XFollowEvent.Kind k) {
        return eventStore.stream().filter(e -> e.getHandle().equals(h) && e.getDirection() == d && e.getKind() == k).count();
    }

    @Test
    void newAccountUpsertedNormalizedWithFirstSeen() {
        snapshot(true, entry("@Alice", false, 0));
        var r = service.sync(now);
        assertTrue(r.ok());
        assertEquals(1, r.newAccounts());
        XFollowAccount a = acct("alice");
        assertEquals(XFollowAccount.Status.FOLLOWING, a.getStatus());
        assertEquals(now, a.getFirstSeenAt());
        assertEquals(now, a.getFollowedAt());
        assertEquals(XFollowAccount.FollowedAtSource.FIRST_SEEN, a.getFollowedAtSource());
        assertEquals(1, count("alice", XFollowEvent.Direction.OUT, XFollowEvent.Kind.FOLLOW));
    }

    @Test
    void ledgerAndInterpolationUseNewerAnchor() {
        Instant t10 = now.minusSeconds(1000);
        Instant t30 = now.minusSeconds(3000);
        ledgerFollow("a", t10);
        ledgerFollow("b", t30);
        snapshot(true,
            entry("new", false, 0),   // newer than newest anchor
            entry("a", false, 2),
            entry("mid", false, 4),   // between a(2) and b(6)
            entry("b", false, 6),
            entry("old", false, 9));  // older than oldest anchor
        service.sync(now);
        assertEquals(XFollowAccount.FollowedAtSource.FIRST_SEEN, acct("new").getFollowedAtSource());
        assertEquals(now, acct("new").getFollowedAt());
        assertEquals(XFollowAccount.FollowedAtSource.LEDGER, acct("a").getFollowedAtSource());
        assertEquals(t10, acct("a").getFollowedAt());
        assertEquals(XFollowAccount.FollowedAtSource.ESTIMATED, acct("mid").getFollowedAtSource());
        assertEquals(t10, acct("mid").getFollowedAt());
        assertEquals(XFollowAccount.FollowedAtSource.ESTIMATED, acct("old").getFollowedAtSource());
        assertEquals(t30, acct("old").getFollowedAt());
    }

    @Test
    void existingFollowedAtNotOverwritten() {
        Instant earlier = now.minusSeconds(99999);
        accountStore.add(XFollowAccount.builder().handle("a").followedAt(earlier)
            .followedAtSource(XFollowAccount.FollowedAtSource.ESTIMATED)
            .firstSeenAt(earlier).lastSeenFollowingAt(earlier).build());
        ledgerFollow("a", now.minusSeconds(5));
        snapshot(true, entry("a", false, 0));
        service.sync(now);
        assertEquals(earlier, acct("a").getFollowedAt());
    }

    @Test
    void incompleteSnapshotLeavesAbsentAccountsUntouched() {
        Instant old = now.minusSeconds(86400);
        accountStore.add(XFollowAccount.builder().handle("gone").followedAt(old)
            .firstSeenAt(old).lastSeenFollowingAt(old).build());
        snapshot(false, entry("x", false, 0));
        var r = service.sync(now);
        assertFalse(r.snapshotComplete());
        assertEquals(XFollowAccount.Status.FOLLOWING, acct("gone").getStatus());
        assertEquals(old, acct("gone").getLastSeenFollowingAt());
    }

    @Test
    void completeSnapshotNeverMarksUnfollowed() {
        Instant old = now.minusSeconds(86400);
        accountStore.add(XFollowAccount.builder().handle("gone").followedAt(old)
            .firstSeenAt(old).lastSeenFollowingAt(old).build());
        snapshot(true, entry("x", false, 0));
        service.sync(now);
        assertEquals(XFollowAccount.Status.FOLLOWING, acct("gone").getStatus());
    }

    @Test
    void refollowRevivesUnfollowed() {
        Instant old = now.minusSeconds(86400);
        accountStore.add(XFollowAccount.builder().handle("r").status(XFollowAccount.Status.UNFOLLOWED)
            .followedAt(old).unfollowedAt(old).unfollowReason("X").firstSeenAt(old).lastSeenFollowingAt(old).build());
        snapshot(true, entry("r", false, 0));
        service.sync(now);
        XFollowAccount a = acct("r");
        assertEquals(XFollowAccount.Status.FOLLOWING, a.getStatus());
        assertEquals(now, a.getFollowedAt());
        assertEquals(XFollowAccount.FollowedAtSource.FIRST_SEEN, a.getFollowedAtSource());
        assertNull(a.getUnfollowedAt());
    }

    @Test
    void followsUsTransitionRecordsFollowBackOnce() {
        snapshot(true, entry("m", false, 0));
        service.sync(now);
        assertFalse(acct("m").isFollowsUs());
        snapshot(true, entry("m", true, 0));
        var r = service.sync(now.plusSeconds(60));
        assertEquals(1, r.newMutual());
        assertTrue(acct("m").isFollowsUs());
        assertEquals(now.plusSeconds(60), acct("m").getFollowsUsSince());
        service.sync(now.plusSeconds(120));
        assertEquals(1, count("m", XFollowEvent.Direction.IN, XFollowEvent.Kind.FOLLOW_BACK));
    }

    @Test
    void ledgerFollowTreatedAsMutual() {
        ledgerFollow("l", now.minusSeconds(10));
        snapshot(true, entry("l", false, 0));
        service.sync(now);
        assertTrue(acct("l").isFollowsUs());
    }

    @Test
    void engagersOnlyForTrackedAccountsAndDedup() {
        snapshot(true, entry("t", false, 0));
        Instant at1 = now.minusSeconds(100);
        Instant at2 = now.minusSeconds(50);
        when(asm.getXEngagers()).thenReturn(new AsmClient.XEngagers("us", true, List.of(
            new AsmClient.XEngagerEvent("T", "LIKE", at1),
            new AsmClient.XEngagerEvent("t", "like", at1),
            new AsmClient.XEngagerEvent("t", "REPLY", at2),
            new AsmClient.XEngagerEvent("stranger", "LIKE", at1))));
        var r = service.sync(now);
        assertEquals(2, r.inboundEvents());
        assertEquals(at2, acct("t").getLastInboundInteractionAt());
        assertEquals(0, count("stranger", XFollowEvent.Direction.IN, XFollowEvent.Kind.LIKE));
        assertEquals(2, eventStore.stream().filter(e -> e.getDirection() == XFollowEvent.Direction.IN
            && e.getKind() != XFollowEvent.Kind.FOLLOW_BACK).count());
        // second sync: same events ignored
        assertEquals(0, service.sync(now.plusSeconds(1)).inboundEvents());
    }

    @Test
    void engagersFailureSkipsOnlyThatStep() {
        snapshot(true, entry("t", false, 0));
        when(asm.getXEngagers()).thenThrow(new RuntimeException("boom"));
        var r = service.sync(now);
        assertTrue(r.ok());
        assertEquals(0, r.inboundEvents());
    }

    @Test
    void outboundLedgerRecordedForTrackedOnly() {
        snapshot(true, entry("t", false, 0));
        Instant at = now.minusSeconds(500);
        outbound.add(XOpsAction.builder().kind(XOpsAction.Kind.OUTBOUND).status(XOpsAction.Status.POSTED)
            .targetAuthorHandle("t").targetTweetId("111").createdAt(at).build());
        outbound.add(XOpsAction.builder().kind(XOpsAction.Kind.OUTBOUND).status(XOpsAction.Status.POSTED)
            .targetAuthorHandle("zzz").targetTweetId("222").createdAt(at).build());
        service.sync(now);
        service.sync(now.plusSeconds(1));
        assertEquals(1, count("t", XFollowEvent.Direction.OUT, XFollowEvent.Kind.REPLY));
        assertEquals(0, count("zzz", XFollowEvent.Direction.OUT, XFollowEvent.Kind.REPLY));
    }

    @Test
    void snapshotFailureWritesNothing() {
        when(asm.getXFollowingSnapshot()).thenThrow(new RuntimeException("down"));
        var r = service.sync(now);
        assertFalse(r.ok());
        verify(accounts, never()).save(any());
        verify(events, never()).save(any());
    }

    @Test
    void disabledFlagSkipsEverything() {
        when(settings.findById(XFollowTrackerService.KEY_ENABLED)).thenReturn(Optional.of(
            SystemSetting.builder().settingKey(XFollowTrackerService.KEY_ENABLED).settingValue("false").build()));
        var r = service.sync(now);
        assertFalse(r.ok());
        verify(asm, never()).getXFollowingSnapshot();
    }
}
