package com.againspring.api;

import com.againspring.api.visits.PublicVisitController;
import com.againspring.domain.VisitEvent;
import com.againspring.repository.VisitEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PublicVisitController Unit Tests")
class PublicVisitControllerTest {

    @Mock
    private VisitEventRepository visitEventRepository;

    @InjectMocks
    private PublicVisitController controller;

    @Test
    @DisplayName("VisitRequest_validPath_acceptable")
    void testVisitRequestValidPath() {
        PublicVisitController.VisitRequest req = PublicVisitController.VisitRequest.builder()
                .path("/home")
                .utmSource("google")
                .build();

        assertNotNull(req);
        assertEquals("/home", req.getPath());
        assertEquals("google", req.getUtmSource());
    }

    @Test
    @DisplayName("recordVisit_valid → 200 recorded")
    void recordVisit_valid_returnsRecorded() {
        when(visitEventRepository.save(any(VisitEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        MockHttpServletRequest http = new MockHttpServletRequest();
        http.setRemoteAddr("127.0.0.1");

        PublicVisitController.VisitRequest req = PublicVisitController.VisitRequest.builder()
                .path("/community")
                .utmSource("instagram")
                .sessionKey("sess-1")
                .build();

        ResponseEntity<Map<String, String>> res = controller.recordVisit(req, http);

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("recorded", res.getBody().get("status"));
        verify(visitEventRepository).save(any(VisitEvent.class));
    }

    @Test
    @DisplayName("recordVisit_adminPath → 400")
    void recordVisit_adminPath_badRequest() {
        MockHttpServletRequest http = new MockHttpServletRequest();
        PublicVisitController.VisitRequest req = PublicVisitController.VisitRequest.builder()
                .path("/admin/dashboard")
                .sessionKey("sess")
                .build();

        ResponseEntity<Map<String, String>> res = controller.recordVisit(req, http);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        verify(visitEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("recordVisit_pathWithoutSlash → 400")
    void recordVisit_invalidPath_badRequest() {
        MockHttpServletRequest http = new MockHttpServletRequest();
        PublicVisitController.VisitRequest req = PublicVisitController.VisitRequest.builder()
                .path("invalid-path")
                .sessionKey("sess")
                .build();

        ResponseEntity<Map<String, String>> res = controller.recordVisit(req, http);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        verify(visitEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("VisitRequest_nullableFields_allowed")
    void testVisitRequestNullableFields() {
        PublicVisitController.VisitRequest req = PublicVisitController.VisitRequest.builder()
                .path("/page")
                .utmSource(null)
                .utmMedium(null)
                .referrer(null)
                .sessionKey(null)
                .build();

        assertNotNull(req);
        assertEquals("/page", req.getPath());
        assertNull(req.getUtmSource());
        assertNull(req.getUtmMedium());
    }

    @Test
    @DisplayName("VisitRequest_withUtmParams_accepted")
    void testVisitRequestWithUtmParams() {
        PublicVisitController.VisitRequest req = PublicVisitController.VisitRequest.builder()
                .path("/product")
                .utmSource("facebook")
                .utmMedium("social")
                .utmCampaign("Q3_2024")
                .utmContent("post_id_123")
                .build();

        assertNotNull(req);
        assertEquals("facebook", req.getUtmSource());
        assertEquals("social", req.getUtmMedium());
        assertEquals("Q3_2024", req.getUtmCampaign());
        assertEquals("post_id_123", req.getUtmContent());
    }
}
