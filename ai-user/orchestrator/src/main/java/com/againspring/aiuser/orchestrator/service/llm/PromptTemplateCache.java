package com.againspring.aiuser.orchestrator.service.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * admin이 편집하는 ai_prompt_template(backend 소유)을 orchestrator가 읽어 워커 요청에 promptOverrides로 싣는다.
 * 워커는 DB를 모르므로(무상태) 이 캐시가 유일한 경로다. TTL 5분 = 옛 워커 DB 캐시와 동일.
 */
@Slf4j
@Component
public class PromptTemplateCache {
    private static final Duration TTL = Duration.ofMinutes(5);

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private volatile Map<String, String> last = Map.of();
    private volatile Instant loadedAt = Instant.EPOCH;

    public PromptTemplateCache(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Map<String, String> overrides() {
        Instant now = clock.instant();
        if (Duration.between(loadedAt, now).compareTo(TTL) < 0) return last;
        synchronized (this) {
            if (Duration.between(loadedAt, clock.instant()).compareTo(TTL) < 0) return last;
            try {
                List<Map.Entry<String, String>> rows = jdbc.query(
                    "SELECT `key`, content FROM ai_prompt_template WHERE content IS NOT NULL AND content != ''",
                    (rs, i) -> Map.entry(rs.getString(1), rs.getString(2)));
                Map<String, String> m = new HashMap<>();
                for (var e : rows) m.put(e.getKey(), e.getValue());
                last = Map.copyOf(m);
                loadedAt = clock.instant();
            } catch (Exception e) {
                log.warn("[PromptTemplateCache] load failed, keeping last ({} keys): {}", last.size(), e.getMessage());
                loadedAt = clock.instant(); // 실패도 TTL 동안 재시도 안 함
            }
            return last;
        }
    }
}
