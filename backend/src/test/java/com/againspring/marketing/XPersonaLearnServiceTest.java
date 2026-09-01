package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.XOpsAction;
import com.againspring.domain.marketing.XPersonaExample;
import com.againspring.llm.LLMProvider;
import com.againspring.llm.PromptSanitizer;
import com.againspring.repository.ai.SystemSettingRepository;
import com.againspring.repository.marketing.XOpsActionRepository;
import com.againspring.repository.marketing.XPersonaExampleRepository;
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
    @Mock
    private XPersonaExampleRepository exampleRepository;
    @Mock
    private XOpsActionRepository xOpsActionRepository;

    @InjectMocks
    private XPersonaLearnService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "llmEnabled", false);
        ReflectionTestUtils.setField(service, "model", "claude-haiku-4-5-20251001");
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());
        when(systemSettingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(xOpsSettingsService.get()).thenReturn(learningOn());
        when(exampleRepository.existsByTweetId(any())).thenReturn(false);
        when(exampleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(exampleRepository.findTop20BySourceOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(exampleRepository.findByTweetId(any())).thenReturn(Optional.empty());
        when(xOpsActionRepository.findByStatusAndKindInAndCreatedAtGreaterThanEqual(any(), any(), any()))
            .thenReturn(List.of());
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
        verify(exampleRepository).save(any());
    }

    @Test
    void runNow_skipsLedgerPostedIdsEvenIfTheyLookManual() {
        when(timelineClient.fetchRecent(eq("againspring_net"), anyInt())).thenReturn(List.of(
            new XManualStatusClassifier.Status(
                "auto-1", "@KoreAgenda 너무귀여움 ㅋㅋㅋㅋ", "KoreAgenda", false),
            new XManualStatusClassifier.Status(
                "man-1", "@ceolmh3 힘빠지긴 할듯", "ceolmh3", false)
        ));
        when(xOpsActionRepository.findByStatusAndKindInAndCreatedAtGreaterThanEqual(any(), any(), any()))
            .thenReturn(List.of(XOpsAction.builder()
                .kind(XOpsAction.Kind.OUTBOUND)
                .status(XOpsAction.Status.POSTED)
                .postedTweetId("auto-1")
                .targetTweetId("root-a")
                .body("너무귀여움 ㅋㅋㅋㅋ")
                .createdAt(Instant.now())
                .build()));

        service.runNow("admin");

        ArgumentCaptor<XPersonaExample> captor = ArgumentCaptor.forClass(XPersonaExample.class);
        verify(exampleRepository).save(captor.capture());
        assertThat(captor.getValue().getTweetId()).isEqualTo("man-1");
        assertThat(captor.getValue().getSource()).isEqualTo(XPersonaExample.Source.TIMELINE);
    }

    @Test
    void runNow_ingestsMissingPostedCommentAsDeletedAuto() {
        when(timelineClient.fetchRecent(eq("againspring_net"), anyInt())).thenReturn(List.of());
        when(xOpsActionRepository.findByStatusAndKindInAndCreatedAtGreaterThanEqual(any(), any(), any()))
            .thenReturn(List.of(XOpsAction.builder()
                .kind(XOpsAction.Kind.OUTBOUND)
                .status(XOpsAction.Status.POSTED)
                .postedTweetId("auto-gone")
                .targetTweetId("root-9")
                .body("문맥없는말")
                .createdAt(Instant.now())
                .build()));

        XPersonaLearnService.LearnResult r = service.runNow("admin");

        assertThat(r.status()).isEqualTo("INGESTED_LLM_DISABLED");
        assertThat(r.newManuals()).isEqualTo(1);
        ArgumentCaptor<XPersonaExample> captor = ArgumentCaptor.forClass(XPersonaExample.class);
        verify(exampleRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(XPersonaExample.Source.DELETED_AUTO);
        assertThat(captor.getValue().getOperatorBody()).isEqualTo("문맥없는말");
        assertThat(captor.getValue().getTweetId()).isEqualTo("auto-gone");
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

    @Test
    void formatPair_putsSituationAndOperator() {
        XPersonaExample ex = XPersonaExample.builder()
            .source(XPersonaExample.Source.TIMELINE)
            .postText("사진 글")
            .operatorBody("너무귀여움")
            .build();
        assertThat(XPersonaLearnService.formatPair(ex, 1))
            .contains("가중 1")
            .contains("상황: 사진 글")
            .contains("운영자: 너무귀여움");
        assertThat(XPersonaLearnService.formatDeletedLine(XPersonaExample.builder()
            .source(XPersonaExample.Source.DELETED_AUTO)
            .postText("root-9")
            .operatorBody("문맥없는말")
            .build()))
            .contains("피함")
            .contains("문맥없는말");
    }

    private static MarketingXOpsSettingsService.XOpsSettings learningOn() {
        return new MarketingXOpsSettingsService.XOpsSettings(
            "07:30", "22:00", 2, 20, 40, 12, 3, 6,
            false, false, false, true, "04:30");
    }
}
