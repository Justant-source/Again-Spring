package com.againspring.aiuser.orchestrator.service.storyprofile;

import com.againspring.aiuser.orchestrator.domain.StoryProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Heuristic StoryProfile builder (WP3 / W4-A) — works offline without LLM.
 *
 * <p>TODO(WP3 enrich): optional LLM structured enrich via llm-ai-user when a dedicated
 * StoryProfile JSON endpoint exists. DoD is satisfied by heuristic + schema; skip for now
 * ({@link com.againspring.aiuser.orchestrator.client.LlmAiUserClient#analyzePost} uses a
 * different PostAnalysis schema).
 */
@Slf4j
@Service
public class StoryProfileAnalyzer {

    public static final int CACHE_SOFT_MAX = 500;

    private static final Set<String> CATEGORIES = Set.of(
            "COUPLE", "MARRIED", "FRIEND", "FAMILY", "WORK", "OTHER");

    private static final Map<String, String> TOPIC_NEEDLES = topicNeedles();
    private static final Map<String, String> VALUE_NEEDLES = valueNeedles();
    private static final Map<String, String> LIFE_NEEDLES = lifeNeedles();
    private static final Map<String, String> IDENTITY_NEEDLES = identityNeedles();

    private final ConcurrentHashMap<String, StoryProfile> cache = new ConcurrentHashMap<>();

    /**
     * Analyze once per story; cache keyed by {@code sourceExampleId} or sha256(title|body).
     *
     * @param source crawl source (natepan/blind) → normalized to NATEPAN|BLIND
     * @param sourceExampleId optional example_bank id; used as cache key when present
     */
    public StoryProfile analyze(
            String title,
            String body,
            String category,
            String source,
            Long sourceExampleId) {
        String key = cacheKey(title, body, sourceExampleId);
        StoryProfile hit = cache.get(key);
        if (hit != null) return hit;

        StoryProfile built = buildHeuristic(title, body, category, source);
        putCache(key, built);
        return built;
    }

    /** Package-visible for tests — no cache. */
    StoryProfile buildHeuristic(String title, String body, String category, String source) {
        String t = nullToEmpty(title);
        String b = nullToEmpty(body);
        String hay = t + "\n" + b;

        String cat = normalizeCategory(category);
        String register = normalizeSourceRegister(source);
        List<String> topics = extractByNeedles(hay, TOPIC_NEEDLES, 8);
        List<String> valueAxis = extractByNeedles(hay, VALUE_NEEDLES, 6);
        List<String> lifeContext = extractByNeedles(hay, LIFE_NEEDLES, 6);
        Map<String, String> identity = extractIdentity(hay);
        String central = deriveCentralConflict(t, b, topics);
        List<String> affordances = deriveReplyAffordances(topics, valueAxis, cat);

        List<String> unknowns = new ArrayList<>();
        if (identity.isEmpty()) unknowns.add("explicit_identity");
        if (lifeContext.isEmpty()) unknowns.add("life_context");
        if (valueAxis.isEmpty()) unknowns.add("value_axis");

        return new StoryProfile(
                central,
                cat,
                topics,
                identity,
                lifeContext,
                valueAxis,
                List.of(),
                List.of(),
                List.copyOf(identity.values()),
                unknowns,
                register,
                affordances,
                "",
                ""
        );
    }

    public static String normalizeCategory(String raw) {
        if (raw == null || raw.isBlank()) return "OTHER";
        String u = raw.trim().toUpperCase(Locale.ROOT);
        return CATEGORIES.contains(u) ? u : "OTHER";
    }

    /**
     * Crawl source → register. Accepts natepan/NATEPAN/blind/BLIND (any case).
     * Unknown / blank / SELF_GENERATED → NATEPAN (default community; caller should filter SELF_GENERATED upstream).
     */
    public static String normalizeSourceRegister(String source) {
        if (source == null || source.isBlank()) return "NATEPAN";
        String u = source.trim().toUpperCase(Locale.ROOT);
        if ("BLIND".equals(u)) return "BLIND";
        if ("NATEPAN".equals(u)) return "NATEPAN";
        return "NATEPAN";
    }

    public void clearCache() {
        cache.clear();
    }

    int cacheSize() {
        return cache.size();
    }

    private void putCache(String key, StoryProfile profile) {
        if (cache.size() >= CACHE_SOFT_MAX) {
            cache.clear();
            log.debug("StoryProfile cache cleared at soft max {}", CACHE_SOFT_MAX);
        }
        cache.put(key, profile);
    }

    private static String cacheKey(String title, String body, Long sourceExampleId) {
        if (sourceExampleId != null) {
            return "ex:" + sourceExampleId;
        }
        return "h:" + sha256(nullToEmpty(title) + "|" + nullToEmpty(body));
    }

    private static String sha256(String payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String deriveCentralConflict(String title, String body, List<String> topics) {
        if (!title.isBlank()) {
            return clip(title.replaceAll("\\s+", " ").trim(), 120);
        }
        if (!topics.isEmpty()) {
            return String.join(" · ", topics.subList(0, Math.min(3, topics.size())));
        }
        if (!body.isBlank()) {
            String first = body.replaceAll("\\s+", " ").trim();
            int cut = first.indexOf('.');
            if (cut < 0) cut = first.indexOf('다');
            if (cut > 20 && cut < 100) {
                return first.substring(0, cut + 1).trim();
            }
            return clip(first, 120);
        }
        return "갈등 맥락 미상";
    }

    private static List<String> deriveReplyAffordances(
            List<String> topics, List<String> valueAxis, String category) {
        List<String> out = new ArrayList<>();
        for (String t : topics) {
            if (out.size() >= 5) break;
            out.add(t);
        }
        for (String v : valueAxis) {
            if (out.size() >= 5) break;
            if (!out.contains(v)) out.add(v);
        }
        if (out.isEmpty()) {
            out.add(switch (category) {
                case "MARRIED" -> "부부 소통";
                case "COUPLE" -> "연인 경계";
                case "FAMILY" -> "가족 역할";
                case "FRIEND" -> "친구 거리";
                case "WORK" -> "직장 현실";
                default -> "상황 공감";
            });
        }
        return List.copyOf(out);
    }

    private static Map<String, String> extractIdentity(String hay) {
        Map<String, String> out = new LinkedHashMap<>();
        for (var e : IDENTITY_NEEDLES.entrySet()) {
            if (hay.contains(e.getKey())) {
                String[] kv = e.getValue().split("=", 2);
                if (kv.length == 2 && !out.containsKey(kv[0])) {
                    out.put(kv[0], kv[1]);
                }
            }
        }
        return Map.copyOf(out);
    }

    private static List<String> extractByNeedles(String hay, Map<String, String> needles, int max) {
        List<String> hit = new ArrayList<>();
        for (var e : needles.entrySet()) {
            if (hit.size() >= max) break;
            if (hay.contains(e.getKey()) && !hit.contains(e.getValue())) {
                hit.add(e.getValue());
            }
        }
        return List.copyOf(hit);
    }

    private static String clip(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static Map<String, String> topicNeedles() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("생활비", "부모 생활비");
        m.put("용돈", "용돈");
        m.put("공동재정", "공동재정");
        m.put("공동 계좌", "공동재정");
        m.put("월급", "급여·재정");
        m.put("빚", "부채");
        m.put("대출", "부채");
        m.put("비밀", "비밀");
        m.put("숨겼", "비밀");
        m.put("외도", "외도");
        m.put("바람", "외도");
        m.put("이혼", "이혼");
        m.put("결혼", "결혼");
        m.put("시댁", "시댁");
        m.put("처가", "처가");
        m.put("육아", "육아");
        m.put("아이", "자녀");
        m.put("시어머니", "시댁");
        m.put("장모", "처가");
        m.put("야근", "야근");
        m.put("상사", "직장 상사");
        m.put("동료", "직장 동료");
        m.put("이직", "이직");
        m.put("퇴사", "퇴사");
        m.put("연애", "연애");
        m.put("이별", "이별");
        m.put("연락", "연락·거리");
        m.put("무시", "무시");
        m.put("가스라이", "통제·압박");
        m.put("폭언", "폭언");
        m.put("폭력", "폭력");
        m.put("친구", "친구 관계");
        m.put("부모님", "부모");
        m.put("형제", "형제");
        m.put("자매", "형제");
        return Map.copyOf(m);
    }

    private static Map<String, String> valueNeedles() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("부양", "가족 부양");
        m.put("투명", "부부 간 투명성");
        m.put("공정", "공정성");
        m.put("공평", "공정성");
        m.put("경계", "개인경계");
        m.put("책임", "책임");
        m.put("신뢰", "신뢰");
        m.put("존중", "존중");
        m.put("자율", "자율성");
        m.put("안정", "안정성");
        m.put("체면", "체면");
        m.put("실용", "실용성");
        m.put("합의", "사전 합의");
        m.put("솔직", "솔직함");
        m.put("배려", "배려");
        return Map.copyOf(m);
    }

    private static Map<String, String> lifeNeedles() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("맞벌이", "맞벌이");
        m.put("전업", "전업");
        m.put("기혼", "기혼");
        m.put("결혼한", "기혼");
        m.put("신혼", "신혼");
        m.put("미혼", "미혼");
        m.put("돌싱", "돌싱");
        m.put("육아", "육아 중");
        m.put("임신", "임신");
        m.put("자취", "자취");
        m.put("동거", "동거");
        m.put("본가", "본가");
        m.put("지방", "지방 거주");
        m.put("서울", "서울 거주");
        m.put("직장인", "직장인");
        m.put("프리랜서", "프리랜서");
        m.put("취업준비", "취업준비");
        m.put("학생", "학생");
        return Map.copyOf(m);
    }

    /** needle → "key=value" for explicit_identity map. */
    private static Map<String, String> identityNeedles() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("남편", "role=남편");
        m.put("아내", "role=아내");
        m.put("와이프", "role=아내");
        m.put("여친", "role=여친");
        m.put("남친", "role=남친");
        m.put("여자친구", "role=여친");
        m.put("남자친구", "role=남친");
        m.put("저는 여자", "gender=F");
        m.put("여자입니다", "gender=F");
        m.put("여잔데", "gender=F");
        m.put("저는 남자", "gender=M");
        m.put("남자입니다", "gender=M");
        m.put("남잔데", "gender=M");
        m.put("20대", "age_band=20s");
        m.put("30대", "age_band=30s");
        m.put("40대", "age_band=40s");
        m.put("50대", "age_band=50s");
        return Map.copyOf(m);
    }
}
