package com.againspring.aiuser.llm.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** provider별 인증 상태. 토큰을 태우는 canary 없이 실제 호출 결과로만 갱신한다. */
@Component
public class ProviderHealthRegistry {

    private record Down(String reason, Instant since) {}

    private final int ttlMinutes;
    private final Clock clock;
    private final Map<LlmProvider, Down> down = new EnumMap<>(LlmProvider.class);

    public ProviderHealthRegistry(@Value("${llm.auth-down-ttl-minutes:10}") int ttlMinutes, Clock clock) {
        this.ttlMinutes = ttlMinutes;
        this.clock = clock;
    }

    public synchronized void markUp(LlmProvider p) { down.remove(p); }

    public synchronized void markAuthDown(LlmProvider p, String reason) {
        down.put(p, new Down(reason, clock.instant()));
    }

    public synchronized Map<String, Object> snapshot() {
        Instant now = clock.instant();
        Map<String, Object> out = new LinkedHashMap<>();
        for (LlmProvider p : LlmProvider.values()) {
            Down d = down.get(p);
            Map<String, Object> row = new LinkedHashMap<>();
            if (d != null && Duration.between(d.since(), now).toMinutes() < ttlMinutes) {
                row.put("state", "AUTH_DOWN");
                row.put("reason", d.reason());
                row.put("since", d.since().toString());
                row.put("ttlMinutes", ttlMinutes);
            } else {
                if (d != null) down.remove(p);
                row.put("state", "UP");
            }
            out.put(p.name().toLowerCase(Locale.ROOT), row);
        }
        return out;
    }
}
