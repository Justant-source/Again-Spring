package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CaptureSplitPlannerTest {

    private static String blocks(int n, int len) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add("가".repeat(len));
        return String.join("\n", l);
    }

    /** 컷이 BE/CaptureSplitSupport 계약(1-based, 파트 1~8블록, 마지막 파트 비지 않음, 컷≤3)을 지키는지. */
    private static void assertContract(int n, List<Integer> cuts) {
        assertNotNull(cuts);
        assertTrue(cuts.size() >= 1 && cuts.size() <= 3, "cuts=" + cuts);
        int prev = 0;
        for (int c : cuts) {
            assertTrue(c > prev && c < n, "cut " + c + " out of order/range n=" + n);
            assertTrue(c - prev >= 1 && c - prev <= 8, "part size " + (c - prev));
            prev = c;
        }
        assertTrue(n - prev >= 1 && n - prev <= 8, "last part " + (n - prev));
    }

    @Test
    void nullAndBlankAndShortReturnNull() {
        assertNull(CaptureSplitPlanner.plan(null));
        assertNull(CaptureSplitPlanner.plan("  \n \n"));
        assertNull(CaptureSplitPlanner.plan(blocks(1, 30)));
        assertNull(CaptureSplitPlanner.plan(blocks(8, 30)));
    }

    @Test
    void blankLinesAreNotBlocks() {
        // 빈 줄 포함 16줄이지만 실제 블록은 8 → null
        String body = blocks(8, 10).replace("\n", "\n\n");
        assertNull(CaptureSplitPlanner.plan(body));
    }

    @Test
    void nineBlocksSplitsIntoTwoParts() {
        List<Integer> cuts = CaptureSplitPlanner.plan(blocks(9, 20));
        assertContract(9, cuts);
        assertEquals(1, cuts.size());
    }

    @Test
    void sixteenEqualBlocksSplitEvenly() {
        assertEquals(List.of(8), CaptureSplitPlanner.plan(blocks(16, 20)));
    }

    @Test
    void seventeenBlocksNeedThreeParts() {
        List<Integer> cuts = CaptureSplitPlanner.plan(blocks(17, 20));
        assertContract(17, cuts);
        assertEquals(2, cuts.size());
    }

    @Test
    void thirtyTwoBlocksFourPartsAndThirtyThreeReturnsNull() {
        List<Integer> cuts = CaptureSplitPlanner.plan(blocks(32, 10));
        assertContract(32, cuts);
        assertEquals(List.of(8, 16, 24), cuts);
        assertNull(CaptureSplitPlanner.plan(blocks(33, 10)));
    }

    @Test
    void longBlocksSplitByVisualLineWeight() {
        // 200자 블록 = 10줄. 4개(40줄)+짧은 8블록(8줄): 파트당 24줄 예산 → 긴 블록은 파트당 2개
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) sb.append("가".repeat(200)).append('\n');
        for (int i = 0; i < 8; i++) sb.append("나".repeat(10)).append('\n');
        List<Integer> cuts = CaptureSplitPlanner.plan(sb.toString().stripTrailing());
        assertContract(12, cuts);
        assertEquals(List.of(2, 4), cuts);
    }

    @Test
    void fewBlocksButOverLineBudgetStillSplits() {
        // 블록 3개 x 300자(14줄) = 42줄 > 24 → 컷 필요
        String body = blocks(3, 300);
        assertEquals(List.of(1, 2), CaptureSplitPlanner.plan(body));
    }

    @Test
    void eightBlocksWithinBudgetIsNullButOverBudgetIsNot() {
        assertNull(CaptureSplitPlanner.plan(blocks(8, 44)));      // 8*2=16줄
        assertNotNull(CaptureSplitPlanner.plan(blocks(8, 88)));   // 8*4=32줄
    }

    @Test
    void singleGiantBlockIsItsOwnPartWithoutException() {
        String giant = "가".repeat(700); // 32줄 > 24
        assertNull(CaptureSplitPlanner.plan(giant));
        List<Integer> cuts = CaptureSplitPlanner.plan("짧은 앞줄\n" + giant + "\n짧은 뒷줄");
        assertNotNull(cuts);
        assertEquals(List.of(1, 2), cuts);
    }

    @Test
    void impossibleBudgetFallsBackToBlockCountOnly() {
        // 9블록 x 700자(32줄): 줄 예산 불가 → 블록 수 제약만으로 분할(예외 없음)
        String body = blocks(9, 700);
        List<Integer> cuts = CaptureSplitPlanner.plan(body);
        assertContract(9, cuts);
    }

    @Test
    void deterministic() {
        String body = blocks(20, 17);
        assertEquals(CaptureSplitPlanner.plan(body), CaptureSplitPlanner.plan(body));
        assertContract(20, CaptureSplitPlanner.plan(body));
    }

    @Test
    void plannerOutputPassesServiceSanitizer() {
        for (int n = 9; n <= 32; n++) {
            String body = blocks(n, 5 + n);
            List<Integer> cuts = CaptureSplitPlanner.plan(body);
            assertContract(n, cuts);
            assertEquals(cuts, StructuredGenerationService.sanitizeCaptureSplits(body, cuts), "n=" + n);
        }
    }
}
