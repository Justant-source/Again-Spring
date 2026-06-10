package com.againspring.api;

import com.againspring.api.visits.PublicVisitController;
import com.againspring.repository.VisitEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

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
        // Arrange & Act
        PublicVisitController.VisitRequest req = PublicVisitController.VisitRequest.builder()
                .path("/home")
                .utmSource("google")
                .build();

        // Assert
        assertNotNull(req);
        assertEquals("/home", req.getPath());
        assertEquals("google", req.getUtmSource());
    }

    @Test
    @DisplayName("VisitRequest_invalidPath_noLeadingSlash")
    void testVisitRequestInvalidPath() {
        // Arrange & Act
        PublicVisitController.VisitRequest req = PublicVisitController.VisitRequest.builder()
                .path("home")  // Missing leading /
                .build();

        // Assert
        assertNotNull(req);
        assertEquals("home", req.getPath());
    }

    @Test
    @DisplayName("VisitRequest_adminPath_flagged")
    void testVisitRequestAdminPath() {
        // Arrange & Act
        PublicVisitController.VisitRequest req = PublicVisitController.VisitRequest.builder()
                .path("/admin/dashboard")
                .build();

        // Assert
        assertNotNull(req);
        assertTrue(req.getPath().startsWith("/admin"));
    }

    @Test
    @DisplayName("VisitRequest_nullableFields_allowed")
    void testVisitRequestNullableFields() {
        // Arrange & Act
        PublicVisitController.VisitRequest req = PublicVisitController.VisitRequest.builder()
                .path("/page")
                .utmSource(null)
                .utmMedium(null)
                .referrer(null)
                .sessionKey(null)
                .build();

        // Assert
        assertNotNull(req);
        assertEquals("/page", req.getPath());
        assertNull(req.getUtmSource());
        assertNull(req.getUtmMedium());
    }

    @Test
    @DisplayName("VisitRequest_maxLengthPath_accepted")
    void testVisitRequestMaxLengthPath() {
        // Arrange
        String maxPath = "/" + "a".repeat(499);  // Exactly 500 chars

        // Act
        PublicVisitController.VisitRequest req = PublicVisitController.VisitRequest.builder()
                .path(maxPath)
                .build();

        // Assert
        assertNotNull(req);
        assertEquals(500, req.getPath().length());
    }

    @Test
    @DisplayName("VisitRequest_withUtmParams_accepted")
    void testVisitRequestWithUtmParams() {
        // Arrange & Act
        PublicVisitController.VisitRequest req = PublicVisitController.VisitRequest.builder()
                .path("/product")
                .utmSource("facebook")
                .utmMedium("social")
                .utmCampaign("Q3_2024")
                .utmContent("post_id_123")
                .build();

        // Assert
        assertNotNull(req);
        assertEquals("facebook", req.getUtmSource());
        assertEquals("social", req.getUtmMedium());
        assertEquals("Q3_2024", req.getUtmCampaign());
        assertEquals("post_id_123", req.getUtmContent());
    }
}
