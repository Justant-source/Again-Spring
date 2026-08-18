package com.againspring.aiuser.orchestrator.safety;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Soft pre-publish spelling pass: call the proofread LLM only when the draft looks
 * like it has common Korean spelling/jamo errors, and keep the original body when
 * the call fails or the corrected text drifts (line count / length).
 *
 * <p>Fail-closed proofread (2026-08-16) discarded already-generated posts whenever
 * the corrector changed newlines ({@code PROOFREAD_STRUCTURE_CHANGED}). Nightly
 * logs showed that as the dominant hold-kill after a successful bundle — not source
 * length or special characters.
 */
public final class SoftProofread {

    /**
     * Common hangul spelling / jamo-combination mistakes that the proofread LLM
     * is meant to fix. Community slang and punctuation are intentionally not here.
     */
    private static final Pattern SPELLING_SUSPECT = Pattern.compile(
            "됬|왠일|웬지|않됬|할께요|되요|안되요|"
                    + "[ㄱ-ㅎㅏ-ㅣ]{2,}|"
                    + "됬어|됬다|됬음");

    private SoftProofread() {
    }

    public static boolean needsLlm(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        return SPELLING_SUSPECT.matcher(body).find();
    }

    /**
     * Prefer {@code llmResult} when it is a structural no-op relative to {@code original}
     * and passes {@code safetyOk}. Otherwise return {@code original}.
     */
    public static String resolve(String original, Optional<String> llmResult, boolean safetyOk) {
        if (original == null) {
            return "";
        }
        if (llmResult == null || llmResult.isEmpty()) {
            return original;
        }
        String corrected = llmResult.get();
        ProofreadQualityGate.Result quality = ProofreadQualityGate.validate(original, corrected);
        if (!quality.passed() || !safetyOk) {
            return original;
        }
        return corrected;
    }
}
