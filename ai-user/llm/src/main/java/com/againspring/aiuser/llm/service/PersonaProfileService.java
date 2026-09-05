package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.dto.PersonaProfileGenRequest;
import com.againspring.aiuser.llm.pool.LlmWorkerPool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * WP1 — {@code POST /generate/persona-profile} 전용 생성 서비스 (01-wp1-persona-data.md §4).
 * {@link com.againspring.aiuser.llm.controller.GenerationController}의 기존 {@code /generate/persona}
 * (strengthener 전용)와는 별개 경로다. 새 서비스 클래스로 분리해 기존 경로에 영향을 주지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonaProfileService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TEMPLATE_PATH = "voice/persona_profile.md";

    private final LlmWorkerPool pool;

    /** 글 전용 모델(Sonnet) — 페르소나 프로필도 이 모델을 재사용한다(§4: "새 서비스는 claudePostModel을 쓴다"). */
    @Value("${llm.post-model:claude-sonnet-5}")
    private String claudePostModel;

    @Value("${llm.worker.default-timeout-ms:600000}")
    private long defaultTimeoutMs;

    private String template = "";

    @PostConstruct
    void loadTemplate() {
        try {
            template = new ClassPathResource(TEMPLATE_PATH).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("persona_profile.md template not found on classpath: {}", TEMPLATE_PATH, e);
            template = "";
        }
    }

    public Map<String, Object> generate(PersonaProfileGenRequest req, String correlationId) {
        if (template.isBlank()) {
            throw new StructuredGenerationException("persona_profile.md template missing");
        }
        String prompt = buildPrompt(req);
        long timeout = req.getTimeoutMs() != null && req.getTimeoutMs() > 0 ? req.getTimeoutMs() : defaultTimeoutMs;
        String raw = pool.executeSyncTask(prompt, claudePostModel, timeout, correlationId);
        Map<String, Object> parsed = parseJson(raw);
        validate(parsed);
        return parsed;
    }

    // ── 프롬프트 조립 ────────────────────────────────────────────────────

    private String buildPrompt(PersonaProfileGenRequest req) {
        String axesKorean = axesToKorean(req.getAxes());
        String usedPhrases = (req.getUsedPhrases() == null || req.getUsedPhrases().isEmpty())
                ? "(없음)"
                : String.join(", ", req.getUsedPhrases());
        return template
                .replace("{{AXES_KOREAN}}", axesKorean)
                .replace("{{USED_PHRASES}}", usedPhrases);
    }

    @SuppressWarnings("unchecked")
    private String axesToKorean(Map<String, Object> axes) {
        if (axes == null) return "정보 없음";
        int ageYears = asInt(axes.get("age_years"), 30);
        String gender = "M".equalsIgnoreCase(String.valueOf(axes.get("gender"))) ? "남" : "여";
        String marital = maritalKorean(axes);
        String jobType = jobTypeKorean(String.valueOf(axes.getOrDefault("job_type", "CORP_LARGE")));
        String region = axes.get("region") != null ? String.valueOf(axes.get("region")) : "";
        Object styleAxesRaw = axes.get("style_axes");
        String styleKorean = styleAxesRaw instanceof Map
                ? styleAxesToKorean((Map<String, Object>) styleAxesRaw) : "정보 없음";

        List<String> parts = new ArrayList<>();
        parts.add(ageYears + "세 " + gender);
        parts.add(marital);
        parts.add(jobType);
        if (!region.isBlank()) parts.add(region);
        parts.add("말투: " + styleKorean);
        return String.join(" / ", parts);
    }

    private String maritalKorean(Map<String, Object> axes) {
        String marital = String.valueOf(axes.getOrDefault("marital", "SINGLE"));
        return switch (marital) {
            case "MARRIED" -> {
                Object my = axes.get("married_years");
                String years = my != null ? (my + "년차") : "연차미상";
                boolean hasKids = Boolean.TRUE.equals(axes.get("has_kids"));
                yield "기혼 " + years + (hasKids ? " / 아이 있음" : " / 무자녀");
            }
            case "ENGAGED" -> "약혼";
            case "DATING" -> "연애중";
            default -> "미혼";
        };
    }

    private String jobTypeKorean(String jobType) {
        return switch (jobType) {
            case "CORP_LARGE" -> "대기업 직장인";
            case "CORP_MID" -> "중견기업 직장인";
            case "STARTUP" -> "스타트업 직장인";
            case "PUBLIC" -> "공무원";
            case "PROFESSIONAL" -> "전문직";
            case "SELF_EMPLOYED" -> "자영업자";
            case "FREELANCER" -> "프리랜서";
            case "JOBSEEKER" -> "구직중";
            case "PARENT_LEAVE" -> "육아휴직중";
            default -> "직장인";
        };
    }

    private String styleAxesToKorean(Map<String, Object> axes) {
        List<String> parts = new ArrayList<>();
        addAxis(parts, axes, "directness", Map.of("BLUNT", "직설", "SOFT", "완곡"));
        addAxis(parts, axes, "affect", Map.of("EMOTIONAL", "감정", "ANALYTIC", "분석"));
        addAxis(parts, axes, "humor", Map.of("JOKER", "드립", "SERIOUS", "진지"));
        addAxis(parts, axes, "stance", Map.of("OFFENSIVE", "공격", "DEFENSIVE", "방어"));
        addAxis(parts, axes, "length", Map.of("LONG", "장문", "SHORT", "단문"));
        addAxis(parts, axes, "speech", Map.of("BANMAL", "반말", "JONDAE", "존댓말", "MIXED", "혼용"));
        String emoticon = axisKr(axes, "emoticon", Map.of("NONE", "없음", "LOW", "낮음", "HIGH", "높음"));
        if (!emoticon.isBlank()) parts.add("ㅋㅋ" + emoticon);
        addAxis(parts, axes, "spelling", Map.of("CLEAN", "맞춤법정확", "SLOPPY", "맞춤법엉성"));
        addAxis(parts, axes, "linebreak", Map.of("WALL", "줄바꿈통짜", "CHOPPED", "줄바꿈잘게"));
        String profanity = axisKr(axes, "profanity", Map.of("NONE", "없음", "MILD", "약간", "HEAVY", "심함"));
        if (!profanity.isBlank()) parts.add("욕설" + profanity);
        return parts.isEmpty() ? "정보 없음" : String.join("·", parts);
    }

    private void addAxis(List<String> out, Map<String, Object> axes, String key, Map<String, String> dict) {
        String kr = axisKr(axes, key, dict);
        if (!kr.isBlank()) out.add(kr);
    }

    private String axisKr(Map<String, Object> axes, String key, Map<String, String> dict) {
        Object raw = axes.get(key);
        if (raw == null) return "";
        return dict.getOrDefault(String.valueOf(raw).toUpperCase(Locale.ROOT), "");
    }

    private int asInt(Object v, int fallback) {
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
    }

    // ── 파싱 + 검증 ──────────────────────────────────────────────────────

    private Map<String, Object> parseJson(String raw) {
        String text = raw != null ? raw.trim() : "";
        if (text.contains("```json")) {
            int s = text.indexOf("```json") + 7;
            int e = text.lastIndexOf("```");
            if (e > s) text = text.substring(s, e).trim();
        } else if (text.contains("```")) {
            int s = text.indexOf("```") + 3;
            int e = text.lastIndexOf("```");
            if (e > s) {
                String candidate = text.substring(s, e).trim();
                if (candidate.startsWith("{")) text = candidate;
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new StructuredGenerationException("persona-profile response is not JSON");
        }
        try {
            JsonNode node = JSON.readTree(text.substring(start, end + 1));
            return JSON.convertValue(node, Map.class);
        } catch (Exception e) {
            throw new StructuredGenerationException("persona-profile JSON parse failed: " + e.getMessage());
        }
    }

    private static final List<String> REQUIRED_KEYS = List.of(
            "job_title", "life_context", "general_style", "lexicon", "writing_quirks",
            "hot_buttons", "reactions", "example_post_openers", "example_comments", "example_replies",
            "post_style", "comment_style", "reply_style", "interests");

    @SuppressWarnings("unchecked")
    private void validate(Map<String, Object> resp) {
        for (String key : REQUIRED_KEYS) {
            if (!resp.containsKey(key) || resp.get(key) == null) {
                throw new StructuredGenerationException("persona-profile missing required key: " + key);
            }
        }
        Object lexiconObj = resp.get("lexicon");
        if (!(lexiconObj instanceof Map)) throw new StructuredGenerationException("lexicon must be an object");
        Map<String, Object> lexicon = (Map<String, Object>) lexiconObj;
        Object phrasesObj = lexicon.get("signature_phrases");
        if (!(phrasesObj instanceof List<?> phrases) || phrases.size() < 6) {
            throw new StructuredGenerationException("signature_phrases must have >= 6 items");
        }
        if (phrases.size() > 10) {
            lexicon.put("signature_phrases", phrases.subList(0, 10));
        }

        Object commentsObj = resp.get("example_comments");
        if (!(commentsObj instanceof List<?> comments) || comments.size() < 5) {
            throw new StructuredGenerationException("example_comments must have >= 5 items");
        }
        if (comments.size() > 5) {
            resp.put("example_comments", comments.subList(0, 5));
        }

        String allText = collectAllText(resp);
        LlmErrorSignatures sig = LlmErrorSignatures.get();
        String lower = allText.toLowerCase(Locale.ROOT);
        if (sig.containsSignature(lower)) {
            throw new StructuredGenerationException("persona-profile response matches error/refusal signature");
        }
        if (sig.hasPromptLeak(allText)) {
            throw new StructuredGenerationException("persona-profile response contains prompt-leak pattern");
        }
        if (sig.hasInsufficientKorean(allText)) {
            throw new StructuredGenerationException("persona-profile response has insufficient Korean ratio");
        }
    }

    /** 모든 문자열 값(중첩 포함)을 이어붙여 시그니처·언어 가드 검사용 텍스트를 만든다. */
    private String collectAllText(Object node) {
        StringBuilder sb = new StringBuilder();
        collectAllTextInto(node, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void collectAllTextInto(Object node, StringBuilder sb) {
        if (node instanceof String s) {
            sb.append(s).append('\n');
        } else if (node instanceof Map<?, ?> map) {
            for (Object v : map.values()) collectAllTextInto(v, sb);
        } else if (node instanceof Iterable<?> it) {
            for (Object v : it) collectAllTextInto(v, sb);
        }
    }
}
