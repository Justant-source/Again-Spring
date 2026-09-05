package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.dto.PersonaProfileGenRequest;
import com.againspring.aiuser.llm.pool.LlmWorkerPool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** WP1 — 01-wp1-persona-data.md §4: /generate/persona-profile 응답 검증. */
class PersonaProfileServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static PersonaProfileService configuredService(LlmWorkerPool pool) {
        PersonaProfileService service = new PersonaProfileService(pool);
        ReflectionTestUtils.setField(service, "claudePostModel", "claude-sonnet-5");
        ReflectionTestUtils.setField(service, "defaultTimeoutMs", 60000L);
        service.loadTemplate();
        return service;
    }

    private static PersonaProfileGenRequest req() {
        Map<String, Object> axes = new LinkedHashMap<>();
        axes.put("age_years", 34);
        axes.put("gender", "M");
        axes.put("marital", "MARRIED");
        axes.put("married_years", 6);
        axes.put("has_kids", true);
        axes.put("job_type", "CORP_MID");
        axes.put("region", "경기");
        axes.put("tier", "HEAVY");
        axes.put("voice_type", "NATEPAN");
        axes.put("style_axes", Map.of("directness", "BLUNT", "speech", "BANMAL",
                "emoticon", "LOW", "spelling", "CLEAN", "linebreak", "CHOPPED", "profanity", "NONE"));
        return PersonaProfileGenRequest.builder()
                .personaId("p1").nickname("야근일상").axes(axes)
                .usedPhrases(List.of("이미 쓴 표현")).correlationId("test-corr").build();
    }

    private static Map<String, Object> validResponseMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("job_title", "중견 제조업 구매팀 대리");
        m.put("life_context", "결혼 6년차라 아이 어린이집 등하원 때문에 아침마다 정신없다 대출 이자도 슬슬 부담된다");
        m.put("general_style", "결론부터 던지고 나서 상황을 설명하는 편이다 감정보다는 사실관계를 먼저 정리한다");
        m.put("lexicon", Map.of(
                "signature_phrases", List.of("결론부터", "이건 좀", "아 근데", "그래서", "일단은", "요약하면"),
                "typing_habit", "문장 끝에 ㅇㅇ 붙임"));
        m.put("writing_quirks", Map.of("spelling_level", "정확", "consistent_errors", List.of(), "mobile_typos", false));
        m.put("hot_buttons", Map.of("triggers", List.of("회사 갑질", "육아 분담", "야근 강요"),
                "soft_spots", List.of("가족", "동료"), "upvote_when", "구체적 사건이 있을 때"));
        m.put("reactions", Map.of("agree", List.of("그렇지", "인정"), "disagree", List.of("그건 아니지"),
                "curious", List.of("그래서 어떻게 됐음")));
        m.put("example_post_openers", List.of("어제 팀장이 또 그랬다", "결혼하고 나니 이런 게 힘들다", "육아휴직 얘기 꺼냈다가"));
        m.put("example_comments", List.of("그건 진짜 아니다", "고생하셨네요", "저도 비슷한 일 있었어요",
                "회사가 원래 그럼", "빨리 이직하세요"));
        m.put("example_replies", List.of("그러게요", "저도 그랬어요", "화이팅입니다"));
        m.put("post_style", "결론부터 쓰고 사건 나열");
        m.put("comment_style", "짧고 단정적으로");
        m.put("reply_style", "한 줄 공감");
        m.put("interests", Map.of("WORK", 0.9, "COUPLE", 0.2, "MARRIED", 0.6, "FRIEND", 0.4, "FAMILY", 0.7));
        return m;
    }

    private static String toJson(Map<String, Object> m) throws Exception {
        return JSON.writeValueAsString(m);
    }

    @Test
    void generate_validResponse_returnsAllKeys() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        when(pool.executeSyncTask(anyString(), anyString(), anyLong(), anyString()))
                .thenReturn(toJson(validResponseMap()));
        PersonaProfileService service = configuredService(pool);

        Map<String, Object> result = service.generate(req(), "corr-1");

        assertTrue(result.containsKey("job_title"));
        assertTrue(result.containsKey("lexicon"));
        assertTrue(result.containsKey("example_comments"));
    }

    @Test
    void generate_promptPlacesVariableContentAfterPersonaSectionMarker() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        when(pool.executeSyncTask(anyString(), anyString(), anyLong(), anyString()))
                .thenAnswer(inv -> {
                    String prompt = inv.getArgument(0);
                    int markerIdx = prompt.indexOf("<<<PERSONA_SECTION>>>");
                    int axesIdx = prompt.indexOf("34세 남");
                    int usedPhrasesIdx = prompt.indexOf("이미 쓴 표현");
                    assertTrue(markerIdx >= 0, "marker must be present");
                    assertTrue(axesIdx > markerIdx, "axes must come after marker");
                    assertTrue(usedPhrasesIdx > markerIdx, "usedPhrases must come after marker");
                    return toJson(validResponseMap());
                });
        PersonaProfileService service = configuredService(pool);
        service.generate(req(), "corr-2");
    }

    @Test
    void generate_missingRequiredKey_throws() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        Map<String, Object> broken = new LinkedHashMap<>(validResponseMap());
        broken.remove("hot_buttons");
        when(pool.executeSyncTask(anyString(), anyString(), anyLong(), anyString())).thenReturn(toJson(broken));
        PersonaProfileService service = configuredService(pool);

        assertThrows(StructuredGenerationException.class, () -> service.generate(req(), "corr-3"));
    }

    @Test
    void generate_tooFewSignaturePhrases_throws() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        Map<String, Object> broken = new LinkedHashMap<>(validResponseMap());
        broken.put("lexicon", Map.of("signature_phrases", List.of("한개", "두개"), "typing_habit", "습관"));
        when(pool.executeSyncTask(anyString(), anyString(), anyLong(), anyString())).thenReturn(toJson(broken));
        PersonaProfileService service = configuredService(pool);

        assertThrows(StructuredGenerationException.class, () -> service.generate(req(), "corr-4"));
    }

    @Test
    void generate_tooFewExampleComments_throws() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        Map<String, Object> broken = new LinkedHashMap<>(validResponseMap());
        broken.put("example_comments", List.of("하나", "둘"));
        when(pool.executeSyncTask(anyString(), anyString(), anyLong(), anyString())).thenReturn(toJson(broken));
        PersonaProfileService service = configuredService(pool);

        assertThrows(StructuredGenerationException.class, () -> service.generate(req(), "corr-5"));
    }

    @Test
    void generate_errorSignatureDetected_throws() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        Map<String, Object> broken = new LinkedHashMap<>(validResponseMap());
        broken.put("general_style", "API error occurred while generating this profile 결론부터 던지는 편이다");
        when(pool.executeSyncTask(anyString(), anyString(), anyLong(), anyString())).thenReturn(toJson(broken));
        PersonaProfileService service = configuredService(pool);

        assertThrows(StructuredGenerationException.class, () -> service.generate(req(), "corr-6"));
    }

    @Test
    void generate_insufficientKorean_throws() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        Map<String, Object> broken = new LinkedHashMap<>();
        broken.put("job_title", "Purchasing team assistant manager");
        broken.put("life_context", "This person has been married for six years and commutes by car every day for work");
        broken.put("general_style", "This person speaks directly and prefers facts over feelings when writing online");
        broken.put("lexicon", Map.of("signature_phrases",
                List.of("in short", "well then", "by the way", "anyway", "so basically", "to sum up"),
                "typing_habit", "ends sentences abruptly"));
        broken.put("writing_quirks", Map.of("spelling_level", "clean", "consistent_errors", List.of(), "mobile_typos", false));
        broken.put("hot_buttons", Map.of("triggers", List.of("overtime", "unfair boss", "chores"),
                "soft_spots", List.of("family", "coworkers"), "upvote_when", "when there is a concrete incident"));
        broken.put("reactions", Map.of("agree", List.of("right", "agreed"), "disagree", List.of("not really"),
                "curious", List.of("what happened next")));
        broken.put("example_post_openers", List.of("yesterday my boss did it again", "since getting married",
                "when I mentioned parental leave"));
        broken.put("example_comments", List.of("that is not right", "rough day huh", "same thing happened to me",
                "companies are like that", "you should quit soon"));
        broken.put("example_replies", List.of("I know right", "same here", "good luck"));
        broken.put("post_style", "state the conclusion first");
        broken.put("comment_style", "short and blunt");
        broken.put("reply_style", "one line of empathy");
        broken.put("interests", Map.of("WORK", 0.9, "COUPLE", 0.2, "MARRIED", 0.6, "FRIEND", 0.4, "FAMILY", 0.7));

        when(pool.executeSyncTask(anyString(), anyString(), anyLong(), anyString())).thenReturn(toJson(broken));
        PersonaProfileService service = configuredService(pool);

        assertThrows(StructuredGenerationException.class, () -> service.generate(req(), "corr-7"));
    }

    // ── 리뷰 결함 #3 — interests 등 형태 검사 ────────────────────────────

    /**
     * interests는 프롬프트(persona_profile.md:22)가 {"WORK":0~1,...} 5키 가중치 맵을 요구하는데
     * "취미 목록"으로 오독해 모델이 배열을 줄 수 있다. 지금까지는 그대로 통과해 다운스트림에서
     * 조용한 타입 불일치가 됐다.
     */
    @Test
    void generate_interestsAsArrayInsteadOfMap_throws() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        Map<String, Object> broken = new LinkedHashMap<>(validResponseMap());
        broken.put("interests", List.of("독서", "영화"));
        when(pool.executeSyncTask(anyString(), anyString(), anyLong(), anyString())).thenReturn(toJson(broken));
        PersonaProfileService service = configuredService(pool);

        assertThrows(StructuredGenerationException.class, () -> service.generate(req(), "corr-8"));
    }

    @Test
    void generate_interestsMissingRequiredKey_throws() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        Map<String, Object> broken = new LinkedHashMap<>(validResponseMap());
        broken.put("interests", Map.of("WORK", 0.9, "COUPLE", 0.2, "MARRIED", 0.6, "FRIEND", 0.4));
        when(pool.executeSyncTask(anyString(), anyString(), anyLong(), anyString())).thenReturn(toJson(broken));
        PersonaProfileService service = configuredService(pool);

        assertThrows(StructuredGenerationException.class, () -> service.generate(req(), "corr-9"));
    }

    @Test
    void generate_interestsValueOutOfRange_throws() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        Map<String, Object> broken = new LinkedHashMap<>(validResponseMap());
        broken.put("interests", Map.of("WORK", 1.5, "COUPLE", 0.2, "MARRIED", 0.6, "FRIEND", 0.4, "FAMILY", 0.7));
        when(pool.executeSyncTask(anyString(), anyString(), anyLong(), anyString())).thenReturn(toJson(broken));
        PersonaProfileService service = configuredService(pool);

        assertThrows(StructuredGenerationException.class, () -> service.generate(req(), "corr-10"));
    }

    @Test
    void generate_mobileTyposNotBoolean_throws() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        Map<String, Object> broken = new LinkedHashMap<>(validResponseMap());
        broken.put("writing_quirks", Map.of("spelling_level", "정확", "consistent_errors", List.of(), "mobile_typos", "yes"));
        when(pool.executeSyncTask(anyString(), anyString(), anyLong(), anyString())).thenReturn(toJson(broken));
        PersonaProfileService service = configuredService(pool);

        assertThrows(StructuredGenerationException.class, () -> service.generate(req(), "corr-11"));
    }

    @Test
    void generate_hotButtonsTriggersNotStringArray_throws() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        Map<String, Object> broken = new LinkedHashMap<>(validResponseMap());
        broken.put("hot_buttons", Map.of("triggers", "회사 갑질",
                "soft_spots", List.of("가족", "동료"), "upvote_when", "구체적 사건이 있을 때"));
        when(pool.executeSyncTask(anyString(), anyString(), anyLong(), anyString())).thenReturn(toJson(broken));
        PersonaProfileService service = configuredService(pool);

        assertThrows(StructuredGenerationException.class, () -> service.generate(req(), "corr-12"));
    }

    @Test
    void generate_reactionsAgreeNotStringArray_throws() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        Map<String, Object> broken = new LinkedHashMap<>(validResponseMap());
        broken.put("reactions", Map.of("agree", List.of(), "disagree", List.of("그건 아니지"),
                "curious", List.of("그래서 어떻게 됐음")));
        when(pool.executeSyncTask(anyString(), anyString(), anyLong(), anyString())).thenReturn(toJson(broken));
        PersonaProfileService service = configuredService(pool);

        assertThrows(StructuredGenerationException.class, () -> service.generate(req(), "corr-13"));
    }

    @Test
    void generate_examplePostOpenersNotStringArray_throws() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        Map<String, Object> broken = new LinkedHashMap<>(validResponseMap());
        broken.put("example_post_openers", List.of(1, 2, 3));
        when(pool.executeSyncTask(anyString(), anyString(), anyLong(), anyString())).thenReturn(toJson(broken));
        PersonaProfileService service = configuredService(pool);

        assertThrows(StructuredGenerationException.class, () -> service.generate(req(), "corr-14"));
    }

    // ── 리뷰 결함 #4 — JsonExtractorUtil 통일 ────────────────────────────

    /**
     * 이전 구현은 text.indexOf("```json")/lastIndexOf("```") 전역 검색이라, 실제 코드펜스 없이
     * 온전한 JSON 응답인데 문자열 값 안에 우연히 "```json"·"```"이 들어 있으면 그 지점에서
     * 잘못 잘라내 파싱에 실패했다(응답 앞부분이 통째로 날아가 '{'를 못 찾음).
     * JsonExtractorUtil로 통일한 뒤에는 Attempt1(직접 파싱)이 먼저 성공해 이 케이스를 그대로 살린다.
     */
    @Test
    void generate_valueContainingBacktickFenceMarkers_stillParsesFullObject() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        Map<String, Object> tricky = new LinkedHashMap<>(validResponseMap());
        tricky.put("job_title", "인용 예시: ```json 형식으로 써주세요 ``` 이런 식");
        when(pool.executeSyncTask(anyString(), anyString(), anyLong(), anyString())).thenReturn(toJson(tricky));
        PersonaProfileService service = configuredService(pool);

        Map<String, Object> result = service.generate(req(), "corr-15");

        assertEquals("인용 예시: ```json 형식으로 써주세요 ``` 이런 식", result.get("job_title"));
        assertTrue(result.containsKey("interests"));
    }

    @Test
    void parseJson_recoversFullObjectDespiteEmbeddedBacktickFenceMarkers() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        PersonaProfileService service = configuredService(pool);
        // 이전 구현: indexOf("```json")가 "a" 값 안의 문구를 fence-open으로 오인하고,
        // lastIndexOf("```")가 "b" 값 안의 문구를 fence-close로 오인해 그 사이(선두 '{' 포함)를
        // 통째로 잘라내 파싱이 실패했다(start<0 → "response is not JSON").
        String raw = "{\"a\":\"aaa ```json bbb\",\"b\":\"ccc ``` ddd\"}";

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>)
                ReflectionTestUtils.invokeMethod(service, "parseJson", raw);

        assertEquals("aaa ```json bbb", parsed.get("a"));
        assertEquals("ccc ``` ddd", parsed.get("b"));
    }
}
