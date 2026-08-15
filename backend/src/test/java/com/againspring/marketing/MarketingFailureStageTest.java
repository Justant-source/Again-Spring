package com.againspring.marketing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarketingFailureStageTest {

    @Test
    void allStagesHaveAsPrefix() {
        for (MarketingFailureStage stage : MarketingFailureStage.values()) {
            String tagged = stage.tagged();
            assertTrue(tagged.startsWith("AS:"), "Stage " + stage.name() + " should be prefixed with AS:");
            assertTrue(tagged.contains(stage.name()), "Tagged value should contain stage name");
        }
    }

    @Test
    void tagsHaveCorrectFormat() {
        assertEquals("AS:BRIEF_BUILD", MarketingFailureStage.BRIEF_BUILD.tagged());
        assertEquals("AS:VARIANT_LLM", MarketingFailureStage.VARIANT_LLM.tagged());
        assertEquals("AS:SIBOM_GUARD", MarketingFailureStage.SIBOM_GUARD.tagged());
        assertEquals("AS:QUALITY_GATE", MarketingFailureStage.QUALITY_GATE.tagged());
        assertEquals("AS:ASM_CREATE", MarketingFailureStage.ASM_CREATE.tagged());
        assertEquals("AS:ASM_POLL", MarketingFailureStage.ASM_POLL.tagged());
        assertEquals("AS:PUBLISH_TRIGGER", MarketingFailureStage.PUBLISH_TRIGGER.tagged());
    }

    @Test
    void sevenStagesDefined() {
        assertEquals(7, MarketingFailureStage.values().length);
    }
}
