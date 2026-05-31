package com.againspring.integration.marketing;

import com.againspring.domain.Session;
import com.againspring.domain.User;
import com.againspring.domain.enums.SessionStatus;
import com.againspring.integration.MariaDbIntegrationSupport;
import com.againspring.repository.SessionRepository;
import com.againspring.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V15.1 — is_test_run=true 세션이 통계 쿼리에서 완전히 제외됨을 검증.
 * 10개 testRun 세션 + 10개 일반 세션 생성 후, 통계 쿼리는 일반 10개만 반환해야 한다.
 */
@TestPropertySource(properties = {
    "app.features.marketing.enabled=true",
    "app.social.master-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class SessionTestRunIsolationTest extends MariaDbIntegrationSupport {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    private static final String REAL_USER_ID = "isolation-real-user1";
    private static final String GUEST_USER_ID = "isolation-guest-user1";

    private final List<String> sessionIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        userRepository.save(User.builder()
                .id(REAL_USER_ID)
                .nickname("격리테스트-일반")
                .isGuest(false)
                .mustChangePassword(false)
                .build());

        userRepository.save(User.builder()
                .id(GUEST_USER_ID)
                .nickname("격리테스트-게스트")
                .isGuest(true)
                .mustChangePassword(false)
                .build());

        for (int i = 0; i < 10; i++) {
            String id = "test-run-session-" + i + "-iso";
            sessionRepository.save(Session.builder()
                    .id(id)
                    .createdByUserId(REAL_USER_ID)
                    .status(SessionStatus.COMPLETED)
                    .testRun(true)
                    .userAMessageCount(5)
                    .build());
            sessionIds.add(id);
        }

        for (int i = 0; i < 8; i++) {
            String id = "real-session-" + i + "-iso";
            sessionRepository.save(Session.builder()
                    .id(id)
                    .createdByUserId(REAL_USER_ID)
                    .status(SessionStatus.COMPLETED)
                    .testRun(false)
                    .userAMessageCount(4)
                    .build());
            sessionIds.add(id);
        }

        // 게스트 세션 2개 (is_test_run=false)
        for (int i = 0; i < 2; i++) {
            String id = "guest-session-" + i + "-iso";
            sessionRepository.save(Session.builder()
                    .id(id)
                    .createdByUserId(GUEST_USER_ID)
                    .status(SessionStatus.CHATTING_SOLO)
                    .testRun(false)
                    .userAMessageCount(2)
                    .build());
            sessionIds.add(id);
        }
    }

    @AfterEach
    void tearDown() {
        sessionRepository.deleteAllById(sessionIds);
        userRepository.deleteById(REAL_USER_ID);
        userRepository.deleteById(GUEST_USER_ID);
    }

    @Test
    @DisplayName("countByCreatedAtBetween: testRun=true 세션 제외 → 일반 10개만 반환")
    void countByCreatedAtBetween_excludesTestRun() {
        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

        long count = sessionRepository.countByCreatedAtBetween(from, to);

        assertThat(count).isEqualTo(10);
    }

    @Test
    @DisplayName("countByStatusAndCreatedAtBetween: COMPLETED 기준으로 testRun 제외")
    void countByStatusAndCreatedAtBetween_excludesTestRun() {
        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

        long completedCount = sessionRepository.countByStatusAndCreatedAtBetween(
                SessionStatus.COMPLETED, from, to);

        // testRun=true인 10개 COMPLETED는 포함되지 않아야 함
        // 일반 8개 COMPLETED만 반환
        assertThat(completedCount).isEqualTo(8);
    }

    @Test
    @DisplayName("countGuestSessionsBetween: 게스트 일반 세션 2개만 반환 (testRun 게스트 세션 없음)")
    void countGuestSessionsBetween_countOnlyRealGuestSessions() {
        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

        long guestCount = sessionRepository.countGuestSessionsBetween(from, to);

        assertThat(guestCount).isEqualTo(2);
    }

    @Test
    @DisplayName("avgTurnsBetween: testRun 세션 제외 후 일반 세션의 평균 턴만 계산")
    void avgTurnsBetween_excludesTestRunFromAverage() {
        Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant to = Instant.now().plus(1, ChronoUnit.HOURS);

        Double avg = sessionRepository.avgTurnsBetween(from, to);

        // 일반: 8개 × 4턴 + 2개 × 2턴 = 32 + 4 = 36 / 10 = 3.6
        // testRun 세션 5턴은 계산에서 제외
        assertThat(avg).isNotNull();
        assertThat(avg).isEqualTo(3.6, org.assertj.core.api.Assertions.within(0.01));
    }

    @Test
    @DisplayName("countByUserInvolvement: REAL_USER의 testRun=true 세션 10개 제외 → 일반 8개만")
    void countByUserInvolvement_excludesTestRun() {
        long count = sessionRepository.countByUserInvolvement(REAL_USER_ID);

        assertThat(count).isEqualTo(8);
    }

    @Test
    @DisplayName("countCompletedByUserInvolvement: testRun COMPLETED 10개 제외 → 일반 COMPLETED 8개만")
    void countCompletedByUserInvolvement_excludesTestRun() {
        long count = sessionRepository.countCompletedByUserInvolvement(REAL_USER_ID);

        assertThat(count).isEqualTo(8);
    }

    @Test
    @DisplayName("findLastSessionCreatedAt: testRun 세션은 최근 시각 계산에서 제외")
    void findLastSessionCreatedAt_excludesTestRun() {
        // testRun=true 세션 10개가 있어도 findLastSessionCreatedAt은 일반 세션 기준으로 반환
        var lastAt = sessionRepository.findLastSessionCreatedAt(REAL_USER_ID);
        assertThat(lastAt).isPresent();

        // testRun=true 세션만 있는 userId에 대해서는 empty 반환
        String onlyTestUserId = "only-test-user-" + UUID.randomUUID().toString().substring(0, 8);
        userRepository.save(User.builder()
                .id(onlyTestUserId)
                .nickname("테스트전용유저")
                .isGuest(false)
                .mustChangePassword(false)
                .build());
        String testOnlySessionId = "test-only-ses-iso";
        sessionRepository.save(Session.builder()
                .id(testOnlySessionId)
                .createdByUserId(onlyTestUserId)
                .status(SessionStatus.CHATTING_SOLO)
                .testRun(true)
                .userAMessageCount(1)
                .build());

        var emptyResult = sessionRepository.findLastSessionCreatedAt(onlyTestUserId);
        assertThat(emptyResult).isEmpty();

        sessionRepository.deleteById(testOnlySessionId);
        userRepository.deleteById(onlyTestUserId);
    }
}
