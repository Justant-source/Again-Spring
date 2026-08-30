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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class XRitualPublisherTest {

    @Mock
    private MarketingXOpsSettingsService settingsService;
    @Mock
    private AsmClient asmClient;
    @Mock
    private XCommentComposer composer;
    @Mock
    private XOpsActionLedger ledger;

    @InjectMocks
    private XRitualPublisher publisher;

    /** 2026-08-31 07:30 KST */
    private final Instant morning = Instant.parse("2026-08-30T22:30:00Z");

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "llmEnabled", true);
        when(settingsService.get()).thenReturn(ritualOn());
        when(ledger.alreadyHandled(anyString())).thenReturn(false);
        when(composer.composeRitual(anyString())).thenReturn(XCommentComposer.Draft.of("좋은 아침"));
        when(asmClient.publishRitual(anyString(), anyString()))
            .thenReturn(new AsmClient.XPublishResult(true, "rit-1", "https://x.com/i/rit", "morning.jpg"));
    }

    @Test
    void flagOff_doesNotCallAsm() {
        when(settingsService.get()).thenReturn(ritualOff());

        publisher.runIfDue(morning);

        verifyNoInteractions(asmClient);
        verifyNoInteractions(composer);
    }

    @Test
    void llmDisabled_doesNotCallAsm() {
        ReflectionTestUtils.setField(publisher, "llmEnabled", false);

        publisher.runIfDue(morning);

        verifyNoInteractions(asmClient);
    }

    @Test
    void firesAtMorningTime_oncePerKstDay() {
        String sentinel = "ritual:morning:2026-08-31";

        publisher.runIfDue(morning);

        verify(composer).composeRitual("morning");
        verify(asmClient).publishRitual("morning", "좋은 아침");
        verify(ledger).recordPosted(
            eq(XOpsAction.Kind.RITUAL), eq(sentinel), isNull(), isNull(),
            eq("rit-1"), eq("좋은 아침"), eq(morning));

        when(ledger.alreadyHandled(sentinel)).thenReturn(true);
        publisher.runIfDue(morning.plusSeconds(60));

        verify(asmClient, times(1)).publishRitual(anyString(), anyString());
    }

    @Test
    void alreadyHandled_preventsDouble() {
        when(ledger.alreadyHandled("ritual:morning:2026-08-31")).thenReturn(true);

        publisher.runIfDue(morning);

        verify(asmClient, never()).publishRitual(any(), any());
        verify(composer, never()).composeRitual(any());
    }

    @Test
    void outsideSlot_doesNotPublish() {
        Instant noonKst = Instant.parse("2026-08-31T03:00:00Z");

        publisher.runIfDue(noonKst);

        verifyNoInteractions(asmClient);
        verify(composer, never()).composeRitual(any());
    }

    @Test
    void nightSlot_usesNightSentinel() {
        Instant night = Instant.parse("2026-08-31T13:00:00Z");
        when(composer.composeRitual("night")).thenReturn(XCommentComposer.Draft.of("벌써자?"));

        publisher.runIfDue(night);

        verify(asmClient).publishRitual("night", "벌써자?");
        verify(ledger).recordPosted(
            eq(XOpsAction.Kind.RITUAL), eq("ritual:night:2026-08-31"), isNull(), isNull(),
            eq("rit-1"), eq("벌써자?"), eq(night));
    }

    private static MarketingXOpsSettingsService.XOpsSettings ritualOn() {
        return new MarketingXOpsSettingsService.XOpsSettings(
            "07:30", "22:00", 2, 20, 40, 12, 3, 6,
            true, false, false, true, "04:30");
    }

    private static MarketingXOpsSettingsService.XOpsSettings ritualOff() {
        return new MarketingXOpsSettingsService.XOpsSettings(
            "07:30", "22:00", 2, 20, 40, 12, 3, 6,
            false, false, false, true, "04:30");
    }
}
