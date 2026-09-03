package com.againspring.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * LLM 오류·거절·누출 시그니처 SSOT 로더.
 * 데이터는 docs/shared/policies/llm-error-signatures.json 한 파일이다(컨테이너 /app/shared/docs/policies에 :ro 마운트).
 * 파일이 없으면 기동 실패 — 시그니처 없이 게시하는 것이 더 위험하다(절대 규칙 #7).
 */
public final class LlmErrorSignatures {
    public static final String ENV_PATH = "LLM_ERROR_SIGNATURES_PATH";
    private static final List<String> CANDIDATES = List.of(
            "/app/shared/docs/policies/llm-error-signatures.json",
            "../docs/shared/policies/llm-error-signatures.json");      // backend

    private static volatile LlmErrorSignatures instance;

    private final List<String> signatures;
    private final List<Pattern> promptLeakPatterns;
    private final double koreanRatioMin;
    private final int koreanCheckMinChars;

    private LlmErrorSignatures(List<String> signatures, List<Pattern> leaks, double ratio, int minChars) {
        this.signatures = List.copyOf(signatures);
        this.promptLeakPatterns = List.copyOf(leaks);
        this.koreanRatioMin = ratio;
        this.koreanCheckMinChars = minChars;
    }

    public static LlmErrorSignatures get() {
        LlmErrorSignatures local = instance;
        if (local == null) {
            synchronized (LlmErrorSignatures.class) {
                local = instance;
                if (local == null) instance = local = load();
            }
        }
        return local;
    }

    static LlmErrorSignatures load() {
        Path path = resolvePath();
        try {
            JsonNode root = new ObjectMapper().readTree(Files.readString(path));
            List<String> sigs = new ArrayList<>();
            for (JsonNode n : root.path("signatures")) {
                String s = n.asText("").trim().toLowerCase(Locale.ROOT);
                if (!s.isEmpty()) sigs.add(s);
            }
            List<Pattern> leaks = new ArrayList<>();
            for (JsonNode n : root.path("prompt_leak_patterns")) leaks.add(Pattern.compile(n.asText()));
            if (sigs.isEmpty()) throw new IllegalStateException("llm-error-signatures.json has no signatures: " + path);
            return new LlmErrorSignatures(sigs, leaks,
                    root.path("korean_ratio_min").asDouble(0.10),
                    root.path("korean_check_min_chars").asInt(20));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read llm-error-signatures.json at " + path, e);
        }
    }

    private static Path resolvePath() {
        String env = System.getenv(ENV_PATH);
        if (env != null && !env.isBlank() && Files.isRegularFile(Path.of(env))) return Path.of(env);
        for (String c : CANDIDATES) {
            Path p = Path.of(c);
            if (Files.isRegularFile(p)) return p.toAbsolutePath().normalize();
        }
        throw new IllegalStateException("llm-error-signatures.json not found; set " + ENV_PATH
                + " or mount /app/shared/docs/policies/llm-error-signatures.json");
    }

    public List<String> signatures() { return signatures; }
    public List<Pattern> promptLeakPatterns() { return promptLeakPatterns; }
    public double koreanRatioMin() { return koreanRatioMin; }
    public int koreanCheckMinChars() { return koreanCheckMinChars; }

    /** {@code lower}는 호출자가 소문자로 넘긴다. */
    public boolean containsSignature(String lower) {
        for (String s : signatures) if (lower.contains(s)) return true;
        return false;
    }

    /** 한국어 AI 콘텐츠에 한글이 사실상 없으면(비율 < koreanRatioMin) 영어 거절·오류로 판정. */
    public boolean hasInsufficientKorean(String text) {
        long significant = text.chars().filter(c -> c > 32).count();
        if (significant < koreanCheckMinChars) return false;
        long korean = text.chars().filter(c ->
                (c >= 0xAC00 && c <= 0xD7A3) || (c >= 0x1100 && c <= 0x11FF) || (c >= 0x3130 && c <= 0x318F)).count();
        return (double) korean / significant < koreanRatioMin;
    }

    public boolean hasPromptLeak(String text) {
        for (Pattern p : promptLeakPatterns) if (p.matcher(text).find()) return true;
        return false;
    }
}
