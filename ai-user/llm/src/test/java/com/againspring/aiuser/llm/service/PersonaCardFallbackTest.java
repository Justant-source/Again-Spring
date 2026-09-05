package com.againspring.aiuser.llm.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** persona-diversity-v4 WP2 계약4 — PersonaCard가 없을 때의 임시 축약기. */
class PersonaCardFallbackTest {

    @Test
    void rendersAgeGenderJobStyleAndSignaturePhrasesWithin300Chars() {
        Map<String, Object> voiceProfile = Map.of(
                "age", "30대 초반",
                "gender", "남",
                "job", "중견 제조업 구매팀 대리",
                "general_style", "직설적이고 담백한 반말",
                "lexicon", Map.of("signature_phrases", List.of("결론부터", "이건 좀", "아 근데")));

        String card = PersonaCardFallback.renderFromVoiceProfile(voiceProfile);

        assertTrue(card.contains("30대 초반"));
        assertTrue(card.contains("남"));
        assertTrue(card.contains("중견 제조업 구매팀 대리"));
        assertTrue(card.contains("직설적이고 담백한 반말"));
        assertTrue(card.contains("결론부터"));
        assertTrue(card.length() <= 300);
    }

    @Test
    void handlesTopLevelSignaturePhrasesWithoutLexiconNesting() {
        Map<String, Object> voiceProfile = Map.of(
                "age", "20대 후반",
                "signature_phrases", List.of("ㅋㅋ 그니까", "진심"));

        String card = PersonaCardFallback.renderFromVoiceProfile(voiceProfile);

        assertTrue(card.contains("20대 후반"));
        assertTrue(card.contains("ㅋㅋ 그니까"));
    }

    @Test
    void emptyVoiceProfileYieldsBlankCard() {
        assertEquals("", PersonaCardFallback.renderFromVoiceProfile(Map.of()));
        assertEquals("", PersonaCardFallback.renderFromVoiceProfile(null));
        assertEquals("", PersonaCardFallback.render(null));
        assertEquals("", PersonaCardFallback.render(Map.of("personaId", "p1")));
    }

    @Test
    void renderUnwrapsNestedVoiceProfileFromOrchestratorPersonaMap() {
        Map<String, Object> raw = Map.of(
                "personaId", "p1",
                "nickname", "야근일상",
                "voiceProfile", Map.of("age", "34", "job", "구매팀 대리"));

        String card = PersonaCardFallback.render(raw);

        assertTrue(card.contains("34"));
        assertTrue(card.contains("구매팀 대리"));
    }

    @Test
    void longSignaturePhraseListIsTruncatedTo300Chars() {
        List<String> manyPhrases = List.of(
                "아 진짜 이건 좀", "결론부터 말하면", "근데 생각해보니", "그니까 내 말이",
                "진짜 어이없네", "아니 근데 그게", "솔직히 말해서", "이게 맞는건가",
                "하 진짜 답답하다", "그래서 결국은", "아무튼 그래서", "이건 진짜 아니지");
        Map<String, Object> voiceProfile = Map.of(
                "age", "40대", "gender", "여", "job", "자영업",
                "general_style", "장문에 감정 위주, 반복 강조가 잦음",
                "lexicon", Map.of("signature_phrases", manyPhrases));

        String card = PersonaCardFallback.renderFromVoiceProfile(voiceProfile);

        assertTrue(card.length() <= 300);
    }
}
