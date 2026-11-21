package com.againspring.marketing.experiment;

import com.againspring.domain.marketing.MarketingAccountStats;
import com.againspring.marketing.AsmClient;
import com.againspring.marketing.AsmUnavailableException;
import com.againspring.repository.marketing.MarketingAccountStatsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketingAccountStatsServiceTest {

    @Mock AsmClient asmClient;
    @Mock MarketingAccountStatsRepository repository;
    MarketingAccountStatsService service;
    ObjectMapper om = new ObjectMapper();
    static final LocalDate DAY = LocalDate.of(2026, 11, 21);

    @BeforeEach
    void setUp() {
        service = new MarketingAccountStatsService(asmClient, repository, om,
            Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void storesFollowersPerPlatform() throws Exception {
        when(asmClient.fetchAccountStats()).thenReturn(om.readTree("""
            {"x":{"followers":120,"following":300},
             "youtube":{"subscribers":45,"views":9000,"videos":80},
             "instagram":{"followers":70,"media_count":50},
             "errors":{}}"""));
        when(repository.findByPlatformAndCollectedOn(any(), any())).thenReturn(Optional.empty());

        assertThat(service.collectAndStore()).isEqualTo(3);
        ArgumentCaptor<MarketingAccountStats> c = ArgumentCaptor.forClass(MarketingAccountStats.class);
        verify(repository, times(3)).save(c.capture());
        assertThat(c.getAllValues()).extracting(MarketingAccountStats::getPlatform)
            .containsExactly("x", "youtube", "instagram");
        assertThat(c.getAllValues()).extracting(MarketingAccountStats::getFollowers)
            .containsExactly(120L, 45L, 70L);
        assertThat(c.getAllValues().get(1).getExtraJson()).contains("\"views\":9000");
        assertThat(c.getAllValues()).allMatch(r -> r.getCollectedOn().equals(DAY));
    }

    @Test
    void sameDayRerun_updatesExistingRow() throws Exception {
        when(asmClient.fetchAccountStats()).thenReturn(om.readTree("{\"x\":{\"followers\":130,\"following\":1}}"));
        MarketingAccountStats existing = MarketingAccountStats.builder().id(7L).platform("x")
            .collectedOn(DAY).followers(120L).build();
        when(repository.findByPlatformAndCollectedOn("x", DAY)).thenReturn(Optional.of(existing));

        assertThat(service.collectAndStore()).isEqualTo(1);
        verify(repository).save(existing);
        assertThat(existing.getFollowers()).isEqualTo(130L);
        assertThat(existing.getId()).isEqualTo(7L);
    }

    @Test
    void platformWithAllNulls_isSkipped() throws Exception {
        when(asmClient.fetchAccountStats()).thenReturn(om.readTree(
            "{\"x\":{\"followers\":null,\"following\":null},\"errors\":{\"x\":\"login wall\"}}"));

        assertThat(service.collectAndStore()).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    void asmFailure_isSwallowed() {
        when(asmClient.fetchAccountStats()).thenThrow(new AsmUnavailableException("404", null));

        assertThat(service.collectAndStore()).isZero();
        verify(repository, never()).save(any());
    }
}
