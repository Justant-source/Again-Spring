package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.PromptSanitizer;
import com.againspring.repository.ai.SystemSettingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class XPersonaLearnServiceTest {

    @Mock
    private SystemSettingRepository systemSettingRepository;
    @Mock
    private MarketingXOpsSettingsService xOpsSettingsService;
    @Mock
    private FxTwitterXTimelineClient timelineClient;
    @Mock
    private LLMProvider llmProvider;
    @Mock
    private PromptSanitizer promptSanitizer;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private XPersonaLearnService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "llmEnabled", false);
        ReflectionTestUtils.setField(service, "model", "claude-haiku-4-5-20251001");
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());
        when(systemSettingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(xOpsSettingsService.get()).thenReturn(learningOn());
    }

    @Test
    void runNow_keepsManualReplies_dropsAutoThread() {
        when(timelineClient.fetchRecent(eq("againspring_net"), anyInt())).thenReturn(List.of(
            new XManualStatusClassifier.Status(
                "auto-hook", "남친 폰 열자마자 소름 돋았다\n\n#다시봄 #againspring", null, false),
            new XManualStatusClassifier.Status(
                "auto-url", "https://againspring.net/community/x", "againspring_net", false),
            new XManualStatusClassifier.Status(
                "man-1", "@KoreAgenda 너무귀여움 ㅋㅋㅋㅋ", "KoreAgenda", false)
        ));

        XPersonaLearnService.LearnResult r = service.runNow("admin");

        assertThat(r.status()).isEqualTo("INGESTED_LLM_DISABLED");
        assertThat(r.newManuals()).isEqualTo(1);
        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(systemSettingRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
            .extracting(SystemSetting::getSettingKey)
            .contains(XPersonaLearnService.KEY_INGESTED, XPersonaLearnService.KEY_PROFILE);
    }

    @Test
    void runIfDue_doesNotFetchOutsideLearnMinute() {
        Instant noonKst = Instant.parse("2026-08-30T03:00:00Z");
        XPersonaLearnService.LearnResult r = service.runIfDue(noonKst);
        assertThat(r).isNull();
        verify(timelineClient, never()).fetchRecent(any(), anyInt());
    }

    @Test
    void runIfDue_atDawnMinute_runsOnce() {
        Instant dawnKst = Instant.parse("2026-08-29T19:30:00Z");
        when(timelineClient.fetchRecent(eq("againspring_net"), anyInt())).thenReturn(List.of());
        XPersonaLearnService.LearnResult r = service.runIfDue(dawnKst);
        assertThat(r).isNotNull();
        verify(timelineClient).fetchRecent(eq("againspring_net"), anyInt());
    }

    private static MarketingXOpsSettingsService.XOpsSettings learningOn() {
        return new MarketingXOpsSettingsService.XOpsSettings(
            "07:30", "22:00", 2, 20, 40, 12, 3, 6,
            false, false, false, true, "04:30");
    }
}
