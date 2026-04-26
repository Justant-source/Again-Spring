package com.againspring.service.prompt;

import static org.junit.jupiter.api.Assertions.*;

import com.againspring.domain.Session;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DuoBalanceFormatterTest {

    private final DuoBalanceFormatter fmt = new DuoBalanceFormatter();

    @Test
    void render_returnsEmpty_whenSessionNull() {
        assertEquals("", fmt.render(null));
    }

    @Test
    void render_returnsEmpty_whenInsufficientData() {
        Session s = Session.builder().id("s")
            .userAMessageCount(1).userBMessageCount(1).build();
        assertEquals("", fmt.render(s));
    }

    @Test
    void render_returnsEmpty_whenBalanced() {
        Session s = Session.builder().id("s")
            .userAMessageCount(3).userBMessageCount(3)
            .userAEmotionIntensity(new BigDecimal("0.30"))
            .userBEmotionIntensity(new BigDecimal("0.30"))
            .build();
        assertEquals("", fmt.render(s));
    }

    @Test
    void render_emitsBExploreDirective_whenADominantByVolume() {
        Session s = Session.builder().id("s")
            .userAMessageCount(6).userBMessageCount(2)
            .userAEmotionIntensity(new BigDecimal("0.30"))
            .userBEmotionIntensity(new BigDecimal("0.30"))
            .build();
        String out = fmt.render(s);
        assertTrue(out.contains("duo_balance"));
        assertTrue(out.contains("B에게는"));
        assertTrue(out.contains("관심 분배"));
    }

    @Test
    void render_emitsAExploreDirective_whenBDominantByIntensity() {
        Session s = Session.builder().id("s")
            .userAMessageCount(3).userBMessageCount(3)
            .userAEmotionIntensity(new BigDecimal("0.20"))
            .userBEmotionIntensity(new BigDecimal("0.70"))
            .build();
        String out = fmt.render(s);
        assertTrue(out.contains("A에게는"));
    }

    @Test
    void render_avoidsTakingSidesWording() {
        Session s = Session.builder().id("s")
            .userAMessageCount(6).userBMessageCount(2)
            .userAEmotionIntensity(new BigDecimal("0.30"))
            .userBEmotionIntensity(new BigDecimal("0.30"))
            .build();
        String out = fmt.render(s);
        // Verdict-style language must not appear in directives
        assertFalse(out.contains("맞습니다"));
        assertFalse(out.contains("틀렸"));
        assertFalse(out.contains("편들어"));
        assertFalse(out.contains("편들기로"));
    }
}
