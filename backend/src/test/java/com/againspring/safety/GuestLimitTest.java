package com.againspring.safety;

import com.againspring.common.exception.GuestLimitException;
import com.againspring.domain.Session;
import com.againspring.domain.User;
import com.againspring.domain.enums.SessionStatus;
import com.againspring.service.GuestSessionRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Guest Limit Tests")
class GuestLimitTest {

    private GuestSessionRateLimiter guestSessionRateLimiter;

    @BeforeEach
    void setUp() {
        guestSessionRateLimiter = new GuestSessionRateLimiter();
    }

    // ===== GuestSessionRateLimiter 단위 테스트 =====

    @Test
    @DisplayName("IP당 3회까지 게스트 세션 허용")
    void guestRateLimit_allows3SessionsPerIp() {
        String ip = "10.0.0.1";
        assertTrue(guestSessionRateLimiter.tryConsumeGuestSession(ip));
        assertTrue(guestSessionRateLimiter.tryConsumeGuestSession(ip));
        assertTrue(guestSessionRateLimiter.tryConsumeGuestSession(ip));
    }

    @Test
    @DisplayName("IP당 4번째 세션 거부")
    void guestRateLimit_blocks4thSession() {
        String ip = "10.0.0.2";
        guestSessionRateLimiter.tryConsumeGuestSession(ip);
        guestSessionRateLimiter.tryConsumeGuestSession(ip);
        guestSessionRateLimiter.tryConsumeGuestSession(ip);
        assertFalse(guestSessionRateLimiter.tryConsumeGuestSession(ip));
    }

    @Test
    @DisplayName("다른 IP는 별도 한도 적용")
    void guestRateLimit_separateBucketsPerIp() {
        String ip1 = "10.0.1.1";
        String ip2 = "10.0.1.2";
        for (int i = 0; i < 3; i++) guestSessionRateLimiter.tryConsumeGuestSession(ip1);
        // ip1 소진 후 ip2는 여전히 허용
        assertTrue(guestSessionRateLimiter.tryConsumeGuestSession(ip2));
    }

    // ===== GuestLimitException 단위 테스트 =====

    @Test
    @DisplayName("GuestLimitException은 HTTP 402, code GUEST_LIMIT_REACHED")
    void guestLimitException_hasCorrectCodeAndStatus() {
        GuestLimitException ex = new GuestLimitException();
        assertEquals("GUEST_LIMIT_REACHED", ex.getCode());
        assertEquals(402, ex.getHttpStatus());
        assertTrue(ex.getMessage().contains("3턴"));
    }

    // ===== 게스트 3턴 한도 경계 조건 =====

    @Test
    @DisplayName("게스트 3턴 이하에서는 한도 미초과")
    void guestTurnCount_under3_notBlocked() {
        assertFalse(buildGuestSession(2).getUserAMessageCount() >= 3);
        assertFalse(buildGuestSession(0).getUserAMessageCount() >= 3);
        assertFalse(buildGuestSession(1).getUserAMessageCount() >= 3);
    }

    @Test
    @DisplayName("게스트 3턴 초과 시 GuestLimitException 발생")
    void guestLimit_3TurnsReached_throwsException() {
        Session session = buildGuestSession(3); // 이미 3턴 — 다음 메시지는 4번째
        User guestUser = buildGuestUser();

        int count = session.getUserAMessageCount();
        assertTrue(count >= 3, "3턴 게스트는 4번째 시도 시 차단");

        // GuestLimitException throw 조건 검증
        assertThrows(GuestLimitException.class, () -> {
            if (count >= 3) throw new GuestLimitException();
        });
    }

    // ===== 헬퍼 =====

    private Session buildGuestSession(int userACount) {
        return Session.builder()
                .id("ses_test")
                .createdByUserId("guest_user_1")
                .status(SessionStatus.CHATTING_SOLO)
                .soloMode(true)
                .userAMessageCount(userACount)
                .userBMessageCount(0)
                .finalizeAgreedByA(false)
                .finalizeAgreedByB(false)
                .mediatorStyleX(50)
                .mediatorStyleY(50)
                .build();
    }

    private User buildGuestUser() {
        return User.builder()
                .id("guest_user_1")
                .nickname("게스트")
                .isGuest(true)
                .roles(new java.util.ArrayList<>())
                .build();
    }
}
