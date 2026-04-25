package com.againspring.security;

import com.againspring.repository.RevokedTokenRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT authentication filter.
 * Reads "Authorization: Bearer {token}" header, validates via JwtService,
 * and sets SecurityContext with UserDetails.
 *
 * If token is invalid/missing, the request proceeds without auth —
 * downstream endpoint matchers will reject if authentication is required.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final RevokedTokenRepository revokedTokenRepository;

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        try {
            Optional<String> token = extractToken(request);

            if (token.isPresent()) {
                authenticateWithToken(token.get());
            }
        } catch (JwtException e) {
            log.debug("JWT authentication failed: {}", e.getMessage());
            // Continue without setting authentication
        } catch (UsernameNotFoundException e) {
            log.debug("User not found during JWT authentication: {}", e.getMessage());
            // Continue without setting authentication
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract Bearer token from Authorization header.
     */
    private Optional<String> extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return Optional.of(authHeader.substring(BEARER_PREFIX.length()));
        }

        return Optional.empty();
    }

    /**
     * Parse token, load user details, and set authentication.
     * Checks if token is revoked before setting authentication.
     */
    private void authenticateWithToken(String token) throws JwtException, UsernameNotFoundException {
        Optional<String> userId = jwtService.extractUserId(token);

        if (userId.isEmpty()) {
            throw new JwtException("Cannot extract user ID from token");
        }

        // Check if token is revoked
        Optional<String> jti = jwtService.extractJti(token);
        if (jti.isPresent() && revokedTokenRepository.existsByJti(jti.get())) {
            log.debug("Token is revoked: {}", jti.get());
            throw new JwtException("Token has been revoked");
        }

        // Load user details by ID
        org.springframework.security.core.userdetails.UserDetails userDetails =
                userDetailsService.loadUserByUsername(userId.get());

        // Create authentication token
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());

        authentication.setDetails(userDetails);

        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("JWT authentication successful for user: {}", userId.get());
    }
}
