package com.againspring.aiuser.orchestrator.service.capsule;

import com.againspring.aiuser.orchestrator.domain.Persona;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Pure text/hash builder for WP2 semantic capsules (no DB / embed).
 * Up to 3 drafts: INTEREST, EXPERIENCE, VALUE.
 */
public final class PersonaCapsuleTextBuilder {

    public static final String TYPE_INTEREST = "INTEREST";
    public static final String TYPE_EXPERIENCE = "EXPERIENCE";
    public static final String TYPE_VALUE = "VALUE";

    public static final String ORIGIN_LEGACY = "LEGACY_IMPORTED";
    public static final String ORIGIN_INFERRED = "INFERRED";

    private static final Map<String, String> INTEREST_KO = Map.of(
            "COUPLE", "연인 갈등",
            "MARRIED", "부부 갈등",
            "FAMILY", "가족 갈등",
            "FRIEND", "친구 갈등",
            "WORK", "직장 갈등",
            "OTHER", "기타 인간관계 갈등"
    );

    private PersonaCapsuleTextBuilder() {}

    public record CapsuleDraft(
            String capsuleType,
            String topicKey,
            String text,
            String contentHash,
            String origin,
            BigDecimal confidence,
            String evidenceRef
    ) {}

    public record FactDraft(
            String factKey,
            String factValue,
            String origin,
            BigDecimal confidence,
            String evidenceRef
    ) {}

    /** Build ≤3 capsule drafts from voice_profile + interests (+ bias/general_style). */
    public static List<CapsuleDraft> buildCapsules(Persona persona) {
        List<CapsuleDraft> out = new ArrayList<>(3);
        CapsuleDraft interest = buildInterest(persona);
        if (interest != null) out.add(interest);
        CapsuleDraft experience = buildExperience(persona);
        if (experience != null) out.add(experience);
        CapsuleDraft value = buildValue(persona);
        if (value != null) out.add(value);
        return out;
    }

    /** Slim facts from voice_profile: age/gender/job/voice_type/formality. */
    public static List<FactDraft> buildFacts(Persona persona) {
        Map<String, Object> vp = persona.getVoiceProfile();
        if (vp == null || vp.isEmpty()) return List.of();
        List<FactDraft> facts = new ArrayList<>();
        addFact(facts, "age", vp.get("age"), "0.900");
        addFact(facts, "gender", vp.get("gender"), "0.900");
        addFact(facts, "job", vp.get("job"), "0.850");
        addFact(facts, "voice_type", vp.get("voice_type"), "0.900");
        addFact(facts, "formality", vp.get("formality"), "0.850");
        Object region = vp.get("region");
        if (region != null && !region.toString().isBlank()) {
            addFact(facts, "region", region, "0.800");
        }
        return facts;
    }

    public static String contentHash(String capsuleType, String topicKey, String text) {
        String payload = capsuleType + "|" + topicKey + "|" + Objects.toString(text, "");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static CapsuleDraft buildInterest(Persona persona) {
        Map<String, Double> interests = persona.getInterests();
        if (interests == null || interests.isEmpty()) return null;

        List<Map.Entry<String, Double>> ranked = interests.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(3)
                .toList();
        if (ranked.isEmpty()) return null;

        String topicKey = ranked.get(0).getKey();
        String joined = ranked.stream()
                .map(e -> INTEREST_KO.getOrDefault(e.getKey(), e.getKey())
                        + "(관심 " + String.format(Locale.ROOT, "%.2f", e.getValue()) + ")")
                .collect(Collectors.joining(", "));
        String text = "관심 갈등 주제: " + joined;
        return draft(TYPE_INTEREST, topicKey, text, ORIGIN_LEGACY, "0.850", "personas.interests");
    }

    static CapsuleDraft buildExperience(Persona persona) {
        Map<String, Object> vp = persona.getVoiceProfile() != null
                ? persona.getVoiceProfile() : Map.of();
        List<String> parts = new ArrayList<>();

        String ageKr = ageToKorean(str(vp.get("age")));
        if (ageKr != null) parts.add(ageKr);

        String genderKr = genderToKorean(str(vp.get("gender")));
        if (genderKr != null) parts.add(genderKr);

        String job = str(vp.get("job"));
        if (job != null) parts.add(job);

        String region = str(vp.get("region"));
        if (region != null) parts.add(region + " 거주");

        if (parts.isEmpty()) return null;

        StringBuilder text = new StringBuilder(String.join(", ", parts) + " 맥락의 생활 경험");
        List<String> avoid = cannotClaimHeuristics(vp, persona.getInterests());
        if (!avoid.isEmpty()) {
            text.append(". 경험 경계(말하지 않음): ").append(String.join(", ", avoid));
        }

        boolean inferredBoundary = !avoid.isEmpty();
        String origin = inferredBoundary ? ORIGIN_INFERRED : ORIGIN_LEGACY;
        String conf = inferredBoundary ? "0.700" : "0.850";
        return draft(TYPE_EXPERIENCE, "demographics", text.toString(), origin, conf, "voice_profile");
    }

    static CapsuleDraft buildValue(Persona persona) {
        Map<String, Object> vp = persona.getVoiceProfile() != null
                ? persona.getVoiceProfile() : Map.of();
        String style = str(vp.get("general_style"));
        Map<String, Double> bias = persona.getBiasProfile();

        List<String> axes = new ArrayList<>();
        if (style != null) {
            axes.addAll(extractValueAxes(style));
        }
        if (axes.isEmpty() && bias != null && !bias.isEmpty()) {
            bias.entrySet().stream()
                    .filter(e -> e.getValue() != null)
                    .sorted(Comparator.comparingDouble((Map.Entry<String, Double> e) -> Math.abs(e.getValue())).reversed())
                    .limit(2)
                    .forEach(e -> {
                        String cat = INTEREST_KO.getOrDefault(e.getKey(), e.getKey());
                        String lean = e.getValue() >= 0 ? "작성자 공감 성향" : "상대방 공감 성향";
                        axes.add(cat + "에서 " + lean);
                    });
        }

        String topicKey;
        String text;
        String origin;
        String conf;
        if (style != null && !axes.isEmpty()) {
            topicKey = "conflict_values";
            text = "갈등 가치축: " + String.join(", ", axes) + ". 스타일: " + clip(style, 180);
            origin = ORIGIN_LEGACY;
            conf = "0.750";
        } else if (style != null) {
            topicKey = "general_style";
            text = "말투·관점: " + clip(style, 220);
            origin = ORIGIN_LEGACY;
            conf = "0.700";
        } else if (!axes.isEmpty()) {
            topicKey = "bias_values";
            text = "갈등 가치축(추론): " + String.join(", ", axes);
            origin = ORIGIN_INFERRED;
            conf = "0.600";
        } else {
            return null;
        }
        return draft(TYPE_VALUE, topicKey, text, origin, conf, "voice_profile.general_style|bias_profile");
    }

    /**
     * Heuristic cannot-claim (AVOID folded into EXPERIENCE text).
     * 미혼·학생·낮은 MARRIED 관심 → 결혼·육아 경험 사칭 금지.
     */
    static List<String> cannotClaimHeuristics(Map<String, Object> vp, Map<String, Double> interests) {
        List<String> avoid = new ArrayList<>();
        String age = str(vp.get("age"));
        String job = str(vp.get("job"));
        double marriedInterest = interests != null && interests.get("MARRIED") != null
                ? interests.get("MARRIED") : 0.0;

        boolean likelyUnmarried =
                "학생".equals(job)
                || "10s".equals(age)
                || "20s_early".equals(age)
                || "20s_late".equals(age)
                || marriedInterest < 0.35;

        if (likelyUnmarried && !"주부".equals(job)) {
            avoid.add("결혼·육아 경험을 자신의 경험처럼 말하지 않음");
        }
        if ("학생".equals(job)) {
            avoid.add("장기 직장 경력·관리자 경험을 자신의 경험처럼 말하지 않음");
        }
        return avoid;
    }

    static List<String> extractValueAxes(String style) {
        Map<String, String> needles = new LinkedHashMap<>();
        needles.put("공정", "공정성");
        needles.put("公平", "공정성");
        needles.put("가족", "가족관");
        needles.put("경계", "개인경계");
        needles.put("책임", "책임");
        needles.put("실용", "실용성");
        needles.put("체면", "체면");
        needles.put("안정", "안정성");
        needles.put("투명", "투명성");
        needles.put("자율", "자율성");
        needles.put("신뢰", "신뢰");
        List<String> hit = new ArrayList<>();
        for (var e : needles.entrySet()) {
            if (style.contains(e.getKey()) && !hit.contains(e.getValue())) {
                hit.add(e.getValue());
            }
        }
        return hit;
    }

    private static CapsuleDraft draft(String type, String topicKey, String text,
                                      String origin, String confidence, String evidence) {
        return new CapsuleDraft(
                type, topicKey, text, contentHash(type, topicKey, text),
                origin, new BigDecimal(confidence), evidence);
    }

    private static void addFact(List<FactDraft> facts, String key, Object raw, String conf) {
        if (raw == null) return;
        String v = raw.toString().trim();
        if (v.isEmpty()) return;
        facts.add(new FactDraft(key, v, ORIGIN_LEGACY, new BigDecimal(conf), "voice_profile." + key));
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String clip(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }

    static String ageToKorean(String age) {
        if (age == null) return null;
        return switch (age) {
            case "10s" -> "10대";
            case "20s_early" -> "20대 초반";
            case "20s_late" -> "20대 후반";
            case "30s_early" -> "30대 초반";
            case "30s_late" -> "30대 후반";
            case "30s" -> "30대";
            case "40s" -> "40대";
            case "50s" -> "50대";
            case "60s" -> "60대";
            default -> age;
        };
    }

    static String genderToKorean(String gender) {
        if (gender == null) return null;
        if ("M".equalsIgnoreCase(gender)) return "남성";
        if ("F".equalsIgnoreCase(gender)) return "여성";
        return gender;
    }
}
