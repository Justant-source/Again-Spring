package com.againspring.aiuser.orchestrator;

import com.againspring.aiuser.orchestrator.safety.ProofreadQualityGate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-08-16 shortform-content-quality fix: 게시 직전 교정 결과가 원문의 구조(줄 수)와
 * 길이를 벗어나면 fail-closed 해야 한다 — "의미·구조 보존" 규칙이 실제로 지켜졌는지의
 * 결정론적 backstop.
 */
class ProofreadQualityGateTest {

    private static final String ORIGINAL = "이거 진짜 이해 안 됨\n남자친구가 어제 갔어 카페에서\n됬어 그냥 넘어가려고 했는데";

    @Test
    void spellingOnlyFixPasses() {
        String corrected = "이거 진짜 이해 안 됨\n남자친구가 어제 갔어 카페에서\n됐어 그냥 넘어가려고 했는데";
        ProofreadQualityGate.Result result = ProofreadQualityGate.validate(ORIGINAL, corrected);
        assertTrue(result.passed());
        assertNull(result.reason());
    }

    @Test
    void identicalTextPasses() {
        ProofreadQualityGate.Result result = ProofreadQualityGate.validate(ORIGINAL, ORIGINAL);
        assertTrue(result.passed());
    }

    @Test
    void emptyCorrectedFails() {
        ProofreadQualityGate.Result result = ProofreadQualityGate.validate(ORIGINAL, "");
        assertFalse(result.passed());
        assertEquals("PROOFREAD_EMPTY", result.reason());
    }

    @Test
    void nullCorrectedFails() {
        ProofreadQualityGate.Result result = ProofreadQualityGate.validate(ORIGINAL, null);
        assertFalse(result.passed());
        assertEquals("PROOFREAD_EMPTY", result.reason());
    }

    @Test
    void lineCountChangedFails() {
        // 원문 3줄 → 교정 2줄(줄바꿈 하나 병합) — capture_split_after_lines 정합성 보호
        String corrected = "이거 진짜 이해 안 됨\n남자친구가 어제 갔어 카페에서 됐어 그냥 넘어가려고 했는데";
        ProofreadQualityGate.Result result = ProofreadQualityGate.validate(ORIGINAL, corrected);
        assertFalse(result.passed());
        assertEquals("PROOFREAD_STRUCTURE_CHANGED", result.reason());
    }

    @Test
    void lengthDriftTooShortFails() {
        String corrected = "짧아짐\n짧아짐\n짧아짐";
        ProofreadQualityGate.Result result = ProofreadQualityGate.validate(ORIGINAL, corrected);
        assertFalse(result.passed());
        assertEquals("PROOFREAD_LENGTH_DRIFT", result.reason());
    }

    @Test
    void lengthDriftTooLongFails() {
        String corrected = ORIGINAL + ORIGINAL.replace("\n", " ") + ORIGINAL.replace("\n", " ");
        ProofreadQualityGate.Result result = ProofreadQualityGate.validate(ORIGINAL, corrected);
        assertFalse(result.passed());
        assertEquals("PROOFREAD_LENGTH_DRIFT", result.reason());
    }

    @Test
    void missingOriginalFails() {
        ProofreadQualityGate.Result result = ProofreadQualityGate.validate("", "무엇");
        assertFalse(result.passed());
        assertEquals("PROOFREAD_MISSING_ORIGINAL", result.reason());
    }
}
