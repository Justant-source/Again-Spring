package com.againspring.integration;

import com.againspring.marketing.AcquisitionFunnelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V130 백필 SQL(마커 이하)을 실제 MariaDB에서 그대로 실행해 결과를 검증하고,
 * 퍼널 집계의 byClass / realVisitors를 확인한다.
 */
class VisitorClassBackfillIT extends MariaDbIntegrationSupport {

    private static final String CHROME_WIN =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36";
    private static final String LEGACY_PLAYWRIGHT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AcquisitionFunnelService funnelService;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM visit_events");
    }

    private void insert(String key, Instant at, String ua, String country, String campaign, String referrer,
                        int isBot, String preClass) {
        jdbc.update("INSERT INTO visit_events (occurred_at, path, utm_campaign, referrer, visitor_key, user_agent, "
                + "is_bot, country, visitor_class) VALUES (?,?,?,?,?,?,?,?,?)",
            Timestamp.from(at), "/post/1", campaign, referrer, key, ua, isBot, country, preClass);
    }

    private void runBackfill() throws Exception {
        String sql = new ClassPathResource("db/migration/V130__visitor_class.sql")
            .getContentAsString(StandardCharsets.UTF_8);
        String part = sql.substring(sql.indexOf("-- ==== BACKFILL ===="));
        List<String> stmts = Arrays.stream(part.split(";\\s*\\n"))
            .map(s -> Arrays.stream(s.split("\n")).filter(l -> !l.trim().startsWith("--"))
                .collect(Collectors.joining("\n")).trim())
            .filter(s -> !s.isEmpty()).toList();
        assertThat(stmts).hasSize(4);
        stmts.forEach(jdbc::update);
    }

    private String classOf(String key) {
        return jdbc.queryForObject("SELECT visitor_class FROM visit_events WHERE visitor_key=?", String.class, key);
    }

    @Test
    void backfillClassifiesAndIsIdempotentAndOnlyTouchesNull() throws Exception {
        Instant t = Instant.now().minusSeconds(3600);
        insert("self-new", t, CHROME_WIN + " AgainSpringCapture", "KR", null, null, 1, null);
        insert("self-old", t, LEGACY_PLAYWRIGHT, "KR", null, null, 1, null);
        insert("scanner", t, "Google-Safety", "US", "c1", null, 1, null);
        insert("crawler", t, "Mozilla/5.0 (compatible; Googlebot/2.1)", "US", null, null, 1, null);
        insert("social", t, "Twitterbot/1.0", "US", null, null, 1, null);
        insert("auto", t, "curl/8.5.0", null, null, null, 1, null);
        insert("legacy", t, null, null, null, null, 0, null);
        insert("kr", t, CHROME_WIN, "KR", null, null, 0, null);
        insert("geo-none", t, CHROME_WIN, null, null, null, 0, null);
        insert("lone-overseas", t, CHROME_WIN, "BR", "other", null, 0, null);
        // 스캐너 c1 직후 2초 해외 브라우저 → 의심, 같은 조건이어도 referrer 있으면 사람, 15초 뒤면 사람, 한국이면 사람
        insert("proxy", t.plusSeconds(2), CHROME_WIN, "BR", "c1", null, 0, null);
        insert("proxy-ref", t.plusSeconds(2), CHROME_WIN, "BR", "c1", "https://t.co/x", 0, null);
        insert("proxy-late", t.plusSeconds(15), CHROME_WIN, "BR", "c1", null, 0, null);
        insert("proxy-kr", t.plusSeconds(2), CHROME_WIN, "KR", "c1", null, 0, null);
        // 이미 분류된 행은 건드리지 않는다
        insert("preset", t, "curl/8.5.0", "KR", null, null, 1, "HUMAN_KR");

        runBackfill();

        assertThat(classOf("self-new")).isEqualTo("BOT_SELF");
        assertThat(classOf("self-old")).isEqualTo("BOT_SELF");
        assertThat(classOf("scanner")).isEqualTo("BOT_SCANNER");
        assertThat(classOf("crawler")).isEqualTo("BOT_CRAWLER");
        assertThat(classOf("social")).isEqualTo("BOT_SOCIAL_PREVIEW");
        assertThat(classOf("auto")).isEqualTo("BOT_AUTOMATION");
        assertThat(classOf("legacy")).isEqualTo("UNKNOWN_LEGACY");
        assertThat(classOf("kr")).isEqualTo("HUMAN_KR");
        assertThat(classOf("geo-none")).isEqualTo("HUMAN_UNKNOWN_GEO");
        assertThat(classOf("lone-overseas")).isEqualTo("HUMAN_OVERSEAS");
        assertThat(classOf("proxy")).isEqualTo("SUSPECT_SCANNER_PROXY");
        assertThat(classOf("proxy-ref")).isEqualTo("HUMAN_OVERSEAS");
        assertThat(classOf("proxy-late")).isEqualTo("HUMAN_OVERSEAS");
        assertThat(classOf("proxy-kr")).isEqualTo("HUMAN_KR");
        assertThat(classOf("preset")).isEqualTo("HUMAN_KR");

        // is_bot 파생: 사람/레거시=0, 봇/의심=1
        assertThat(jdbc.queryForObject("SELECT is_bot FROM visit_events WHERE visitor_key='proxy'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT is_bot FROM visit_events WHERE visitor_key='legacy'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT is_bot FROM visit_events WHERE visitor_key='kr'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT is_bot FROM visit_events WHERE visitor_key='self-old'", Integer.class)).isEqualTo(1);

        // 멱등
        List<Map<String, Object>> before = jdbc.queryForList("SELECT visitor_key, visitor_class, class_reason, is_bot FROM visit_events ORDER BY visitor_key");
        runBackfill();
        assertThat(jdbc.queryForList("SELECT visitor_key, visitor_class, class_reason, is_bot FROM visit_events ORDER BY visitor_key"))
            .isEqualTo(before);
    }

    @Test
    void funnelExposesByClassAndRealVisitors() {
        Instant t = Instant.now().minusSeconds(600);
        insert("v1", t, CHROME_WIN, "KR", null, null, 0, "HUMAN_KR");
        insert("v1", t, CHROME_WIN, "KR", null, null, 0, "HUMAN_KR");
        insert("v2", t, CHROME_WIN, "US", null, null, 0, "HUMAN_OVERSEAS");
        insert("b1", t, "Google-Safety", "US", null, null, 1, "BOT_SCANNER");
        insert("old", t, CHROME_WIN, "KR", null, null, 0, null);

        AcquisitionFunnelService.FunnelDto dto = funnelService.funnel(7);

        assertThat(dto.byClass()).extracting(AcquisitionFunnelService.ClassRow::visitorClass)
            .containsExactlyInAnyOrder("HUMAN_KR", "BOT_SCANNER", "HUMAN_OVERSEAS", "UNCLASSIFIED");
        AcquisitionFunnelService.ClassRow kr = dto.byClass().get(0);
        assertThat(kr.visits()).isEqualTo(2);
        assertThat(kr.visitors()).isEqualTo(1);
        // 기존 필드 의미 보존: botSplit.human = is_bot 0 전체
        assertThat(dto.botSplit().human()).isEqualTo(4);
        assertThat(dto.botSplit().bot()).isEqualTo(1);
        assertThat(dto.byChannel()).hasSize(1);
        assertThat(dto.byChannel().get(0).realVisitors()).isEqualTo(1);
        assertThat(dto.byChannel().get(0).visitors()).isEqualTo(3);
    }
}
