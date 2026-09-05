package com.againspring.aiuser.orchestrator.persona;

import com.againspring.aiuser.orchestrator.domain.Persona;

import java.util.List;
import java.util.Map;

/**
 * 계약 4 (.request/persona-diversity-v4/00-shared.md) — {@code Persona} → 한 덩어리 텍스트.
 * AI_POST·PAIRED·HUMAN_POST·human-reply 전부 이 카드를 쓰고 {@code voiceProfile} 전체 JSON은
 * 더 이상 보내지 않는다. 순수 함수(부작용 없음, 정렬 무작위성 없음 — 같은 입력엔 같은 출력).
 *
 * <p>닉네임은 {@code users.nickname}에 있고 Persona 엔티티엔 없다(기존 관례:
 * {@code PlanPersonaMapper.loadNicknames} 참고). 계약 시그니처는 {@link #render(Persona)}
 * 한 인자이므로 그대로 유지하되, 호출자가 이미 nickname을 조회했다면 {@link #render(Persona, String)}
 * 오버로드로 정확한 닉네임을 넘길 수 있다. 한 인자 버전은 voiceProfile.nickname(있으면) 또는
 * persona id로 대체한다.
 *
 * <p><b>2026-09 순응도 개정</b> — dev 실측에서 {@code style_axes} 5/10개가 실제 출력에 반영되지
 * 않는 걸 확인했다(예: profanity=HEAVY인데 욕설 0건). 원인은 배선이 아니라 표현 방식: 종전엔
 * "직설/분석/진지 · 반말 · ㅋㅋ 낮음" 같은 압축 라벨이었고, LLM이 이를 배경 정보로 흘려 읽었다.
 * {@link #line2(Persona)}를 라벨에서 축별 명령문(예: "profanity=HEAVY: 욕설·비속어를 실제로
 * 섞어 쓴다")으로 바꾸고, 계약 4의 400자 상한을 늘렸다(§MAX_LEN 주석 참고 — 실측 카드 길이가
 * 늘어난 만큼 늘림, 실측·토큰 영향은 이 트랙 보고서 참고). 축=값 태그를 문장 안에 그대로 남긴
 * 이유: {@code StructuredGenerationService}의 결정론적 self-critique가 같은 문자열에서
 * 정규식으로 의도 값을 파싱해 출력과 대조할 수 있어야 하기 때문이다(라벨 문구가 바뀌어도
 * axis=VALUE 토큰은 안정적).
 */
public final class PersonaCard {

    /**
     * 계약 4 원안은 400자였으나, 라벨을 축별 명령문으로 펼치면서 실측 카드 길이가 늘었다
     * (10개 축 전부 채워진 경우 약 750~850자 관측 — PersonaCardTest 참고). 라벨 압축을 되돌리면
     * 순응도 문제가 재발하므로, 잘림으로 뒷부분 축(특히 [지뢰])이 날아가지 않도록 여유를 두고
     * 1100자로 올린다. 토큰 영향(대략): 400자 카드 ≈ 250~300 토큰 → 1100자 카드 ≈ 650~750 토큰,
     * 150명 캐스트 전원을 한 PLAN 요청에 실으면 +5~7만 토큰/요청 수준 증가 가능 — 상세는 보고서.
     */
    private static final int MAX_LEN = 1100;

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

    /** {@code style_axes}가 렌더링되는 순서 — 결정론 보장(순수 함수 계약) + 파서 안정성. */
    static final List<String> AXIS_ORDER = List.of(
            "directness", "affect", "humor", "stance", "length",
            "speech", "emoticon", "spelling", "linebreak", "profanity");

    /**
     * 2026-09 개정 — 축 값을 "라벨"이 아니라 "명령문"으로 렌더링한다.
     * 각 줄은 {@code key=VALUE: 지시문} 형태로 시작해 {@code StructuredGenerationService}가
     * 같은 문자열에서 정규식으로 의도 값을 되짚을 수 있게 한다(자기검증용, PersonaCard 자체는
     * 파싱하지 않음 — 순수 렌더러 책임 분리).
     */
    private static String line2(Persona p) {
        Map<String, String> axes = p.getStyleAxes();
        if (axes == null || axes.isEmpty()) return "정보 없음";

        List<String> bullets = new java.util.ArrayList<>();
        for (String key : AXIS_ORDER) {
            String directive = axisDirective(key, axes.get(key));
            if (directive != null) bullets.add("- " + directive);
        }
        if (bullets.isEmpty()) return "정보 없음";
        return "아래 문체 지시는 라벨이 아니라 명령이다 — 전부 실제 문장에 반영할 것:\n"
                + String.join("\n", bullets);
    }

    /**
     * {@code key=VALUE: 한국어 지시문} 한 줄, 인식 불가 값이면 null(해당 축 생략).
     * 지시문은 "무엇을 하라"는 구체 행동으로 쓴다 — 형용사 라벨 금지.
     */
    private static String axisDirective(String key, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return null;
        String v = rawValue.toUpperCase(java.util.Locale.ROOT);
        String directive = switch (key) {
            case "directness" -> switch (v) {
                case "BLUNT" -> "돌려 말하지 않고 하고 싶은 말을 바로 한다";
                case "SOFT" -> "직접 말하지 않고 에둘러 표현한다";
                default -> null;
            };
            case "affect" -> switch (v) {
                case "EMOTIONAL" -> "감정을 억누르지 않고 그대로 터뜨리듯 쓴다";
                case "ANALYTIC" -> "감정보다 상황을 분석하듯 담담하게 설명한다";
                default -> null;
            };
            case "humor" -> switch (v) {
                case "JOKER" -> "자조나 드립을 최소 1번은 실제로 넣는다";
                case "SERIOUS" -> "농담 없이 시종일관 진지하게 쓴다";
                default -> null;
            };
            case "stance" -> switch (v) {
                case "OFFENSIVE" -> "상대 잘못을 직접 지적하며 몰아붙인다";
                case "DEFENSIVE" -> "내 잘못일 가능성을 먼저 방어적으로 깔고 말한다(예: \"내가 예민한 걸 수도 있는데\")";
                default -> null;
            };
            case "length" -> switch (v) {
                case "LONG" -> "문장을 길게 늘여 쓴다";
                case "SHORT" -> "문장을 짧게 끊어 쓴다";
                default -> null;
            };
            case "speech" -> switch (v) {
                case "BANMAL" -> "반말만 쓴다 — ~요/~습니다 종결 절대 금지";
                case "JONDAE" -> "존댓말만 쓴다 — 반말 종결 절대 금지";
                case "MIXED" -> "반말과 존댓말을 문장마다 섞어 쓴다";
                default -> null;
            };
            case "emoticon" -> switch (v) {
                case "NONE" -> "ㅋㅋ·ㅠㅠ 등 이모티콘을 전혀 쓰지 않는다";
                case "LOW" -> "이모티콘은 글 전체에서 한두 번만 쓴다";
                case "HIGH" -> "문단마다 ㅋㅋ·ㅠㅠ 같은 표현을 실제로 넣는다";
                default -> null;
            };
            case "spelling" -> switch (v) {
                case "CLEAN" -> "맞춤법·띄어쓰기를 정확히 지킨다";
                case "SLOPPY" -> "오탈자·축약을 자연스럽게 섞는다(예: ㄱㅊ, 어케, 담에)";
                default -> null;
            };
            case "linebreak" -> switch (v) {
                case "WALL" -> "줄바꿈 없이 한 문단으로 몰아 쓴다";
                case "CHOPPED" -> "한두 문장마다 줄을 바꾼다";
                default -> null;
            };
            case "profanity" -> switch (v) {
                case "NONE" -> "욕설을 전혀 쓰지 않는다";
                case "MILD" -> "욕설을 아주 가끔, 약하게만 섞는다";
                case "HEAVY" -> "욕설·비속어를 실제로 섞어 쓴다 — 순화하지 않는다";
                default -> null;
            };
            default -> null;
        };
        return directive == null ? null : key + "=" + v + ": " + directive;
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
