package com.againspring.marketing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureHeightCalculatorTest {

    @Test
    void shortBodyHasNoSplitOrHeight() {
        String body = "한 줄만 있는 짧은 사연입니다.";
        CaptureSplitSupport.ResolvedCapture r = CaptureSplitSupport.resolveSolo(body, null);
        assertTrue(r.splits().isEmpty());
        assertTrue(CaptureHeightCalculator.partHeightsCss("짧은 제목", body, r, false).isEmpty());
        assertNull(CaptureHeightCalculator.part1HeightCss("짧은 제목", body, null, false));
    }

    @Test
    void longBodyUsesHeuristicWhenSplitMissing() {
        String body = blocks(15);
        CaptureSplitSupport.ResolvedCapture r = CaptureSplitSupport.resolveSolo(body, null);
        assertTrue(!r.splits().isEmpty());
        List<Double> hs = CaptureHeightCalculator.partHeightsCss("아무 제목", body, r, false);
        assertEquals(r.splits().size(), hs.size());
        assertTrue(hs.get(0) > 100);
    }

    @Test
    void prefersValidProposedSplit() {
        String body = blocks(20);
        assertEquals(List.of(7, 14), CaptureSplitSupport.resolveSolo(body, List.of(7, 14)).splits());
    }

    @Test
    void titleLengthDoesNotChangeHeight() {
        String body = blocks(15);
        CaptureSplitSupport.ResolvedCapture r = CaptureSplitSupport.resolveSolo(body, List.of(8));
        Double shortTitle = CaptureHeightCalculator.partHeightsCss("짧", body, r, false).get(0);
        Double longTitle = CaptureHeightCalculator.partHeightsCss(
                "아주아주아주 긴 제목이지만 헤더는 말줄임이라 높이 불변", body, r, false).get(0);
        assertEquals(shortTitle, longTitle);
    }

    @Test
    void moreFrontBlocksIncreaseHeight() {
        String body = blocks(16);
        Double h6 = CaptureHeightCalculator.partHeightsCss(
                "t", body, new CaptureSplitSupport.ResolvedCapture(List.of(6), 16), false).get(0);
        Double h8 = CaptureHeightCalculator.partHeightsCss(
                "t", body, new CaptureSplitSupport.ResolvedCapture(List.of(8), 16), false).get(0);
        assertTrue(h8 > h6);
    }

    @Test
    void visualLinesWrapEstimate() {
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
