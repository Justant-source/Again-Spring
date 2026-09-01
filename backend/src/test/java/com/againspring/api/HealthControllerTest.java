package com.againspring.api;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HealthController Unit Tests")
class HealthControllerTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private HealthController controller;

    @Test
    @DisplayName("health_alwaysReturnsUp")
    void health_alwaysReturnsUp() {
        ResponseEntity<Map<String, Object>> res = controller.health();

        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals("UP", res.getBody().get("status"));
    }

    @Test
    @DisplayName("deepHealth_dbOk_returns200WithDbOk")
    void deepHealth_dbOk_returns200WithDbOk() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1);

        ResponseEntity<Map<String, Object>> res = controller.deepHealth();

        assertEquals(HttpStatus.OK, res.getStatusCode());
        Map<String, Object> body = res.getBody();
        assertNotNull(body);
        assertEquals("UP", body.get("status"));
        assertEquals("ok", body.get("db"));
        assertTrue(body.containsKey("dbLatencyMs"));
        assertTrue(body.get("dbLatencyMs") instanceof Integer);
        assertTrue((Integer) body.get("dbLatencyMs") >= 0);
        assertTrue(body.containsKey("checkedAt"));
        assertFalse(body.containsKey("error"));
    }

    @Test
    @DisplayName("deepHealth_dbDown_returns503WithDbFail")
    void deepHealth_dbDown_returns503WithDbFail() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenThrow(new RuntimeException("Connection refused: connect to db:3306 failed with secret-password"));

        ResponseEntity<Map<String, Object>> res = controller.deepHealth();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, res.getStatusCode());
        Map<String, Object> body = res.getBody();
        assertNotNull(body);
        assertEquals("DOWN", body.get("status"));
        assertEquals("fail", body.get("db"));
        assertTrue(body.containsKey("checkedAt"));
        // 내부 정보(예외 메시지·접속 문자열)가 바디에 노출되면 안 된다
        String bodyStr = String.valueOf(body);
        assertFalse(bodyStr.contains("secret-password"));
        assertFalse(bodyStr.contains("Connection refused"));
        assertFalse(body.containsKey("dbLatencyMs"));
    }
}
