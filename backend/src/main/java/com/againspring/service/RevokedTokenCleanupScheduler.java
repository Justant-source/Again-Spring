package com.againspring.service;

import com.againspring.repository.RevokedTokenRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만료된 취소 토큰 정리 스케줄러
 * 매일 4am UTC 실행: 만료된 취소 토큰 삭제
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RevokedTokenCleanupScheduler {

    private final RevokedTokenRepository revokedTokenRepository;

    /**
     * 일일 정리 작업 (4am UTC)
     * cron = "0 0 4 * * *" => 매일 04:00:00 UTC
     */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("Starting revoked token cleanup job");

        try {
            Instant now = Instant.now();
            revokedTokenRepository.deleteExpired(now);
            log.info("Revoked token cleanup job completed");
        } catch (Exception e) {
            log.error("Error during revoked token cleanup job", e);
        }
    }
}
