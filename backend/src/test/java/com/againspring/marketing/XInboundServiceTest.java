package com.againspring.marketing;

import com.againspring.domain.marketing.XOpsAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class XInboundServiceTest {

    @Mock
    private MarketingXOpsSettingsService settingsService;
    @Mock
    private AsmClient asmClient;
    @Mock
    private XCommentComposer composer;
    @Mock
    private XOpsActionLedger ledger;

    @InjectMocks
    private XInboundService service;

    private final Instant now = Instant.parse("2026-08-31T00:00:00Z");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "llmEnabled", true);
        when(settingsService.get()).thenReturn(inboundOn(40, 12));
        when(ledger.alreadyHandled(anyString())).thenReturn(false);
        when(ledger.countPostedToday(eq(XOpsAction.Kind.INBOUND), any())).thenReturn(0);
        when(ledger.countPostedTodayForOurPost(anyString(), any())).thenReturn(0);
        when(composer.composeReply(any(), any())).thenReturn(XCommentComposer.Draft.of("공감돼요 ㅋㅋ"));
        when(asmClient.publishX(anyString(), anyString(), any(), any()))
            .thenReturn(new AsmClient.XPublishResult(true, "posted-1", "https://x.com/i/posted-1", null));
    }

    @Test
    void flagOff_doesNotCallAsm() {
        when(settingsService.get()).thenReturn(inboundOff());

        service.run(now);

        verifyNoInteractions(asmClient);
        verifyNoInteractions(composer);
        verifyNoInteractions(ledger);
    }

    @Test
    void llmDisabled_doesNotCallAsm() {
        ReflectionTestUtils.setField(service, "llmEnabled", false);

        service.run(now);

        verifyNoInteractions(asmClient);
        verifyNoInteractions(composer);
    }

    @Test
    void tooNew_skipped() {
        String id = "c-new";
        when(asmClient.listXInbox(90)).thenReturn(List.of(
            inbox(id, now.minus(Duration.ofMinutes(1)))));

        service.run(now);

        verify(asmClient, never()).publishX(any(), any(), any(), any());
        verify(composer, never()).composeReply(any(), any());
    }

    @Test
    void tooOld_skipped() {
        String id = "c-old";
        when(asmClient.listXInbox(90)).thenReturn(List.of(
            inbox(id, now.minus(Duration.ofMinutes(31)))));

        service.run(now);

        verify(asmClient, never()).publishX(any(), any(), any(), any());
        verify(composer, never()).composeReply(any(), any());
    }

    @Test
    void inWindow_postsOnce() {
        String id = "c-ok";
        Instant created = now.minus(Duration.ofMinutes(XInboundService.jitterMinutes(id)));
        when(asmClient.listXInbox(90)).thenReturn(List.of(inbox(id, created)));

        service.run(now);

        verify(asmClient).publishX(eq("공감돼요 ㅋㅋ"), eq(id), isNull(), isNull());
        verify(ledger).recordPosted(
            eq(XOpsAction.Kind.INBOUND), eq(id), eq("parent-1"), eq("our-1"),
            eq("posted-1"), eq("공감돼요 ㅋㅋ"), eq(now));
    }

    @Test
    void dailyCap40_stopsWithoutRecordingCapForLeftovers() {
        when(ledger.countPostedToday(eq(XOpsAction.Kind.INBOUND), any())).thenReturn(40);
        String id = "c-cap";
        Instant created = now.minus(Duration.ofMinutes(XInboundService.jitterMinutes(id)));
        when(asmClient.listXInbox(90)).thenReturn(List.of(inbox(id, created)));

        service.run(now);

        verify(asmClient, never()).publishX(any(), any(), any(), any());
        verify(composer, never()).composeReply(any(), any());
        verify(ledger, never()).recordSkipped(eq(XOpsAction.Kind.INBOUND), any(), eq("CAP"), any());
    }

    @Test
    void perPostCap12_skipsThatPostOnly() {
        String a = "c-post-a";
        String b = "c-post-b";
        Instant createdA = now.minus(Duration.ofMinutes(XInboundService.jitterMinutes(a)));
        Instant createdB = now.minus(Duration.ofMinutes(XInboundService.jitterMinutes(b)));
        when(asmClient.listXInbox(90)).thenReturn(List.of(
            new AsmClient.XInboxItem(a, "p", "our-full", "alice", "좋은 글", createdA),
            new AsmClient.XInboxItem(b, "p", "our-open", "bob", "공감요", createdB)));
        when(ledger.countPostedTodayForOurPost(eq("our-full"), any())).thenReturn(12);
        when(ledger.countPostedTodayForOurPost(eq("our-open"), any())).thenReturn(0);

        service.run(now);

        verify(asmClient, never()).publishX(any(), eq(a), any(), any());
        verify(asmClient).publishX(eq("공감돼요 ㅋㅋ"), eq(b), isNull(), isNull());
        verify(ledger).recordSkipped(eq(XOpsAction.Kind.INBOUND), eq(a), eq("CAP"), eq(now));
    }

    @Test
    void alreadyHandled_preventsDouble() {
        String id = "c-dup";
        Instant created = now.minus(Duration.ofMinutes(XInboundService.jitterMinutes(id)));
        when(asmClient.listXInbox(90)).thenReturn(List.of(inbox(id, created)));
        when(ledger.alreadyHandled(id)).thenReturn(true);

        service.run(now);

        verify(asmClient, never()).publishX(any(), any(), any(), any());
        verify(composer, never()).composeReply(any(), any());
    }

    @Test
    void followForFollow_skippedAsSafetyWithoutPublish() {
        String id = "c-spam";
        Instant created = now.minus(Duration.ofMinutes(XInboundService.jitterMinutes(id)));
        when(asmClient.listXInbox(90)).thenReturn(List.of(
            new AsmClient.XInboxItem(id, "p", "our-1", "spammer", "맞팔해요!", created)));

        service.run(now);

        verify(asmClient, never()).publishX(any(), any(), any(), any());
        verify(ledger).recordSkipped(eq(XOpsAction.Kind.INBOUND), eq(id), eq("SAFETY"), eq(now));
        verify(composer, never()).composeReply(any(), any());
    }

    @Test
    void publishFailure_recordsFailed() {
        String id = "c-fail";
        Instant created = now.minus(Duration.ofMinutes(XInboundService.jitterMinutes(id)));
        when(asmClient.listXInbox(90)).thenReturn(List.of(inbox(id, created)));
        when(asmClient.publishX(anyString(), anyString(), any(), any()))
            .thenThrow(new AsmUnavailableException("400 from ASM"));

        service.run(now);

        verify(ledger).recordFailed(eq(XOpsAction.Kind.INBOUND), eq(id), eq("ASM_ERROR"), eq(now));
        verify(ledger, never()).recordPosted(any(), any(), any(), any(), any(), any(), any());
    }

    private static AsmClient.XInboxItem inbox(String tweetId, Instant createdAt) {
        return new AsmClient.XInboxItem(tweetId, "parent-1", "our-1", "alice", "좋은 글이네요", createdAt);
    }

    private static MarketingXOpsSettingsService.XOpsSettings inboundOn(int daily, int perPost) {
        return new MarketingXOpsSettingsService.XOpsSettings(
            "07:30", "22:00", 2, 20, daily, perPost, 3, 6,
            false, true, false, true, "04:30");
    }

    private static MarketingXOpsSettingsService.XOpsSettings inboundOff() {
        return new MarketingXOpsSettingsService.XOpsSettings(
            "07:30", "22:00", 2, 20, 40, 12, 3, 6,
            false, false, false, true, "04:30");
    }
}
