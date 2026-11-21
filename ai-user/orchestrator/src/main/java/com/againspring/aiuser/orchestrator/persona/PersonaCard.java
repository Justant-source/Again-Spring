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
 *
 * <p><b>2026-11 개정(명령→배경)</b> — prod 실측: 명령형 문체 지시 + 예시 인용구 때문에
 * DEFENSIVE 글 71%가 예시 문구를 첫 문장에 복사, 시그니처 문구가 28% 글에 토씨 그대로 등장했다.
 * 이제 머리말은 "평소 말버릇(배경)", 축 서술은 성향, 예시 인용구는 어떤 축에도 없고, 시그니처는
 * 최대 2개 "가끔 쓰는 표현"이며, 모순 조합은 렌더 단계에서 낮춘다({@link #effectiveAxes}).
 * 표현 denylist는 두지 않는다(절대 규칙 #3).
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
        return render(p, nickname, 0);
    }

    /** 닉네임 폴백 + 시그니처 회전 seed. 생성 요청 매퍼가 날짜 seed로 호출해 같은 문구 반복을 줄인다. */
    public static String render(Persona p, int seed) {
        return render(p, fallbackNickname(p), seed);
    }

    /**
     * 시그니처 문구 선택을 호출마다 달리하고 싶은 호출자용 오버로드. {@code seed}가 같으면 출력도
     * 같다(순수 함수 유지). seed=0이면 앞 2개 고정. PlanPersonaMapper는 날짜(epochDay) seed로 호출한다.
     */
    public static String render(Persona p, String nickname, int seed) {
        if (p == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("[페르소나] ").append(line1(p, nickname));
        sb.append('\n').append("[말투] ").append(line2(p));

        String habits = line3(p, seed);
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
        return String.join(" · ", parts)
                + " (직업·나이·지역은 배경 사실이다. 사건과 관계 없으면 글에서 언급하지 않고, 직업을 소재로 농담하지 않는다)";
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
        Map<String, String> axes = effectiveAxes(p.getStyleAxes());
        if (axes.isEmpty()) return "정보 없음";

        List<String> bullets = new java.util.ArrayList<>();
        for (String key : AXIS_ORDER) {
            String directive = axisDirective(key, axes.get(key));
            if (directive != null) bullets.add("- " + directive);
        }
        if (bullets.isEmpty()) return "정보 없음";
        return "평소 말버릇이다. 자연스러운 자리에서만 드러나고, 전부 넣으려 하지 않는다:\n"
                + String.join("\n", bullets);
    }

    /**
     * 렌더 단계 전용 조합 정리(DB 값은 그대로). 없는 화자를 만드는 모순 조합을 한 단계 낮춘다.
     * 낮춘 값은 태그의 VALUE에도 그대로 반영된다(llm 자기검증과 어긋나지 않게).
     * <ul>
     *   <li>speech=JONDAE + profanity=HEAVY → profanity=MILD</li>
     *   <li>directness=SOFT + profanity=HEAVY → profanity=MILD (에둘러 말하는 화자의 폭언)</li>
     *   <li>emoticon=HIGH + humor=SERIOUS + affect=ANALYTIC → emoticon=LOW</li>
     * </ul>
     */
    static Map<String, String> effectiveAxes(Map<String, String> raw) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        if (raw == null) return out;
        for (Map.Entry<String, String> e : raw.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            out.put(e.getKey(), e.getValue().trim().toUpperCase(java.util.Locale.ROOT));
        }
        if ("HEAVY".equals(out.get("profanity"))
                && ("JONDAE".equals(out.get("speech")) || "SOFT".equals(out.get("directness")))) {
            out.put("profanity", "MILD");
        }
        if ("HIGH".equals(out.get("emoticon"))
                && "SERIOUS".equals(out.get("humor")) && "ANALYTIC".equals(out.get("affect"))) {
            out.put("emoticon", "LOW");
        }
        return out;
    }

    /**
     * {@code key=VALUE: 한국어 서술} 한 줄, 인식 불가 값이면 null(해당 축 생략).
     * 할당량("최소 N번")이나 예시 인용구를 쓰지 않고 성향만 서술한다 — 예시는 모델이 그대로 복사한다.
     */
    private static String axisDirective(String key, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return null;
        String v = rawValue.toUpperCase(java.util.Locale.ROOT);
        String directive = switch (key) {
            case "directness" -> switch (v) {
                case "BLUNT" -> "돌려 말하지 않고 하고 싶은 말을 바로 하는 편";
                case "SOFT" -> "직접 말하기보다 에둘러 표현하는 편";
                default -> null;
            };
            case "affect" -> switch (v) {
                case "EMOTIONAL" -> "감정이 말에 그대로 묻어나는 편";
                case "ANALYTIC" -> "감정보다 상황을 따져 담담하게 설명하는 편";
                default -> null;
            };
            case "humor" -> switch (v) {
                case "JOKER" -> "분위기상 어울릴 때 자조 섞인 농담이 슬쩍 나오는 편";
                case "SERIOUS" -> "농담 없이 진지한 편";
                default -> null;
            };
            case "stance" -> switch (v) {
                case "OFFENSIVE" -> "자기가 옳다고 확신하고 상대 탓을 하는 편";
                case "DEFENSIVE" -> "확신이 없어 남들 생각을 묻는 편";
                default -> null;
            };
            case "length" -> switch (v) {
                case "LONG" -> "문장이 긴 편";
                case "SHORT" -> "문장이 짧게 끊기는 편";
                default -> null;
            };
            case "speech" -> switch (v) {
                case "BANMAL" -> "반말로 쓴다";
                case "JONDAE" -> "존댓말로 쓴다";
                case "MIXED" -> "반말과 존댓말이 섞이는 편";
                default -> null;
            };
            case "emoticon" -> switch (v) {
                case "NONE" -> "ㅋㅋ·ㅠㅠ 같은 표현을 쓰지 않는 편";
                case "LOW" -> "ㅋㅋ·ㅠㅠ 같은 표현은 드물게 쓰는 편";
                case "HIGH" -> "ㅋㅋ·ㅠㅠ 같은 표현을 자주 쓰는 편";
                default -> null;
            };
            case "spelling" -> switch (v) {
                case "CLEAN" -> "맞춤법·띄어쓰기를 지키는 편";
                case "SLOPPY" -> "맞춤법·띄어쓰기가 느슨하고 축약이 섞이는 편";
                default -> null;
            };
            case "linebreak" -> switch (v) {
                case "WALL" -> "줄바꿈이 적고 한 덩어리로 쓰는 편";
                case "CHOPPED" -> "줄을 자주 바꾸는 편";
                default -> null;
            };
            case "profanity" -> switch (v) {
                case "NONE" -> "욕설을 쓰지 않는 편";
                case "MILD" -> "욕설은 아주 가끔, 약하게만 나오는 편";
                case "HEAVY" -> "화가 날 때 거친 말이 튀어나오는 편";
                default -> null;
            };
            default -> null;
        };
        return directive == null ? null : key + "=" + v + ": " + directive;
    }

    // ── [버릇] ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static String line3(Persona p, int seed) {
        Map<String, Object> vp = p.getVoiceProfile();
        if (vp == null) return "";
        Object lexiconObj = vp.get("lexicon");
        if (!(lexiconObj instanceof Map)) return "";
        Map<String, Object> lexicon = (Map<String, Object>) lexiconObj;

        List<String> segments = new java.util.ArrayList<>();
        Object phrasesObj = lexicon.get("signature_phrases");
        if (phrasesObj instanceof List<?> list && !list.isEmpty()) {
            // 최대 2개. seed=0이면 앞 2개 고정, 아니면 seed로 시작 위치만 돌린다(순수 함수).
            int n = list.size();
            int start = n <= 2 ? 0 : Math.floorMod(seed, n);
            List<String> picked = new java.util.ArrayList<>();
            for (int k = 0; k < Math.min(2, n); k++) picked.add("\"" + list.get((start + k) % n) + "\"");
            segments.add("가끔 쓰는 표현 — 안 써도 되고, 쓰더라도 그대로 붙이지 않는다: "
                    + String.join(", ", picked));
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
