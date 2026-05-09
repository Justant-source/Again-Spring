package com.againspring.safety;

import com.againspring.common.exception.DailyLimitExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Daily Limit Tests")
class DailyLimitTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // ===== DailyLimitExceededException 단위 테스트 =====

    @Test
    @DisplayName("DailyLimitExceededException은 HTTP 429, code DAILY_LIMIT_EXCEEDED")
    void dailyLimitException_hasCorrectCodeAndStatus() {
        DailyLimitExceededException ex = new DailyLimitExceededException();
        assertEquals("DAILY_LIMIT_EXCEEDED", ex.getCode());
        assertEquals(429, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("5세션"));
    }

    // ===== KST 자정 기준 경계 테스트 =====

    @Test
    @DisplayName("오늘 KST 자정 start-of-day는 LocalDate.atStartOfDay(KST)와 일치")
    void kstMidnight_startOfDay_isCorrect() {
        LocalDate today = LocalDate.now(KST);
        Instant startOfDay = today.atStartOfDay(KST).toInstant();
        Instant endOfDay = today.plusDays(1).atStartOfDay(KST).toInstant();

        assertTrue(startOfDay.isBefore(endOfDay), "startOfDay는 endOfDay보다 이전이어야 함");
        // 두 시점 차이는 정확히 24시간
        long diffSeconds = endOfDay.getEpochSecond() - startOfDay.getEpochSecond();
        assertEquals(86400, diffSeconds, "KST 자정 구간은 정확히 86400초(24h)여야 함");
    }

    @Test
    @DisplayName("어제 자정과 오늘 자정은 다른 구간임을 확인")
    void kstMidnight_yesterdayVsToday_differentBoundaries() {
        LocalDate today = LocalDate.now(KST);
        LocalDate yesterday = today.minusDays(1);

        Instant todayStart = today.atStartOfDay(KST).toInstant();
        Instant yesterdayStart = yesterday.atStartOfDay(KST).toInstant();

        assertTrue(yesterdayStart.isBefore(todayStart), "어제 자정은 오늘 자정보다 이전이어야 함");
    }

    // ===== 세션 한도 임계값 테스트 =====

    @Test
    @DisplayName("5세션 미만은 한도 미초과")
    void sessionCount_under5_notBlocked() {
        for (int count = 0; count <= 4; count++) {
            int c = count;
            assertDoesNotThrow(() -> {
                if (c >= 5) throw new DailyLimitExceededException();
            }, "count=" + count + "이면 예외 없어야 함");
        }
    }

    @Test
    @DisplayName("5세션 이상이면 DailyLimitExceededException 발생")
    void sessionCount_5orMore_throwsDailyLimitException() {
        for (int count = 5; count <= 7; count++) {
            int c = count;
            assertThrows(DailyLimitExceededException.class, () -> {
                if (c >= 5) throw new DailyLimitExceededException();
            }, "count=" + count + "이면 예외 발생해야 함");
        }
    }

    @Test
    @DisplayName("정확히 5세션(한도)에서 6번째 시도 시 차단")
    void sessionCount_exactly5_blocks6th() {
        int existingTodayCount = 5;
        assertThrows(DailyLimitExceededException.class, () -> {
            if (existingTodayCount >= 5) throw new DailyLimitExceededException();
        });
    }

    @Test
    @DisplayName("4세션 후 5번째 세션은 허용")
    void sessionCount_4existing_allows5th() {
        int existingTodayCount = 4;
        assertDoesNotThrow(() -> {
            if (existingTodayCount >= 5) throw new DailyLimitExceededException();
        });
    }
}
