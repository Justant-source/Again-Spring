package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.XFollowAccount;
import com.againspring.domain.marketing.XFollowEvent;
import com.againspring.notification.TelegramNotifier;
import com.againspring.repository.ai.SystemSettingRepository;
import com.againspring.repository.marketing.XFollowAccountRepository;
import com.againspring.repository.marketing.XFollowEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class XFollowCleanupServiceTest {

    @Mock SystemSettingRepository settings;
    @Mock AsmClient asm;
    @Mock XFollowTrackerService tracker;
    @Mock XFollowAccountRepository accounts;
    @Mock XFollowEventRepository events;
    @Mock TelegramNotifier telegram;

    XFollowCleanupService service;
    final Map<String, String> cfg = new HashMap<>();
    final Instant now = Instant.parse("2026-11-06T19:00:00Z");

    @BeforeEach
    void setUp() {
        service = new XFollowCleanupService(settings, asm, tracker, accounts, events, telegram);
        service.sleeper = ms -> { };
        when(settings.findById(anyString())).thenAnswer(inv -> {
            String k = inv.getArgument(0);
            return cfg.containsKey(k)
                ? Optional.of(SystemSetting.builder().settingKey(k).settingValue(cfg.get(k)).build())
                : Optional.empty();
        });
        cfg.put(XFollowCleanupService.KEY_ENABLED, "true");
        cfg.put(XFollowCleanupService.KEY_DRY_RUN, "false");
        when(tracker.sync(any())).thenReturn(new XFollowTrackerService.SyncResult(true, true, 10, 0, 0, 0));
        when(events.countByDirectionAndKindAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            any(), any(), any(), any())).thenReturn(0L);
        when(asm.unfollowX(anyList())).thenAnswer(inv ->
            new AsmClient.XUnfollowResult(inv.getArgument(0), List.of()));
    }

    XFollowAccount acc(String h, int followedDaysAgo, Integer followers, Instant checked) {
        return XFollowAccount.builder().handle(h).status(XFollowAccount.Status.FOLLOWING)
            .followedAt(now.minus(Duration.ofDays(followedDaysAgo)))
            .firstSeenAt(now.minus(Duration.ofDays(followedDaysAgo)))
            .lastSeenFollowingAt(now)
            .followerCount(followers).followerCountCheckedAt(checked).build();
    }

    void candidates(XFollowAccount... a) {
        when(accounts.findByStatusAndFollowsUsFalseAndFollowedAtLessThanEqual(
            eq(XFollowAccount.Status.FOLLOWING), any())).thenReturn(List.of(a));
    }

    @Test
    void disabled_doesNothing() {
        cfg.put(XFollowCleanupService.KEY_ENABLED, "false");
        assertThat(service.run(now).ran()).isFalse();
        verifyNoInteractions(tracker, asm);
    }

    @Test
    void trackerNotOk_aborts() {
        when(tracker.sync(any())).thenReturn(new XFollowTrackerService.SyncResult(false, false, 0, 0, 0, 0));
        var r = service.run(now);
        assertThat(r.abortedReason()).isNotNull();
        verify(asm, never()).unfollowX(anyList());
        verify(accounts, never()).findByStatusAndFollowsUsFalseAndFollowedAtLessThanEqual(any(), any());
    }

    @Test
    void incompleteSnapshot_aborts() {
        when(tracker.sync(any())).thenReturn(new XFollowTrackerService.SyncResult(true, false, 5, 0, 0, 0));
        assertThat(service.run(now).abortedReason()).contains("incomplete");
        verify(asm, never()).unfollowX(anyList());
    }

    @Test
    void selection_onlyBigIgnoredAccounts() {
        Instant fresh = now.minus(Duration.ofHours(1));
        XFollowAccount interacted = acc("interacted", 10, 50_000, fresh);
        interacted.setLastInboundInteractionAt(now.minus(Duration.ofDays(2)));
        XFollowAccount earlier = acc("earlierlike", 10, 50_000, fresh);
        earlier.setLastInboundInteractionAt(now.minus(Duration.ofDays(20)));
        XFollowAccount noDate = acc("nodate", 10, 50_000, fresh);
        noDate.setFollowedAt(null);
        candidates(acc("big", 10, 50_000, fresh), acc("exact", 10, 10_000, fresh),
            acc("small", 10, 9_999, fresh), interacted, earlier, noDate);
        when(asm.getXProfileFollowers(anyList())).thenReturn(new AsmClient.XProfileFollowersResult(List.of()));
        // unknown count -> skipped
        XFollowAccount unknown = acc("unknown", 10, null, null);
        candidates(acc("big", 10, 50_000, fresh), acc("exact", 10, 10_000, fresh),
            acc("small", 10, 9_999, fresh), interacted, earlier, noDate, unknown);

        var r = service.run(now);

        ArgumentCaptor<List<String>> cap = ArgumentCaptor.forClass(List.class);
        verify(asm, times(1)).unfollowX(cap.capture());
        List<String> all = cap.getAllValues().stream().flatMap(List::stream).toList();
        assertThat(all).containsExactlyInAnyOrder("big", "exact", "earlierlike");
        assertThat(r.unfollowed()).isEqualTo(3);
    }

    @Test
    void megaAccounts_atOrAboveMax_areKept() {
        Instant fresh = now.minus(Duration.ofHours(1));
        candidates(acc("big", 10, 999_999, fresh), acc("mega", 10, 1_000_000, fresh),
            acc("elon", 10, 241_000_000, fresh));
        when(asm.getXProfileFollowers(anyList())).thenReturn(new AsmClient.XProfileFollowersResult(List.of()));

        service.run(now);

        ArgumentCaptor<List<String>> cap = ArgumentCaptor.forClass(List.class);
        verify(asm, times(1)).unfollowX(cap.capture());
        assertThat(cap.getAllValues().stream().flatMap(List::stream).toList()).containsExactly("big");
    }

    @Test
    void dryRun_noAsmUnfollowNoDbChange() {
        cfg.put(XFollowCleanupService.KEY_DRY_RUN, "true");
        candidates(acc("big", 10, 50_000, now.minus(Duration.ofHours(1))));
        var r = service.run(now);
        assertThat(r.dryRun()).isTrue();
        assertThat(r.qualified()).isEqualTo(1);
        verify(asm, never()).unfollowX(anyList());
        verify(asm, never()).getXProfileFollowers(anyList());
        verify(accounts, never()).save(any());
        verify(events, never()).save(any());
        verify(telegram).send(anyString());
    }

    @Test
    void realMode_recordsStateAndEvent() {
        XFollowAccount a = acc("big", 10, 50_000, now.minus(Duration.ofHours(1)));
        candidates(a);
        service.run(now);
        assertThat(a.getStatus()).isEqualTo(XFollowAccount.Status.UNFOLLOWED);
        assertThat(a.getUnfollowedAt()).isEqualTo(now);
        assertThat(a.getUnfollowReason()).isEqualTo("NO_ACTION_7D");
        ArgumentCaptor<XFollowEvent> ev = ArgumentCaptor.forClass(XFollowEvent.class);
        verify(events).save(ev.capture());
        assertThat(ev.getValue().getKind()).isEqualTo(XFollowEvent.Kind.UNFOLLOW);
        assertThat(ev.getValue().getDirection()).isEqualTo(XFollowEvent.Direction.OUT);
        assertThat(ev.getValue().getOccurredAt()).isEqualTo(now);
    }

    @Test
    void failure_abortsWholeRun() {
        Instant fresh = now.minus(Duration.ofHours(1));
        candidates(acc("a", 10, 50_000, fresh), acc("b", 10, 50_000, fresh), acc("c", 10, 50_000, fresh),
            acc("d", 10, 50_000, fresh));
        when(asm.unfollowX(anyList())).thenReturn(new AsmClient.XUnfollowResult(
            List.of("a"), List.of(new AsmClient.XUnfollowFailure("b", "rate limit"))));
        var r = service.run(now);
        verify(asm, times(1)).unfollowX(anyList());
        assertThat(r.unfollowed()).isEqualTo(1);
        assertThat(r.abortedReason()).contains("@b");
    }

    @Test
    void exception_abortsWholeRun() {
        Instant fresh = now.minus(Duration.ofHours(1));
        candidates(acc("a", 10, 50_000, fresh), acc("b", 10, 50_000, fresh), acc("c", 10, 50_000, fresh),
            acc("d", 10, 50_000, fresh));
        when(asm.unfollowX(anyList())).thenThrow(new RuntimeException("boom"));
        var r = service.run(now);
        verify(asm, times(1)).unfollowX(anyList());
        assertThat(r.abortedReason()).contains("boom");
    }

    @Test
    void dailyCap_subtractsAlreadyDone() {
        cfg.put(XFollowCleanupService.KEY_DAILY_CAP, "5");
        when(events.countByDirectionAndKindAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            any(), any(), any(), any())).thenReturn(3L);
        Instant fresh = now.minus(Duration.ofHours(1));
        candidates(acc("a", 12, 50_000, fresh), acc("b", 11, 50_000, fresh), acc("c", 10, 50_000, fresh));
        var r = service.run(now);
        assertThat(r.qualified()).isEqualTo(2);
        assertThat(r.unfollowed()).isEqualTo(2);
    }

    @Test
    void followerLookup_cachedWithin3Days_otherwiseFetchedAndSaved() {
        XFollowAccount cached = acc("cached", 10, 20_000, now.minus(Duration.ofDays(2)));
        XFollowAccount stale = acc("stale", 10, 20_000, now.minus(Duration.ofDays(4)));
        XFollowAccount never = acc("never", 10, null, null);
        candidates(cached, stale, never);
        when(asm.getXProfileFollowers(anyList())).thenReturn(new AsmClient.XProfileFollowersResult(List.of(
            new AsmClient.XProfileFollowers("stale", 500, null),
            new AsmClient.XProfileFollowers("never", 90_000, null))));
        var r = service.run(now);
        ArgumentCaptor<List<String>> cap = ArgumentCaptor.forClass(List.class);
        verify(asm, times(1)).getXProfileFollowers(cap.capture());
        assertThat(cap.getValue()).containsExactlyInAnyOrder("stale", "never");
        assertThat(stale.getFollowerCount()).isEqualTo(500);
        assertThat(never.getFollowerCount()).isEqualTo(90_000);
        assertThat(never.getFollowerCountCheckedAt()).isEqualTo(now);
        assertThat(r.qualified()).isEqualTo(2); // cached + never
    }
}
