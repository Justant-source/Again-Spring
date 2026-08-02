package com.againspring.marketing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureHeightCalculatorTest {

    @Test
    void shortBodyHasNoSplitOrHeight() {
        String body = "한 줄만 있는 짧은 사연입니다.";
        assertNull(CaptureSplitSupport.resolveSplit(body, null));
        assertNull(CaptureHeightCalculator.part1HeightCss("짧은 제목", body, null, false));
    }

    @Test
    void longBodyUsesHeuristicWhenSplitMissing() {
        String body = blocks(15);
        Integer split = CaptureSplitSupport.resolveSplit(body, null);
        assertEquals(9, split); // round(15 * 0.6)
        Double h = CaptureHeightCalculator.part1HeightCss("아무 제목", body, null, false);
        assertTrue(h != null && h > 100);
    }

    @Test
    void prefersValidProposedSplit() {
        String body = blocks(20);
        assertEquals(7, CaptureSplitSupport.resolveSplit(body, 7));
        assertEquals(12, CaptureSplitSupport.resolveSplit(body, 12));
        // out of range → heuristic
        assertEquals(12, CaptureSplitSupport.resolveSplit(body, 20)); // round(20*0.6)=12
    }

    @Test
    void titleLengthDoesNotChangeHeight() {
        String body = blocks(15);
        Double shortTitle = CaptureHeightCalculator.part1HeightCss("짧", body, 8, false);
        Double longTitle = CaptureHeightCalculator.part1HeightCss(
                "아주아주아주 긴 제목이지만 헤더는 말줄임이라 높이 불변", body, 8, false);
        assertEquals(shortTitle, longTitle);
    }

    @Test
    void moreFrontBlocksIncreaseHeight() {
        String body = blocks(16);
        Double h6 = CaptureHeightCalculator.part1HeightCss("t", body, 6, false);
        Double h10 = CaptureHeightCalculator.part1HeightCss("t", body, 10, false);
        assertTrue(h10 > h6);
    }

    @Test
    void visualLinesWrapEstimate() {
        // CHARS_PER_LINE=22 → 45 chars ≈ 3 visual lines
        assertEquals(3, CaptureHeightCalculator.visualLines("가".repeat(45)));
        assertEquals(1, CaptureHeightCalculator.visualLines("짧음"));
    }

    private static String blocks(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            if (i > 1) sb.append('\n');
            sb.append("사연 문장 ").append(i).append(" 입니다.");
        }
        return sb.toString();
    }
}
