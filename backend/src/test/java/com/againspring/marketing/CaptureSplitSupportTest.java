package com.againspring.marketing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureSplitSupportTest {

    @Test
    void shortBodyIsSinglePart() {
        CaptureSplitSupport.ResolvedCapture r = CaptureSplitSupport.resolveSolo(blocks(5), null);
        assertEquals(5, r.captureBlockCount());
        assertTrue(r.splits().isEmpty());
    }

    @Test
    void twentyBlocksPreferThreeSemanticPartsFromHeuristic() {
        CaptureSplitSupport.ResolvedCapture r = CaptureSplitSupport.resolveSolo(blocks(20), null);
        assertEquals(20, r.captureBlockCount());
        assertEquals(2, r.splits().size()); // 3 parts → 2 cuts
        assertTrue(r.splits().get(0) <= CaptureSplitSupport.SHORT_POST_MAX_BLOCKS);
    }

    @Test
    void acceptsValidProposedCuts() {
        CaptureSplitSupport.ResolvedCapture r =
                CaptureSplitSupport.resolveSolo(blocks(20), List.of(7, 14));
        assertEquals(List.of(7, 14), r.splits());
        assertEquals(20, r.captureBlockCount());
    }

    @Test
    void truncatesPastMaxPartsBudget() {
        // 40 blocks, max 4 parts × 8 = 32 capture blocks
        CaptureSplitSupport.ResolvedCapture r = CaptureSplitSupport.resolveSolo(blocks(40), null);
        assertEquals(32, r.captureBlockCount());
        assertEquals(3, r.splits().size());
    }

    @Test
    void pairedShrinksPartnerFirst() {
        CaptureSplitSupport.PairedCapture both = CaptureSplitSupport.resolvePaired(
                blocks(32), null, // author uses 4 parts
                blocks(20), null);
        assertEquals(4, CaptureSplitSupport.partCount(both.author().splits(), both.author().captureBlockCount()));
        // remaining budget 2 for partner
        assertEquals(2, CaptureSplitSupport.partCount(both.partner().splits(), both.partner().captureBlockCount()));
        assertTrue(both.partner().captureBlockCount() <= 16);
    }

    @Test
    void resolveSoloFallsBackWhenSingleCutLeavesAnOversizedTail() {
        CaptureSplitSupport.ResolvedCapture cut = CaptureSplitSupport.resolveSolo(blocks(20), List.of(7));
        assertEquals(List.of(7, 14), cut.splits());
        assertTrue(CaptureSplitSupport.resolveSolo(blocks(5), null).splits().isEmpty());
    }

    @Test
    void coalesceProposedPrefersList() {
        assertEquals(List.of(3, 6), CaptureSplitSupport.coalesceProposed(List.of(3, 6), 1));
        assertEquals(List.of(9), CaptureSplitSupport.coalesceProposed(null, 9));
        assertNull(CaptureSplitSupport.coalesceProposed(null, null));
    }

    // ── 시각 줄 가중합 기준 ──

    private static String chars(int n) {
        return "가".repeat(n);
    }

    private static String join(int count, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append('\n');
            sb.append(chars(len));
        }
        return sb.toString();
    }

    @Test
    void legacyEightBlocksOf45CharsStaysUncut() {
        CaptureSplitSupport.ResolvedCapture r = CaptureSplitSupport.resolveSolo(join(8, 45), null);
        assertEquals(8, r.captureBlockCount());
        assertTrue(r.splits().isEmpty());
    }

    @Test
    void legacyEightBlocksOf60CharsStillUncut() {
        // 60자 = 3줄 → 8×3=24줄
        assertTrue(CaptureSplitSupport.resolveSolo(join(8, 60), null).splits().isEmpty());
    }

    @Test
    void legacyElevenBlocksOf45CharsCutAtSix() {
        CaptureSplitSupport.ResolvedCapture r = CaptureSplitSupport.resolveSolo(join(11, 45), null);
        assertEquals(11, r.captureBlockCount());
        assertEquals(List.of(6), r.splits());
    }

    @Test
    void legacyTwentyBlocksOf45CharsCutAtSevenAndFourteen() {
        CaptureSplitSupport.ResolvedCapture r = CaptureSplitSupport.resolveSolo(join(20, 45), null);
        assertEquals(20, r.captureBlockCount());
        assertEquals(List.of(7, 14), r.splits());
    }

    @Test
    void threeLongBlocksOf300CharsSplitOnePerPart() {
        CaptureSplitSupport.ResolvedCapture r = CaptureSplitSupport.resolveSolo(join(3, 300), null);
        assertEquals(3, r.captureBlockCount());
        assertEquals(List.of(1, 2), r.splits());
    }

    @Test
    void sixBlocksOf150CharsSplitInHalf() {
        CaptureSplitSupport.ResolvedCapture r = CaptureSplitSupport.resolveSolo(join(6, 150), null);
        assertEquals(6, r.captureBlockCount());
        assertEquals(List.of(3), r.splits());
    }

    @Test
    void singleNineHundredCharBlockIsOnePartWithoutError() {
        CaptureSplitSupport.ResolvedCapture r = CaptureSplitSupport.resolveSolo(chars(900), null);
        assertEquals(1, r.captureBlockCount());
        assertTrue(r.splits().isEmpty());
    }

    @Test
    void oversizedBlockBecomesItsOwnPart() {
        String body = chars(100) + "\n" + chars(600) + "\n" + chars(100);
        CaptureSplitSupport.ResolvedCapture r = CaptureSplitSupport.resolveSolo(body, null);
        assertEquals(3, r.captureBlockCount());
        assertEquals(List.of(1, 2), r.splits());
    }

    @Test
    void truncatesByLineBudgetWhenBlocksAreLong() {
        // 14줄 블록 6개 → 파트당 1블록, 최대 4파트 → 4블록만 캡처
        CaptureSplitSupport.ResolvedCapture r = CaptureSplitSupport.resolveSolo(join(6, 300), null);
        assertEquals(4, r.captureBlockCount());
        assertEquals(List.of(1, 2, 3), r.splits());
    }

    @Test
    void acceptsProposedCutsWithinLineBudget() {
        assertEquals(List.of(3), CaptureSplitSupport.resolveSolo(join(6, 150), List.of(3)).splits());
    }

    @Test
    void rejectsProposedCutsExceedingLineBudgetAndFallsBackToHeuristic() {
        // 4블록×7줄=28줄 > 24 → 제안 거부, 휴리스틱 [3]
        assertEquals(List.of(3), CaptureSplitSupport.resolveSolo(join(6, 150), List.of(4)).splits());
    }

    @Test
    void pairedBudgetUsesLineWeights() {
        CaptureSplitSupport.PairedCapture both = CaptureSplitSupport.resolvePaired(
                join(6, 300), null,   // 작성자: 4파트로 캡
                join(12, 150), null); // 상대방: 남은 2파트
        assertEquals(4, CaptureSplitSupport.partCount(both.author().splits(), both.author().captureBlockCount()));
        assertEquals(2, CaptureSplitSupport.partCount(both.partner().splits(), both.partner().captureBlockCount()));
        assertEquals(6, both.partner().captureBlockCount());
        assertEquals(List.of(3), both.partner().splits());
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
