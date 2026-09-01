package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.XOpsAction;
import com.againspring.domain.marketing.XPersonaExample;
import com.againspring.notification.TelegramNotifier;
import com.againspring.repository.ai.SystemSettingRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class XPersonaDrillServiceTest {

    @Mock
    private SystemSettingRepository systemSettingRepository;
    @Mock
    private XPersonaExampleRepository exampleRepository;
    @Mock
    private MarketingXOpsSettingsService xOpsSettingsService;
    @Mock
    private AsmClient asmClient;
    @Mock
    private TelegramNotifier telegramNotifier;
    @Mock
    private XPersonaLearnService xPersonaLearnService;
    @Mock
    private XOpsActionLedger ledger;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private XPersonaDrillService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "dailyCap", 10);
        when(telegramNotifier.isConfigured()).thenReturn(true);
        when(telegramNotifier.configuredChatId()).thenReturn("111");
        when(systemSettingRepository.findById(any())).thenReturn(Optional.empty());
        when(systemSettingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(xOpsSettingsService.get()).thenReturn(new MarketingXOpsSettingsService.XOpsSettings(
            "07:30", "22:00", 2, 20, 40, 12, 0, 24,
            false, false, true, true, "04:30"));
        when(exampleRepository.existsByTweetId(any())).thenReturn(false);
        when(exampleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(ledger.alreadyHandled(any())).thenReturn(false);
        when(telegramNotifier.sendAndGetMessageId(any())).thenReturn(Optional.of(99L));
        when(xPersonaLearnService.ingestDrillIntoProfile(any())).thenReturn("INGESTED_LLM_DISABLED");
        when(xPersonaLearnService.drillsToday(any())).thenReturn(1);
    }

    @Test
    void pickCandidate_skipsVideoAndLabeled() {
        when(asmClient.listXOutboundCandidates(0, 24)).thenReturn(List.of(
            new AsmClient.XOutboundCandidate(
                "vid", "a", "영상", 1, 1.0, false, null, true, false, null, List.of()),
            new AsmClient.XOutboundCandidate(
                "done", "b", "이미", 1, 1.0, false, null, false, false, null, List.of()),
            new AsmClient.XOutboundCandidate(
                "ok", "c", "가능", 1, 1.0, false, null, false, false, null, List.of())
        ));
        when(exampleRepository.existsByTweetId("done")).thenReturn(true);

        AsmClient.XOutboundCandidate picked = service.pickCandidate();

        assertThat(picked.tweetId()).isEqualTo("ok");
    }

    @Test
    void caption_includesHandleAndNoPostWarning() {
        String cap = XPersonaDrillService.buildCaption(new AsmClient.XOutboundCandidate(
            "t1", "someone", "본문입니다", 0, 0.5, false, null,
            false, false, null, List.of("힌트댓글")));
        assertThat(cap).contains("[Justant-Bot 드릴]");
        assertThat(cap).contains("@someone");
        assertThat(cap).contains("본문입니다");
        assertThat(cap).contains("힌트댓글");
        assertThat(cap).contains("게시 안 함");
        assertThat(cap.length()).isLessThanOrEqualTo(TelegramNotifier.CAPTION_MAX);
    }

    @Test
    void wrongChat_isIgnored() throws Exception {
        service.handleUpdate(objectMapper.readTree("""
            {"message":{"message_id":1,"chat":{"id":999},"text":"/drill"}}
            """));
        verify(asmClient, never()).listXOutboundCandidates(anyInt(), anyInt());
    }

    @Test
    void replyBindsPendingAndSavesDrill() throws Exception {
        String pendingJson = """
            {"telegramMessageId":99,"tweetId":"root-1","postText":"상황글","hasPhoto":false,
             "expiresAt":"2099-01-01T00:00:00Z"}
            """;
        when(systemSettingRepository.findById(XPersonaDrillService.KEY_PENDING))
            .thenReturn(Optional.of(SystemSetting.builder()
                .settingKey(XPersonaDrillService.KEY_PENDING)
                .settingValue(pendingJson)
                .build()));

        service.handleUpdate(objectMapper.readTree("""
            {"message":{"message_id":12,"chat":{"id":111},"text":"너무귀여움",
              "reply_to_message":{"message_id":99}}}
            """));

        ArgumentCaptor<XPersonaExample> captor = ArgumentCaptor.forClass(XPersonaExample.class);
        verify(exampleRepository).save(captor.capture());
        assertThat(captor.getValue().getSource()).isEqualTo(XPersonaExample.Source.DRILL);
        assertThat(captor.getValue().getTweetId()).isEqualTo("root-1");
        assertThat(captor.getValue().getOperatorBody()).isEqualTo("너무귀여움");
        verify(ledger).recordSkipped(eq(XOpsAction.Kind.OUTBOUND), eq("root-1"), eq("DRILL"), any());
        verify(xPersonaLearnService).ingestDrillIntoProfile("telegram");
        verify(telegramNotifier).send(contains("저장됨"));
    }

    @Test
    void replyToWrongMessage_isIgnored() throws Exception {
        String pendingJson = """
            {"telegramMessageId":99,"tweetId":"root-1","postText":"상황글","hasPhoto":false,
             "expiresAt":"2099-01-01T00:00:00Z"}
            """;
        when(systemSettingRepository.findById(XPersonaDrillService.KEY_PENDING))
            .thenReturn(Optional.of(SystemSetting.builder()
                .settingKey(XPersonaDrillService.KEY_PENDING)
                .settingValue(pendingJson)
                .build()));

        service.handleUpdate(objectMapper.readTree("""
            {"message":{"message_id":12,"chat":{"id":111},"text":"너무귀여움",
              "reply_to_message":{"message_id":1}}}
            """));

        verify(exampleRepository, never()).save(any());
    }
}
