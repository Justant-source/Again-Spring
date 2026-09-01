package com.againspring.api.admin;

import com.againspring.marketing.MarketingXOpsSettingsService;
import com.againspring.marketing.XOutboundService;
import com.againspring.marketing.XPersonaLearnService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMarketingXOpsOutboundTest {

    @Mock
    MarketingXOpsSettingsService marketingXOpsSettingsService;
    @Mock
    XPersonaLearnService xPersonaLearnService;
    @Mock
    XOutboundService xOutboundService;

    @InjectMocks
    AdminMarketingController controller;

    @Test
    void runXOutboundNow_disabled_returns400WithoutTick() {
        when(marketingXOpsSettingsService.get()).thenReturn(settings(false));

        assertThatThrownBy(() -> controller.runXOutboundNow())
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(xOutboundService, never()).run(any());
    }

    @Test
    void runXOutboundNow_enabled_runsTick() {
        when(marketingXOpsSettingsService.get()).thenReturn(settings(true));

        assertThat(controller.runXOutboundNow().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(xOutboundService).run(any());
    }

    private static MarketingXOpsSettingsService.XOpsSettings settings(boolean outbound) {
        return new MarketingXOpsSettingsService.XOpsSettings(
            "07:30", "22:00", 2, 20, 40, 12, 3, 6,
            false, false, outbound, true, "04:30");
    }
}
