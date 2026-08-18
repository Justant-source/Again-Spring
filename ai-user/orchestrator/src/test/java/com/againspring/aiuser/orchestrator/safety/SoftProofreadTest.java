package com.againspring.aiuser.orchestrator.safety;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SoftProofreadTest {

    @Test
    void skipsCleanCommunityText() {
        assertFalse(SoftProofread.needsLlm("어제 시어머니가 오셔서 아이 밥만 챙겼음\n진짜 황당함"));
    }

    @Test
    void flagsCommonSpellingMistake() {
        assertTrue(SoftProofread.needsLlm("어제 일이 됬어 그냥 넘어가려고 했는데"));
    }

    @Test
    void resolveKeepsOriginalWhenLlmMissing() {
        String original = "어제 일이 됬어";
        assertEquals(original, SoftProofread.resolve(original, Optional.empty(), true));
    }

    @Test
    void resolveKeepsOriginalWhenStructureChanges() {
        String original = "첫째줄\n됬어";
        assertEquals(original, SoftProofread.resolve(original, Optional.of("첫째줄 됐어"), true));
    }

    @Test
    void resolveKeepsOriginalWhenUnsafe() {
        String original = "어제 일이 됬어";
        assertEquals(original, SoftProofread.resolve(original, Optional.of("어제 일이 됐어"), false));
    }

    @Test
    void resolveAppliesSpellingOnlyFix() {
        String original = "어제 일이 됬어";
        assertEquals("어제 일이 됐어", SoftProofread.resolve(original, Optional.of("어제 일이 됐어"), true));
    }
}
