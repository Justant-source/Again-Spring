package com.againspring.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT token generation and validation service.
 * Uses JJWT library (io.jsonwebtoken) with HS256 algorithm.
 */
@Component
@Slf4j
public class JwtService {

    private final String jwtSecret;
    private final long expirationMs;
    private final long guestExpirationMs = 7200000; // 2 hours

    public JwtService(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        this.jwtSecret = jwtSecret;
        this.expirationMs = expirationMs;
    }

    /**
     * Generate access token for a registered user.
     *
     * @param userId the user ID
     * @param email  the user email
     * @return JWT token string
     */
    public String generateAccessToken(String userId, String email) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Generate short-lived guest token.
     *
     * @param guestId the guest ID
     * @return JWT token string (2h expiration)
     */
    public String generateGuestToken(String guestId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + guestExpirationMs);

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(guestId)
                .claim("type", "guest")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * Parse and validate JWT token, returning claims.
     *
     * @param token the JWT token string
     * @return Claims object if valid
     * @throws JwtException if token is invalid or expired
     */
    public Claims parseToken(String token) throws JwtException {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extract user ID (subject) from token.
     *
     * @param token the JWT token string
     * @return Optional containing user ID if valid, empty otherwise
     */
    public Optional<String> extractUserId(String token) {
        try {
            Claims claims = parseToken(token);
            return Optional.of(claims.getSubject());
        } catch (JwtException e) {
            log.debug("Failed to extract user ID from token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extract email claim from token.
     *
     * @param token the JWT token string
     * @return Optional containing email if present, empty otherwise
     */
    public Optional<String> extractEmail(String token) {
        try {
            Claims claims = parseToken(token);
            return Optional.ofNullable(claims.get("email", String.class));
        } catch (JwtException e) {
            log.debug("Failed to extract email from token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get token type claim (access or guest).
     *
     * @param token the JWT token string
     * @return token type, defaults to "access"
     */
    public String getTokenType(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.get("type", String.class) != null ? claims.get("type", String.class) : "access";
        } catch (JwtException e) {
            log.debug("Failed to extract token type: {}", e.getMessage());
            return "unknown";
        }
    }
}
