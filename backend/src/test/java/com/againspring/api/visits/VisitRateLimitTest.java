package com.againspring.api.visits;

import com.againspring.domain.VisitEvent;
import com.againspring.repository.VisitEventRepository;
import com.againspring.service.acquisition.VisitorClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * 방문 계측 rate limit 회귀 테스트.
 *
 * <p>2026-08-29: 이전 구현은 "윈도우 시작 시각"만 저장하고 그 뒤 2초 안의 요청을 전부
 * 거부했다. 모든 페이지뷰를 기록하도록 바뀌면서 홈 → 사연으로 빠르게 이동하는 정상
 * 사용자의 두 번째 방문이 429로 유실됐다(e2e에서 실측). 계측이 사용자 행동을 막아서는
 * 안 되므로, 연속 페이지뷰가 통과하는지를 명시적으로 못박는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VisitRateLimitTest {

    @Mock
    private VisitEventRepository visitEventRepository;

    @Spy
    private VisitorClassifier visitorClassifier = new VisitorClassifier();

    @InjectMocks
    private PublicVisitController controller;

    private static final String BROWSER_UA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/26.6 Mobile/15E148 Safari/604.1";

    @BeforeEach
    void setUp() {
        PublicVisitController.resetRateLimitState();
        lenient().when(visitEventRepository.save(any(VisitEvent.class)))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    private ResponseEntity<Map<String, String>> visit(String ip, String path) {
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setRemoteAddr(ip);
        http.addHeader("User-Agent", BROWSER_UA);
        return controller.recordVisit(
            PublicVisitController.VisitRequest.builder().path(path).build(), http);
    }

    @Test
    @DisplayName("빠르게 연속으로 페이지를 넘겨도 방문이 유실되지 않는다 (2초 규칙 회귀)")
    void rapidConsecutivePageViewsAreRecorded() {
        // 홈 → 사연 → 다른 사연을 지연 없이 이동하는 실제 사용자 흐름
        assertThat(visit("1.2.3.4", "/").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(visit("1.2.3.4", "/community").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(visit("1.2.3.4", "/community/post_abc").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("윈도우 안 30건까지 허용하고 31번째부터 429")
    void blocksBeyondWindowLimit() {
        for (int i = 0; i < 30; i++) {
            assertThat(visit("5.6.7.8", "/p" + i).getStatusCode())
                .as("요청 %d", i + 1).isEqualTo(HttpStatus.OK);
        }
        assertThat(visit("5.6.7.8", "/p30").getStatusCode())
            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("IP가 다르면 서로의 한도에 영향을 주지 않는다")
    void limitIsPerIp() {
        for (int i = 0; i < 30; i++) {
            visit("9.9.9.9", "/p" + i);
        }
        assertThat(visit("9.9.9.9", "/blocked").getStatusCode())
            .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(visit("10.10.10.10", "/ok").getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
