package com.againspring.service;

import com.againspring.config.UserPermissionsConfig;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP당 게스트 세션 생성 횟수 제한 — Bucket4j in-memory (단일 인스턴스 기준).
 * 한도는 user-permissions.json의 tiers.guest.sessions.dailyLimit에서 로드.
 */
@Component
@RequiredArgsConstructor
public class GuestSessionRateLimiter {

    private final UserPermissionsConfig permissions;
    private final ConcurrentHashMap<String, Bucket> ipBuckets = new ConcurrentHashMap<>();

    /**
     * IP에 대해 게스트 세션 생성 토큰 1개 소비.
     * @return true면 허용, false면 한도 초과
     */
    public boolean tryConsumeGuestSession(String ip) {
        Bucket bucket = ipBuckets.computeIfAbsent(ip, this::newBucket);
        return bucket.tryConsume(1);
    }

    private Bucket newBucket(String ip) {
        int max = permissions.getGuest().getSessions().getDailyLimit();
        Bandwidth limit = Bandwidth.classic(
                max,
                Refill.intervally(max, Duration.ofHours(24)));
        return Bucket4j.builder().addLimit(limit).build();
    }
}
