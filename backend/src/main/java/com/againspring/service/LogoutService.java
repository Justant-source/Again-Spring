package com.againspring.service;

import com.againspring.domain.RevokedToken;
import com.againspring.repository.RevokedTokenRepository;
import com.againspring.security.JwtService;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그아웃 서비스 (JWT 토큰 취소)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogoutService {

    private final RevokedTokenRepository revokedTokenRepository;
    private final JwtService jwtService;

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 토큰 취소
     * Authorization 헤더에서 토큰 추출 후 블랙리스트에 추가
     */
    @Transactional
    public void revokeToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.debug("Invalid or missing Authorization header");
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        // JTI와 만료 시간 추출
        Optional<String> jti = jwtService.extractJti(token);
        Optional<Instant> expiration = jwtService.extractExpiration(token);

        if (jti.isEmpty() || expiration.isEmpty()) {
            log.debug("Failed to extract JTI or expiration from token");
            return;
        }

        // 이미 취소된 토큰인지 확인
        if (revokedTokenRepository.existsByJti(jti.get())) {
            log.debug("Token already revoked: {}", jti.get());
            return;
        }

        // 사용자 ID 추출 (선택사항)
        Optional<String> userId = jwtService.extractUserId(token);

        // 취소된 토큰 저장
        RevokedToken revokedToken = RevokedToken.builder()
                .jti(jti.get())
                .userId(userId.orElse(null))
                .expiresAt(expiration.get())
                .build();

        revokedTokenRepository.save(revokedToken);
        log.info("Token revoked: {} (expires at: {})", jti.get(), expiration.get());
    }
}
