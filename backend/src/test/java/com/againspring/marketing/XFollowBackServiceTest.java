package com.againspring.marketing;

import com.againspring.domain.ai.SystemSetting;
import com.againspring.domain.marketing.XOpsAction;
import com.againspring.repository.ai.SystemSettingRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class XFollowBackServiceTest {

    private final SystemSettingRepository settings = mock(SystemSettingRepository.class);
    private final AsmClient asm = mock(AsmClient.class);
    private final XOpsActionLedger ledger = mock(XOpsActionLedger.class);
    private final XFollowBackService service = new XFollowBackService(settings, asm, ledger);
    private final Instant now = Instant.parse("2026-10-22T03:00:00Z");

    private void set(String key, String value) {
        when(settings.findById(key)).thenReturn(Optional.of(
            SystemSetting.builder().settingKey(key).settingValue(value).build()));
    }

    @Test
    void disabledByDefaultDoesNotCallAsm() {
        service.run(now);
        verify(asm, never()).followBackVerified(anyInt());
    }

    @Test
    void stopsWhenDailyCapReached() {
        set(XFollowBackService.KEY_ENABLED, "true");
        set(XFollowBackService.KEY_DAILY_CAP, "30");
        when(ledger.countPostedToday(XOpsAction.Kind.FOLLOW, now)).thenReturn(31);
        service.run(now);
        verify(asm, never()).followBackVerified(anyInt());
    }

    @Test
    void recordsEachNewHandleOnce() {
        set(XFollowBackService.KEY_ENABLED, "true");
        set(XFollowBackService.KEY_DAILY_CAP, "30");
        set(XFollowBackService.KEY_PER_TICK, "5");
        when(ledger.countPostedToday(XOpsAction.Kind.FOLLOW, now)).thenReturn(0);
        when(asm.followBackVerified(anyInt())).thenReturn(
            new AsmClient.XFollowBackResult(10, 6, List.of("@Alice", "bob")));
        when(ledger.alreadyHandled("follow:bob")).thenReturn(true);

        service.run(now);

        verify(ledger).recordPosted(eq(XOpsAction.Kind.FOLLOW), eq("follow:alice"),
            any(), any(), any(), eq("alice"), eq(now));
        verify(ledger, never()).recordPosted(any(), eq("follow:bob"), any(), any(), any(), any(), any());
    }
}
