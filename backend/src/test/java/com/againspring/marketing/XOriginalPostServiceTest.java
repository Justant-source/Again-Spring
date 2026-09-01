package com.againspring.marketing;

import com.againspring.domain.community.Post;
import com.againspring.domain.marketing.XOpsAction;
import com.againspring.notification.TelegramNotifier;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.marketing.MarketingJobRepository;
import com.againspring.safety.KeywordGuard;
import com.againspring.safety.Level;
import com.againspring.safety.ScanResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class XOriginalPostServiceTest {

    @Mock
    private MarketingXOpsSettingsService settingsService;
    @Mock
    private PostRepository postRepository;
    @Mock
    private MarketingJobRepository marketingJobRepository;
    @Mock
    private XCommentComposer composer;
    @Mock
    private KeywordGuard keywordGuard;
    @Mock
    private AsmClient asmClient;
    @Mock
    private XOpsActionLedger ledger;
    @Mock
    private TelegramNotifier telegramNotifier;

    @InjectMocks
    private XOriginalPostService service;

    /** 2026-09-01 12:30 KST */
    private final Instant noon = Instant.parse("2026-09-01T03:30:00Z");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "llmEnabled", true);
        when(settingsService.get()).thenReturn(originalOn(2, 1));
        when(ledger.alreadyHandled(anyString())).thenReturn(false);
        when(ledger.alreadyScooped(anyLong())).thenReturn(false);
        when(ledger.countPostedToday(eq(XOpsAction.Kind.ORIGINAL), any())).thenReturn(0);
        when(marketingJobRepository.countAnyPlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(marketingJobRepository.countActivePlatformJobs(anyString(), anyString())).thenReturn(0L);
        when(keywordGuard.scanLLMOutput(any())).thenReturn(ScanResult.empty());
        when(composer.composeOriginal(any(), any())).thenReturn(XCommentComposer.Draft.of("그 마음 알겠음"));
        when(asmClient.publishX(anyString(), any(), any(), any()))
            .thenReturn(new AsmClient.XPublishResult(true, "orig-1", "https://x.com/i/orig", null));
        when(postRepository.findPublicRankedForMarketing(anyInt(), anyInt()))
            .thenReturn(List.of(story("1001")));
    }

    @Test
    void flagOff_doesNotCallAsm() {
        when(settingsService.get()).thenReturn(originalOff());

        service.runIfDue(noon);

        verifyNoInteractions(asmClient);
        verifyNoInteractions(composer);
        verify(ledger, never()).recordPosted(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void llmDisabled_doesNotCallAsm() {
        ReflectionTestUtils.setField(service, "llmEnabled", false);

        service.runIfDue(noon);

        verifyNoInteractions(asmClient);
    }

    @Test
    void offSlot_doesNotCallAsm() {
        Instant ritualMorning = Instant.parse("2026-08-30T22:30:00Z");

        service.runIfDue(ritualMorning);

        verifyNoInteractions(asmClient);
        verifyNoInteractions(postRepository);
    }

    @Test
    void dailyCap_doesNotCompose() {
        when(ledger.countPostedToday(eq(XOpsAction.Kind.ORIGINAL), any())).thenReturn(1);

        service.runIfDue(noon);

        verifyNoInteractions(composer);
        verifyNoInteractions(asmClient);
    }

    @Test
    void scoopCapUsesMinOfDailyCapAndStoryScoops() {
        when(settingsService.get()).thenReturn(originalOn(0, 5));

        service.runIfDue(noon);

        verifyNoInteractions(composer);
        verifyNoInteractions(asmClient);
    }

    @Test
    void duplicateScoop_skipsToNextPost_andSavesRefPostId() {
        when(postRepository.findPublicRankedForMarketing(anyInt(), anyInt()))
            .thenReturn(List.of(story("1001"), story("1002")));
        when(ledger.alreadyScooped(1001L)).thenReturn(true);
        when(ledger.alreadyScooped(1002L)).thenReturn(false);

        service.runIfDue(noon);

        verify(composer).composeOriginal(any(), any());
        ArgumentCaptor<Long> ref = ArgumentCaptor.forClass(Long.class);
        verify(ledger).recordPosted(
            eq(XOpsAction.Kind.ORIGINAL),
            eq("original:1230:2026-09-01"),
            eq("1002"),
            isNull(),
            eq("orig-1"),
            anyString(),
            eq(noon),
            ref.capture());
        assertThat(ref.getValue()).isEqualTo(1002L);
        verify(asmClient).publishX(anyString(), isNull(), isNull(), isNull());
    }

    @Test
    void keywordSkip_doesNotPublish() {
        when(keywordGuard.scanLLMOutput(any())).thenReturn(ScanResult.blockedResult(
            Level.LEVEL1,
            List.of(new ScanResult.Match("판결", Level.LEVEL1, "JUDGMENT", false, 0))));

        service.runIfDue(noon);

        verify(asmClient, never()).publishX(any(), any(), any(), any());
        verify(ledger).recordSkipped(eq(XOpsAction.Kind.ORIGINAL), eq("1001"), eq("SAFETY"), eq(noon));
        verify(telegramNotifier, never()).send(any());
    }

    @Test
    void refPostIdSaved_onSuccessfulPublish() {
        service.runIfDue(noon);

        verify(ledger).recordPosted(
            eq(XOpsAction.Kind.ORIGINAL),
            eq("original:1230:2026-09-01"),
            eq("1001"),
            isNull(),
            eq("orig-1"),
            anyString(),
            eq(noon),
            eq(1001L));
        verify(telegramNotifier).send(anyString());
        verify(composer).composeOriginal(anyString(), anyString());
    }

    @Test
    void xThreadJob_skipsCandidate() {
        when(marketingJobRepository.countAnyPlatformJobs(eq("1001"), eq("x_thread"))).thenReturn(1L);
        when(postRepository.findPublicRankedForMarketing(anyInt(), anyInt()))
            .thenReturn(List.of(story("1001")));

        service.runIfDue(noon);

        verifyNoInteractions(composer);
        verify(asmClient, never()).publishX(any(), any(), any(), any());
        verify(ledger).recordSkipped(
            eq(XOpsAction.Kind.ORIGINAL), eq("original:1230:2026-09-01"), eq("NO_MATERIAL"), eq(noon));
    }

    private static Post story(String id) {
        return Post.builder().id(id).title("제목").bodyPublished("본문 요약").build();
    }

    private static MarketingXOpsSettingsService.XOpsSettings originalOn(int scoops, int dailyCap) {
        return new MarketingXOpsSettingsService.XOpsSettings(
            "07:30", "22:00", scoops, 20, 40, 12, 3, 6,
            false, false, false, true, "04:30",
            true, true, dailyCap);
    }

    private static MarketingXOpsSettingsService.XOpsSettings originalOff() {
        return new MarketingXOpsSettingsService.XOpsSettings(
            "07:30", "22:00", 2, 20, 40, 12, 3, 6,
            false, false, false, true, "04:30");
    }
}
