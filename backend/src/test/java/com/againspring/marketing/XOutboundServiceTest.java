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

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class XOutboundServiceTest {

    @Mock
    private MarketingXOpsSettingsService settingsService;
    @Mock
    private AsmClient asmClient;
    @Mock
    private XCommentComposer composer;
    @Mock
    private XOpsActionLedger ledger;

    @InjectMocks
    private XOutboundService service;

    private final Instant now = Instant.parse("2026-08-31T03:00:00Z");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "llmEnabled", true);
        when(settingsService.get()).thenReturn(outboundOn(20));
        when(ledger.alreadyHandled(anyString())).thenReturn(false);
        when(ledger.countPostedToday(eq(XOpsAction.Kind.OUTBOUND), any())).thenReturn(0);
        when(composer.composeReply(any(), any())).thenReturn(XCommentComposer.Draft.of("너무귀여움"));
        when(asmClient.publishX(anyString(), anyString(), any(), any()))
            .thenReturn(new AsmClient.XPublishResult(true, "out-posted", "https://x.com/i/out", null));
    }

    @Test
    void flagOff_doesNotCallAsm() {
        when(settingsService.get()).thenReturn(outboundOff());

        service.run(now);

        verifyNoInteractions(asmClient);
    }

    @Test
    void llmDisabled_doesNotCallAsm() {
        ReflectionTestUtils.setField(service, "llmEnabled", false);

        service.run(now);

        verifyNoInteractions(asmClient);
    }

    @Test
    void firstReply_usesRootTweetId() {
        when(asmClient.listXOutboundCandidates(3, 6)).thenReturn(List.of(
            new AsmClient.XOutboundCandidate("root-1", "mutual", "오늘 저녁 뭐먹지", 5, 1.0, false, null)));

        service.run(now);

        verify(asmClient).publishX(eq("너무귀여움"), eq("root-1"), isNull(), isNull());
        verify(ledger).recordPosted(
            eq(XOpsAction.Kind.OUTBOUND), eq("root-1"), eq("root-1"), isNull(),
            eq("out-posted"), eq("너무귀여움"), eq(now));
    }

    @Test
    void secondReply_usesOurReplyTweetId() {
        when(asmClient.listXOutboundCandidates(3, 6)).thenReturn(List.of(
            new AsmClient.XOutboundCandidate(
                "root-1", "mutual", "오늘 저녁 뭐먹지", 5, 1.0, true, "our-reply-9")));

        service.run(now);

        verify(asmClient).publishX(eq("너무귀여움"), eq("our-reply-9"), isNull(), isNull());
        verify(ledger).recordPosted(
            eq(XOpsAction.Kind.OUTBOUND), eq("our-reply-9"), eq("root-1"), isNull(),
            eq("out-posted"), eq("너무귀여움"), eq(now));
    }

    @Test
    void alreadyRepliedWithoutOurReplyId_skips() {
        when(asmClient.listXOutboundCandidates(3, 6)).thenReturn(List.of(
            new AsmClient.XOutboundCandidate("root-1", "mutual", "글", 5, 1.0, true, null)));

        service.run(now);

        verify(asmClient, never()).publishX(any(), any(), any(), any());
        verify(composer, never()).composeReply(any(), any());
    }

    @Test
    void outboundCap20_doesNotFetchCandidates() {
        when(ledger.countPostedToday(eq(XOpsAction.Kind.OUTBOUND), any())).thenReturn(20);

        service.run(now);

        verify(asmClient, never()).listXOutboundCandidates(anyInt(), anyInt());
        verify(asmClient, never()).publishX(any(), any(), any(), any());
    }

    @Test
    void alreadyHandled_preventsDouble() {
        when(asmClient.listXOutboundCandidates(3, 6)).thenReturn(List.of(
            new AsmClient.XOutboundCandidate("root-1", "mutual", "글", 5, 1.0, false, null)));
        when(ledger.alreadyHandled("root-1")).thenReturn(true);

        service.run(now);

        verify(asmClient, never()).publishX(any(), any(), any(), any());
    }

    @Test
    void noVoice_doesNotPublish_andDoesNotConsumeCap() {
        when(asmClient.listXOutboundCandidates(3, 6)).thenReturn(List.of(
            new AsmClient.XOutboundCandidate("root-1", "mutual", "글", 5, 1.0, false, null)));
        when(composer.composeReply(any(), any())).thenReturn(XCommentComposer.Draft.skipped("NO_VOICE"));

        service.run(now);

        verify(asmClient, never()).publishX(any(), any(), any(), any());
        verify(ledger).recordSkipped(eq(XOpsAction.Kind.OUTBOUND), eq("root-1"), eq("NO_VOICE"), eq(now));
        verify(ledger, never()).recordPosted(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void maxOnePublishPerTick() {
        when(asmClient.listXOutboundCandidates(3, 6)).thenReturn(List.of(
            new AsmClient.XOutboundCandidate("root-1", "a", "글1", 5, 1.0, false, null),
            new AsmClient.XOutboundCandidate("root-2", "b", "글2", 8, 2.0, false, null)));

        service.run(now);

        verify(asmClient).publishX(eq("너무귀여움"), eq("root-1"), isNull(), isNull());
        verify(asmClient, never()).publishX(any(), eq("root-2"), any(), any());
    }

    private static MarketingXOpsSettingsService.XOpsSettings outboundOn(int cap) {
        return new MarketingXOpsSettingsService.XOpsSettings(
            "07:30", "22:00", 2, cap, 40, 12, 3, 6,
            false, false, true, true, "04:30");
    }

    private static MarketingXOpsSettingsService.XOpsSettings outboundOff() {
        return new MarketingXOpsSettingsService.XOpsSettings(
            "07:30", "22:00", 2, 20, 40, 12, 3, 6,
            false, false, false, true, "04:30");
    }
}
