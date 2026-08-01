package com.againspring.aiuser.orchestrator.service.storyprofile;

import com.againspring.aiuser.orchestrator.domain.StoryProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class StoryProfileAnalyzerTest {

    private StoryProfileAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new StoryProfileAnalyzer();
    }

    @Test
    void toSearchDocumentNeverExceeds512() {
        String longTopic = "가".repeat(80);
        List<String> topics = IntStream.range(0, 12)
                .mapToObj(i -> longTopic + i)
                .collect(Collectors.toList());
        List<String> life = List.of("맞벌이", "기혼", "육아 중", "서울 거주", "직장인");
        List<String> values = List.of("가족 부양", "부부 간 투명성", "공정성", "신뢰", "사전 합의");
        StoryProfile profile = new StoryProfile(
                "공동 재정에서 부모 생활비를 숨긴 문제 — 배신감과 불공정이 큼",
                "MARRIED",
                topics,
                Map.of("role", "아내", "age_band", "30s"),
                life,
                values,
                List.of(),
                List.of(),
                List.of("아내"),
                List.of(),
                "NATEPAN",
                List.of("금액보다 신뢰", "부양 현실", "사전 합의", "부부 소통", "재정 투명"),
                "상대 사정을 듣지 않음",
                "부모님 부양 부담이 있을 수 있음"
        );

        String doc = profile.toSearchDocument();
        assertTrue(doc.length() <= StoryProfile.SEARCH_DOC_MAX,
                "searchDoc length=" + doc.length() + " doc=" + doc);
        assertFalse(doc.isBlank());
        assertTrue(doc.startsWith("category:MARRIED") || doc.contains("category:MARRIED"), doc);
        assertTrue(doc.contains("register:NATEPAN"), doc);
    }

    @Test
    void toSearchDocumentPacksCoreFields() {
        StoryProfile profile = new StoryProfile(
                "부모 생활비 비밀",
                "MARRIED",
                List.of("부모 생활비", "공동재정", "비밀"),
                Map.of(),
                List.of("기혼", "맞벌이"),
                List.of("가족 부양", "부부 간 투명성"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "NATEPAN",
                List.of("금액보다 신뢰"),
                "",
                ""
        );
        String doc = profile.toSearchDocument();
        assertTrue(doc.contains("topics:부모 생활비"));
        assertTrue(doc.contains("life_context:기혼"));
        assertTrue(doc.contains("value_axis:가족 부양"));
        assertTrue(doc.length() <= 512);
    }

    @Test
    void normalizeCategoryAcceptsKnownAndFallsBack() {
        assertEquals("MARRIED", StoryProfileAnalyzer.normalizeCategory("married"));
        assertEquals("WORK", StoryProfileAnalyzer.normalizeCategory("WORK"));
        assertEquals("COUPLE", StoryProfileAnalyzer.normalizeCategory(" couple "));
        assertEquals("OTHER", StoryProfileAnalyzer.normalizeCategory(null));
        assertEquals("OTHER", StoryProfileAnalyzer.normalizeCategory(""));
        assertEquals("OTHER", StoryProfileAnalyzer.normalizeCategory("UNKNOWN_CAT"));
    }

    @Test
    void normalizeSourceRegisterMapsNatepanAndBlind() {
        assertEquals("NATEPAN", StoryProfileAnalyzer.normalizeSourceRegister("natepan"));
        assertEquals("NATEPAN", StoryProfileAnalyzer.normalizeSourceRegister("NATEPAN"));
        assertEquals("BLIND", StoryProfileAnalyzer.normalizeSourceRegister("blind"));
        assertEquals("BLIND", StoryProfileAnalyzer.normalizeSourceRegister("BLIND"));
        assertEquals("NATEPAN", StoryProfileAnalyzer.normalizeSourceRegister(null));
        assertEquals("NATEPAN", StoryProfileAnalyzer.normalizeSourceRegister("SELF_GENERATED"));
    }

    @Test
    void heuristicExtractsTopicsValuesAndRegister() {
        StoryProfile p = analyzer.analyze(
                "남편이 공동재정에서 부모 생활비를 숨겼어요",
                "맞벌이인데 투명하게 안 하고 비밀로 부양비를 빼갔어요. 배신감이 큽니다.",
                "married",
                "natepan",
                42L
        );

        assertEquals("MARRIED", p.category());
        assertEquals("NATEPAN", p.sourceRegister());
        assertTrue(p.topics().contains("부모 생활비") || p.topics().contains("공동재정"));
        assertTrue(p.topics().stream().anyMatch(t -> t.contains("비밀") || t.contains("공동")));
        assertFalse(p.valueAxis().isEmpty());
        assertTrue(p.lifeContext().contains("맞벌이"));
        assertTrue(p.toSearchDocument().length() <= 512);
        assertTrue(p.toSearchDocument().contains("emotional:배신감")
                || p.topics().stream().anyMatch(t -> t.contains("비밀")));
    }

    @Test
    void blindSourceNormalizesAndCachesByExampleId() {
        StoryProfile a = analyzer.analyze("야근 강요", "상사 때문에 야근이 일상", "work", "blind", 7L);
        StoryProfile b = analyzer.analyze("different title", "different body", "work", "BLIND", 7L);

        assertEquals("BLIND", a.sourceRegister());
        assertEquals("WORK", a.category());
        assertSame(a, b);
        assertEquals(1, analyzer.cacheSize());
    }

    @Test
    void cacheFallsBackToContentHashWhenNoExampleId() {
        StoryProfile a = analyzer.analyze("제목", "본문 내용", "FAMILY", "natepan", null);
        StoryProfile b = analyzer.analyze("제목", "본문 내용", "FAMILY", "natepan", null);
        StoryProfile c = analyzer.analyze("제목", "다른 본문", "FAMILY", "natepan", null);

        assertSame(a, b);
        assertNotSame(a, c);
    }
}
