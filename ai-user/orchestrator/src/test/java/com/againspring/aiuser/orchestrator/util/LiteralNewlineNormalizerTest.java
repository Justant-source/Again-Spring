package com.againspring.aiuser.orchestrator.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LiteralNewlineNormalizerTest {

    @Test
    void convertsLiteralBackslashN() {
        String out = LiteralNewlineNormalizer.normalize("첫줄\\n둘째줄");
        assertFalse(out.contains("\\n"));
        assertEquals("첫줄\n둘째줄", out);
    }

    @Test
    void leavesRealNewlines() {
        assertEquals("a\nb", LiteralNewlineNormalizer.normalize("a\nb"));
    }

    @Test
    void nullSafe() {
        assertNull(LiteralNewlineNormalizer.normalize(null));
        assertEquals("", LiteralNewlineNormalizer.normalize(""));
    }
}
