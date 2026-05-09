package com.againspring.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP당 게스트 세션 생성 횟수 제한 — Bucket4j in-memory (단일 인스턴스 기준).
 * 24시간 내 동일 IP에서 최대 3회 게스트 세션 허용.
 */
@Component
public class GuestSessionRateLimiter {

    private static final int MAX_SESSIONS_PER_IP_PER_DAY = 3;
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
        Bandwidth limit = Bandwidth.classic(
                MAX_SESSIONS_PER_IP_PER_DAY,
                Refill.intervally(MAX_SESSIONS_PER_IP_PER_DAY, Duration.ofHours(24)));
        return Bucket4j.builder().addLimit(limit).build();
    }
}
