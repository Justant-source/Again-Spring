package com.againspring.api.visits;

import com.againspring.domain.VisitEvent;
import com.againspring.repository.VisitEventRepository;
import com.againspring.service.acquisition.VisitorClassifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PublicVisitController 계약 테스트.
 *
 * <p>배경(2026-08-29 인시던트): {@code frontend/lib/api/visits.ts}가 snake_case
 * (utm_source, session_key)로 요청 본문을 보냈는데 {@link PublicVisitController.VisitRequest}는
 * camelCase(utmSource, sessionKey) 필드를 기대했다. Jackson은 알 수 없는 프로퍼티를 조용히
 * 버렸고(에러 없음), path·referrer만 우연히 같은 이름이라 살아남았다. 그 결과
 * {@code visit_events.session_key}는 한 달간 100% NULL이었고 UTM 귀속은 전량 유실됐다.
 *
 * <p>기존 {@code com.againspring.api.PublicVisitControllerTest}는 {@link
 * PublicVisitController.VisitRequest}를 항상 Java 빌더로 직접 생성해 호출한다 — 즉
 * 실제 HTTP 요청이 거치는 JSON 역직렬화 단계를 한 번도 통과하지 않는다. 그래서 그 테스트는
 * 이 버그를 절대 잡을 수 없었다. 이 클래스는 그 구멍을 메운다: 실제 JSON 문자열을
 * Spring이 쓰는 것과 동일한 방식으로 구성한 {@link ObjectMapper}로 역직렬화해 필드가
 * 채워지는지(혹은 과거처럼 조용히 버려지는지) 직접 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PublicVisitController 계약 테스트 — JSON 필드명·봇 판정·admin 차단")
class PublicVisitControllerTest {

    /**
     * Spring Boot가 자동구성하는 ObjectMapper와 동일한 방식으로 만든다.
     * {@link Jackson2ObjectMapperBuilder}는 기본값으로
     * {@code DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES}를 끈다 — 이것이 바로
     * snake_case 필드가 예외 없이 "조용히" 사라졌던 이유다. 순수 {@code new ObjectMapper()}는
     * 이 기능이 기본 켜져 있어(예외 발생) 실제 운영 동작을 재현하지 못하므로 쓰지 않는다.
     */
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();

    @Mock
    private VisitEventRepository visitEventRepository;

    private final VisitorClassifier visitorClassifier = new VisitorClassifier();

    private PublicVisitController controller() {
        return new PublicVisitController(visitEventRepository, visitorClassifier);
    }

    // ── 1. Jackson 필드명 계약 ────────────────────────────────────────────

    @Test
    @DisplayName("camelCase JSON → 모든 필드가 채워진다 (프런트가 실제로 보내는 형식)")
    void camelCaseJson_allFieldsPopulated() throws Exception {
        String json = """
            {
              "path": "/community",
              "utmSource": "instagram",
              "utmMedium": "social",
              "utmCampaign": "asm-job-1",
              "utmContent": "post_id_123",
              "referrer": "https://instagram.com",
              "sessionKey": "sess-camel-1",
              "visitorKey": "visitor-camel-1"
            }
            """;

        PublicVisitController.VisitRequest req =
                mapper.readValue(json, PublicVisitController.VisitRequest.class);

        assertThat(req.getPath()).isEqualTo("/community");
        assertThat(req.getUtmSource()).isEqualTo("instagram");
        assertThat(req.getUtmMedium()).isEqualTo("social");
        assertThat(req.getUtmCampaign()).isEqualTo("asm-job-1");
        assertThat(req.getUtmContent()).isEqualTo("post_id_123");
        assertThat(req.getReferrer()).isEqualTo("https://instagram.com");
        assertThat(req.getSessionKey()).isEqualTo("sess-camel-1");
        assertThat(req.getVisitorKey()).isEqualTo("visitor-camel-1");
    }

    @Test
    @DisplayName("🔴 회귀 문서화: snake_case JSON을 보내면 UTM·세션·방문자 필드가 조용히 유실된다")
    void snakeCaseJson_utmAndSessionFieldsSilentlyDropped() throws Exception {
        // 2026-08-29 인시던트 재현: 고쳐지기 전 frontend/lib/api/visits.ts가 실제로
        // 보내던 본문 형태. path·referrer는 단어가 하나뿐이라 우연히 살아남고, 나머지
        // (utm_source, utm_medium, utm_campaign, utm_content, session_key, visitor_key)는
        // 매칭되는 camelCase 프로퍼티가 없어 예외 없이 버려진다.
        String legacyBuggyJson = """
            {
              "path": "/community",
              "utm_source": "instagram",
              "utm_medium": "social",
              "utm_campaign": "asm-job-1",
              "utm_content": "post_id_123",
              "referrer": "https://instagram.com",
              "session_key": "sess-snake-1",
              "visitor_key": "visitor-snake-1"
            }
            """;

        PublicVisitController.VisitRequest req =
                mapper.readValue(legacyBuggyJson, PublicVisitController.VisitRequest.class);

        // 이름이 겹치는 필드만 살아남는다 — 이것이 인시던트 당시 "일부만 살아있어" 발견이
        // 늦어진 이유다.
        assertThat(req.getPath()).isEqualTo("/community");
        assertThat(req.getReferrer()).isEqualTo("https://instagram.com");

        // camelCase와 철자가 다른 나머지는 전부 NULL — 이 assertion들이 실패한다면
        // (즉 값이 채워진다면) DTO나 ObjectMapper 설정이 바뀌어 이 인시던트 클래스가
        // 더 이상 재현되지 않는다는 뜻이니, 이 테스트를 갱신할 것.
        assertThat(req.getUtmSource()).isNull();
        assertThat(req.getUtmMedium()).isNull();
        assertThat(req.getUtmCampaign()).isNull();
        assertThat(req.getUtmContent()).isNull();
        assertThat(req.getSessionKey()).isNull();
        assertThat(req.getVisitorKey()).isNull();
    }

    @Test
    @DisplayName("camelCase JSON → 컨트롤러를 거쳐 저장되는 VisitEvent까지 필드가 살아있다")
    void camelCaseJson_throughController_persistsAllFields() throws Exception {
        when(visitEventRepository.save(any(VisitEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setRemoteAddr("10.0.0.101");
        http.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/125.0.0.0 Safari/537.36");

        String json = """
            {
              "path": "/community",
              "utmSource": "instagram",
              "sessionKey": "sess-e2e-1",
              "visitorKey": "visitor-e2e-1"
            }
            """;
        PublicVisitController.VisitRequest req =
                mapper.readValue(json, PublicVisitController.VisitRequest.class);

        ResponseEntity<Map<String, String>> res = controller().recordVisit(req, http);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        ArgumentCaptor<VisitEvent> captor = ArgumentCaptor.forClass(VisitEvent.class);
        verify(visitEventRepository).save(captor.capture());
        VisitEvent saved = captor.getValue();

        assertThat(saved.getUtmSource()).isEqualTo("instagram");
        assertThat(saved.getSessionKey()).isEqualTo("sess-e2e-1");
        assertThat(saved.getVisitorKey()).isEqualTo("visitor-e2e-1");
    }

    // ── 2. 봇 판정 연동 ──────────────────────────────────────────────────

    @Test
    @DisplayName("봇 User-Agent(Googlebot)로 방문하면 is_bot=true로 저장된다 (행은 보존)")
    void recordVisit_botUserAgent_storedWithBotFlagTrue() {
        when(visitEventRepository.save(any(VisitEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setRemoteAddr("10.0.0.102");
        http.addHeader("User-Agent", "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)");

        PublicVisitController.VisitRequest req = PublicVisitController.VisitRequest.builder()
                .path("/community")
                .sessionKey("sess-bot-1")
                .build();

        ResponseEntity<Map<String, String>> res = controller().recordVisit(req, http);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        ArgumentCaptor<VisitEvent> captor = ArgumentCaptor.forClass(VisitEvent.class);
        verify(visitEventRepository).save(captor.capture());
        assertThat(captor.getValue().isBot()).isTrue();
    }

    @Test
    @DisplayName("일반 브라우저 User-Agent는 is_bot=false로 저장된다")
    void recordVisit_normalUserAgent_storedWithBotFlagFalse() {
        when(visitEventRepository.save(any(VisitEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setRemoteAddr("10.0.0.103");
        http.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/125.0.0.0 Safari/537.36");

        PublicVisitController.VisitRequest req = PublicVisitController.VisitRequest.builder()
                .path("/community")
                .sessionKey("sess-human-1")
                .build();

        ResponseEntity<Map<String, String>> res = controller().recordVisit(req, http);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        ArgumentCaptor<VisitEvent> captor = ArgumentCaptor.forClass(VisitEvent.class);
        verify(visitEventRepository).save(captor.capture());
        assertThat(captor.getValue().isBot()).isFalse();
    }

    // ── 3. /admin 경로 차단 ─────────────────────────────────────────────

    @Test
    @DisplayName("/admin 경로는 400으로 거부되고 저장되지 않는다")
    void recordVisit_adminPath_rejectedAndNeverSaved() {
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setRemoteAddr("10.0.0.104");
        PublicVisitController.VisitRequest req = PublicVisitController.VisitRequest.builder()
                .path("/admin/dashboard")
                .sessionKey("sess-admin-1")
                .build();

        ResponseEntity<Map<String, String>> res = controller().recordVisit(req, http);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(visitEventRepository, never()).save(any());
    }
}
