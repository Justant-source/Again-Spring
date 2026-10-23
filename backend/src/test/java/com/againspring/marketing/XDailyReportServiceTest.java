package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.XOpsAction;
import com.againspring.notification.TelegramNotifier;
import com.againspring.repository.ai.SystemSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XDailyReportServiceTest {

    @Mock private XOpsActionLedger ledger;
    @Mock private AsmClient asmClient;
    @Mock private TelegramNotifier telegramNotifier;
    @Mock private SystemSettingRepository settings;
    @InjectMocks private XDailyReportService service;

    private final Instant now = Instant.parse("2026-10-23T14:00:00Z"); // 23:00 KST

    private void counts() {
        when(ledger.countPostedToday(eq(XOpsAction.Kind.OUTBOUND), any())).thenReturn(120);
        when(ledger.countPostedToday(eq(XOpsAction.Kind.INBOUND), any())).thenReturn(8);
        when(ledger.countPostedToday(eq(XOpsAction.Kind.FOLLOW), any())).thenReturn(22);
        when(ledger.countTodayByStatus(any(), eq(XOpsAction.Status.FAILED), any())).thenReturn(1);
    }

    @Test
    void reportShowsDeltaAgainstPreviousSnapshotAndStoresNewOne() {
        counts();
        when(asmClient.getXProfileCounts()).thenReturn(new AsmClient.XProfileCounts(130, 400));
        SystemSetting prev = SystemSetting.builder().settingKey(XDailyReportService.SNAPSHOT_KEY)
            .settingValue("2026-10-22|118|380").build();
        when(settings.findById(XDailyReportService.SNAPSHOT_KEY)).thenReturn(Optional.of(prev));

        String text = service.buildReport(now);

        assertThat(text).contains("댓글 합계: 128건", "선댓글 120", "대댓글 8", "맞팔로우: 22명",
            "팔로워: 130 (+12", "팔로잉: 400 (+20");
        ArgumentCaptor<SystemSetting> saved = ArgumentCaptor.forClass(SystemSetting.class);
        verify(settings).save(saved.capture());
        assertThat(saved.getValue().getSettingValue()).isEqualTo("2026-10-23|130|400");
    }

    @Test
    void firstReportHasNoBaseline() {
        counts();
        when(asmClient.getXProfileCounts()).thenReturn(new AsmClient.XProfileCounts(130, 400));
        when(settings.findById(XDailyReportService.SNAPSHOT_KEY)).thenReturn(Optional.empty());

        assertThat(service.buildReport(now)).contains("비교 기준 없음");
    }

    @Test
    void profileLookupFailureStillReportsCounts() {
        counts();
        when(asmClient.getXProfileCounts()).thenThrow(new AsmUnavailableException("boom"));

        assertThat(service.buildReport(now)).contains("댓글 합계: 128건", "조회 실패");
    }
}
