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
    void legacyResolveSplitReturnsFirstCut() {
        Integer cut = CaptureSplitSupport.resolveSplit(blocks(20), 7);
        assertEquals(7, cut);
        assertNull(CaptureSplitSupport.resolveSplit(blocks(5), null));
    }

    @Test
    void coalesceProposedPrefersList() {
        assertEquals(List.of(3, 6), CaptureSplitSupport.coalesceProposed(List.of(3, 6), 1));
        assertEquals(List.of(9), CaptureSplitSupport.coalesceProposed(null, 9));
        assertNull(CaptureSplitSupport.coalesceProposed(null, null));
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
