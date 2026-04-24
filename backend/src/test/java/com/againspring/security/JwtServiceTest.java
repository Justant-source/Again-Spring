package com.againspring.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for JwtService.
 */
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        String secret = "test-secret-key-this-should-be-at-least-256-bits-long-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        jwtService = new JwtService(secret, 86400000);
    }

    @Test
    void testGenerateAndParseAccessToken() {
        // Given
        String userId = "usr_123";
        String email = "test@example.com";

        // When
        String token = jwtService.generateAccessToken(userId, email);

        // Then
        assertThat(token).isNotBlank();
        Claims claims = jwtService.parseToken(token);
        assertThat(claims.getSubject()).isEqualTo(userId);
        assertThat(claims.get("email")).isEqualTo(email);
    }

    @Test
    void testExtractUserId() {
        // Given
        String userId = "usr_456";
        String token = jwtService.generateAccessToken(userId, "test@example.com");

        // When
        Optional<String> extracted = jwtService.extractUserId(token);

        // Then
        assertThat(extracted).isPresent().contains(userId);
    }

    @Test
    void testGenerateGuestToken() {
        // Given
        String guestId = "gst_789";

        // When
        String token = jwtService.generateGuestToken(guestId);

        // Then
        assertThat(token).isNotBlank();
        Optional<String> extracted = jwtService.extractUserId(token);
        assertThat(extracted).isPresent().contains(guestId);
    }

    @Test
    void testInvalidTokenThrowsException() {
        // Given
        String invalidToken = "invalid.token.string";

        // When & Then
        assertThatThrownBy(() -> jwtService.parseToken(invalidToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void testExtractUserIdFromInvalidTokenReturnsEmpty() {
        // Given
        String invalidToken = "invalid.token.string";

        // When
        Optional<String> result = jwtService.extractUserId(invalidToken);

        // Then
        assertThat(result).isEmpty();
    }
}
