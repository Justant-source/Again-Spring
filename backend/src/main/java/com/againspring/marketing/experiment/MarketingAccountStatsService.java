package com.againspring.marketing.experiment;

import com.againspring.domain.marketing.MarketingAccountStats;
import com.againspring.marketing.AsmClient;
import com.againspring.repository.marketing.MarketingAccountStatsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/** ASM에서 계정 지표(팔로워·구독자 등)를 받아 하루 1행 적재한다(설계 §2.5). 같은 날 재실행은 갱신. */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingAccountStatsService {

    private final AsmClient asmClient;
    private final MarketingAccountStatsRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** @return 적재(신규+갱신)한 플랫폼 수. ASM 실패(404 포함)는 경고 로그만 남기고 0. */
    public int collectAndStore() {
        JsonNode body;
        try {
            body = asmClient.fetchAccountStats();
        } catch (Exception e) {
            log.warn("marketing account stats fetch failed (ASM endpoint missing or down): {}", e.getMessage());
            return 0;
        }
        if (body == null || !body.isObject()) {
            log.warn("marketing account stats: empty ASM response");
            return 0;
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        int stored = 0;
        stored += store("x", followersOf(body.get("x"), "followers"), body.get("x"), today);
        stored += store("youtube", followersOf(body.get("youtube"), "subscribers"), body.get("youtube"), today);
        stored += store("instagram", followersOf(body.get("instagram"), "followers"), body.get("instagram"), today);
        JsonNode errors = body.get("errors");
        if (errors != null && errors.isObject() && !errors.isEmpty()) {
            log.warn("marketing account stats partial errors: {}", errors);
        }
        return stored;
    }

    private int store(String platform, Long followers, JsonNode node, LocalDate day) {
        if (node == null || !node.isObject()) {
            return 0;
        }
        boolean anyValue = false;
        Map<String, Object> extra = new LinkedHashMap<>();
        for (var it = node.fields(); it.hasNext(); ) {
            var f = it.next();
            if (!f.getValue().isNull()) {
                anyValue = true;
                extra.put(f.getKey(), f.getValue().isNumber() ? f.getValue().asLong() : f.getValue().asText());
            }
        }
        if (!anyValue) {
            return 0; // 이 플랫폼은 값이 없다 — 빈 행으로 직전 값을 덮지 않는다
        }
        String extraJson;
        try {
            extraJson = objectMapper.writeValueAsString(extra);
        } catch (Exception e) {
            extraJson = null;
        }
        MarketingAccountStats row = repository.findByPlatformAndCollectedOn(platform, day)
            .orElseGet(() -> MarketingAccountStats.builder().platform(platform).collectedOn(day).build());
        if (followers != null || row.getFollowers() == null) {
            row.setFollowers(followers);
        }
        row.setExtraJson(extraJson);
        repository.save(row);
        return 1;
    }

    private static Long followersOf(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(field);
        return v != null && v.isNumber() ? v.asLong() : null;
    }
}
