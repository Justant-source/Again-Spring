package com.againspring.aiuser.orchestrator.persona;

import com.againspring.aiuser.orchestrator.domain.Persona;

import java.util.List;
import java.util.Map;

/**
 * 계약 4 (.request/persona-diversity-v4/00-shared.md) — {@code Persona} → 400자 이내 한 덩어리 텍스트.
 * AI_POST·PAIRED·HUMAN_POST·human-reply 전부 이 카드를 쓰고 {@code voiceProfile} 전체 JSON은
 * 더 이상 보내지 않는다. 순수 함수(부작용 없음, 정렬 무작위성 없음 — 같은 입력엔 같은 출력).
 *
 * <p>닉네임은 {@code users.nickname}에 있고 Persona 엔티티엔 없다(기존 관례:
 * {@code PlanPersonaMapper.loadNicknames} 참고). 계약 시그니처는 {@link #render(Persona)}
 * 한 인자이므로 그대로 유지하되, 호출자가 이미 nickname을 조회했다면 {@link #render(Persona, String)}
 * 오버로드로 정확한 닉네임을 넘길 수 있다. 한 인자 버전은 voiceProfile.nickname(있으면) 또는
 * persona id로 대체한다.
 */
public final class PersonaCard {

    private static final int MAX_LEN = 400;

    private PersonaCard() {
    }

    public static String render(Persona p) {
        return render(p, fallbackNickname(p));
    }

    public static String render(Persona p, String nickname) {
        if (p == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("[페르소나] ").append(line1(p, nickname));
        sb.append('\n').append("[말투] ").append(line2(p));

        String habits = line3(p);
        if (!habits.isBlank()) sb.append('\n').append("[버릇] ").append(habits);

        String interests = line4(p);
        if (!interests.isBlank()) sb.append('\n').append("[관심] ").append(interests);

        String mines = line5(p);
        if (!mines.isBlank()) sb.append('\n').append("[지뢰] ").append(mines);

        String card = sb.toString().stripTrailing();
        return card.length() > MAX_LEN ? card.substring(0, MAX_LEN) : card;
    }

    // ── [페르소나] ────────────────────────────────────────────────────────

    private static String line1(Persona p, String nickname) {
        List<String> parts = new java.util.ArrayList<>();
        parts.add("닉네임=" + (nickname == null || nickname.isBlank() ? p.getId() : nickname));
        parts.add(p.getAgeYears() + "세 " + genderKr(p.getGender()));
        parts.add(maritalKr(p));
        parts.add(jobKr(p));
        String region = stringField(p, "region");
        if (!region.isBlank()) parts.add(region);
        return String.join(" · ", parts);
    }

    private static String genderKr(String gender) {
        return "M".equalsIgnoreCase(gender) ? "남" : "여";
    }

    private static String maritalKr(Persona p) {
        String marital = p.getMarital() == null ? "SINGLE" : p.getMarital();
        return switch (marital) {
            case "MARRIED" -> {
                String years = p.getMarriedYears() != null ? (p.getMarriedYears() + "년차") : "연차미상";
                yield "기혼 " + years + (p.isHasKids() ? ", 아이 있음" : ", 무자녀");
            }
            case "ENGAGED" -> "약혼";
            case "DATING" -> "연애중";
            default -> "미혼";
        };
    }

    private static String jobKr(Persona p) {
        if (p.getJobTitle() != null && !p.getJobTitle().isBlank()) return p.getJobTitle();
        return switch (p.getJobType() == null ? "CORP_LARGE" : p.getJobType()) {
            case "CORP_LARGE" -> "대기업 직장인";
            case "CORP_MID" -> "중견기업 직장인";
            case "STARTUP" -> "스타트업 직장인";
            case "PUBLIC" -> "공무원";
            case "PROFESSIONAL" -> "전문직";
            case "SELF_EMPLOYED" -> "자영업자";
            case "FREELANCER" -> "프리랜서";
            case "JOBSEEKER" -> "구직자";
            case "PARENT_LEAVE" -> "육아휴직자";
            default -> "직장인";
        };
    }

    // ── [말투] ────────────────────────────────────────────────────────────

    private static String line2(Persona p) {
        Map<String, String> axes = p.getStyleAxes();
        if (axes == null || axes.isEmpty()) return "정보 없음";

        List<String> cluster = new java.util.ArrayList<>();
        addAxis(cluster, axes, "directness", Map.of("BLUNT", "직설", "SOFT", "완곡"));
        addAxis(cluster, axes, "affect", Map.of("EMOTIONAL", "감정", "ANALYTIC", "분석"));
        addAxis(cluster, axes, "humor", Map.of("JOKER", "드립", "SERIOUS", "진지"));
        addAxis(cluster, axes, "stance", Map.of("OFFENSIVE", "공격", "DEFENSIVE", "방어"));
        addAxis(cluster, axes, "length", Map.of("LONG", "장문", "SHORT", "단문"));

        List<String> parts = new java.util.ArrayList<>();
        if (!cluster.isEmpty()) parts.add(String.join("/", cluster));

        String speech = axisKr(axes, "speech", Map.of("BANMAL", "반말", "JONDAE", "존댓말", "MIXED", "혼용"));
        if (!speech.isBlank()) parts.add(speech);

        String emoticon = axisKr(axes, "emoticon", Map.of("NONE", "없음", "LOW", "낮음", "HIGH", "높음"));
        if (!emoticon.isBlank()) parts.add("ㅋㅋ " + emoticon);

        String spelling = axisKr(axes, "spelling", Map.of("CLEAN", "정확", "SLOPPY", "엉성"));
        if (!spelling.isBlank()) parts.add("맞춤법 " + spelling);

        String linebreak = axisKr(axes, "linebreak", Map.of("WALL", "통짜", "CHOPPED", "잘게"));
        if (!linebreak.isBlank()) parts.add("줄바꿈 " + linebreak);

        String profanity = axisKr(axes, "profanity", Map.of("NONE", "없음", "MILD", "약간", "HEAVY", "심함"));
        if (!profanity.isBlank()) parts.add("욕설 " + profanity);

        return parts.isEmpty() ? "정보 없음" : String.join(" · ", parts);
    }

    private static void addAxis(List<String> out, Map<String, String> axes, String key, Map<String, String> dict) {
        String kr = axisKr(axes, key, dict);
        if (!kr.isBlank()) out.add(kr);
    }

    private static String axisKr(Map<String, String> axes, String key, Map<String, String> dict) {
        String raw = axes.get(key);
        if (raw == null || raw.isBlank()) return "";
        return dict.getOrDefault(raw.toUpperCase(java.util.Locale.ROOT), "");
    }

    // ── [버릇] ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static String line3(Persona p) {
        Map<String, Object> vp = p.getVoiceProfile();
        if (vp == null) return "";
        Object lexiconObj = vp.get("lexicon");
        if (!(lexiconObj instanceof Map)) return "";
        Map<String, Object> lexicon = (Map<String, Object>) lexiconObj;

        List<String> segments = new java.util.ArrayList<>();
        Object phrasesObj = lexicon.get("signature_phrases");
        if (phrasesObj instanceof List<?> list && !list.isEmpty()) {
            String joined = list.stream().limit(3)
                    .map(o -> "\"" + o + "\"")
                    .collect(java.util.stream.Collectors.joining(", "));
            segments.add("시그니처: " + joined);
        }
        Object habit = lexicon.get("typing_habit");
        if (habit != null && !String.valueOf(habit).isBlank()) {
            segments.add("습관: " + habit);
        }
        return String.join(" / ", segments);
    }

    // ── [관심] ────────────────────────────────────────────────────────────

    private static String line4(Persona p) {
        Map<String, Double> interests = p.getInterests();
        if (interests == null || interests.isEmpty()) return "";
        return interests.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(3)
                .map(e -> categoryKr(e.getKey()) + " " + String.format(java.util.Locale.ROOT, "%.1f", e.getValue()))
                .collect(java.util.stream.Collectors.joining(" · "));
    }

    private static String categoryKr(String category) {
        if (category == null) return "";
        return switch (category.toUpperCase(java.util.Locale.ROOT)) {
            case "WORK" -> "직장";
            case "COUPLE" -> "연애";
            case "MARRIED" -> "결혼생활";
            case "FRIEND" -> "친구";
            case "FAMILY" -> "가족";
            default -> "기타";
        };
    }

    // ── [지뢰] ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static String line5(Persona p) {
        Map<String, Object> vp = p.getVoiceProfile();
        if (vp == null) return "";
        Object hotButtonsObj = vp.get("hot_buttons");
        if (!(hotButtonsObj instanceof Map)) return "";
        Object triggersObj = ((Map<String, Object>) hotButtonsObj).get("triggers");
        if (!(triggersObj instanceof List<?> list) || list.isEmpty()) return "";
        return list.stream().limit(3).map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static String stringField(Persona p, String key) {
        Map<String, Object> vp = p.getVoiceProfile();
        if (vp == null) return "";
        Object v = vp.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String fallbackNickname(Persona p) {
        String vpNickname = stringField(p, "nickname");
        return vpNickname.isBlank() ? p.getId() : vpNickname;
    }
}
