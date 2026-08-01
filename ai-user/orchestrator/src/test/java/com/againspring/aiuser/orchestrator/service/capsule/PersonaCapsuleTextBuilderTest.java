package com.againspring.aiuser.orchestrator.service.capsule;

import com.againspring.aiuser.orchestrator.domain.Persona;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PersonaCapsuleTextBuilderTest {

    @Test
    void buildsThreeCapsulesWithStableHash() {
        Persona p = samplePersona();
        List<PersonaCapsuleTextBuilder.CapsuleDraft> drafts = PersonaCapsuleTextBuilder.buildCapsules(p);
        assertEquals(3, drafts.size());
        assertEquals("INTEREST", drafts.get(0).capsuleType());
        assertEquals("WORK", drafts.get(0).topicKey());
        assertTrue(drafts.get(0).text().contains("직장 갈등"));
        assertEquals(64, drafts.get(0).contentHash().length());

        assertEquals("EXPERIENCE", drafts.get(1).capsuleType());
        assertTrue(drafts.get(1).text().contains("20대 후반"));
        assertTrue(drafts.get(1).text().contains("결혼·육아"));

        assertEquals("VALUE", drafts.get(2).capsuleType());
        assertTrue(drafts.get(2).text().contains("공정성") || drafts.get(2).text().contains("말투"));

        String again = PersonaCapsuleTextBuilder.contentHash(
                drafts.get(0).capsuleType(), drafts.get(0).topicKey(), drafts.get(0).text());
        assertEquals(drafts.get(0).contentHash(), again);
    }

    @Test
    void hashChangesWhenTextChanges() {
        String a = PersonaCapsuleTextBuilder.contentHash("INTEREST", "WORK", "직장 갈등");
        String b = PersonaCapsuleTextBuilder.contentHash("INTEREST", "WORK", "직장 갈등 ");
        assertNotEquals(a, b);
    }

    @Test
    void factsFromVoiceProfileAreLegacyImported() {
        Persona p = samplePersona();
        List<PersonaCapsuleTextBuilder.FactDraft> facts = PersonaCapsuleTextBuilder.buildFacts(p);
        assertTrue(facts.size() >= 5);
        assertTrue(facts.stream().anyMatch(f -> "age".equals(f.factKey()) && "20s_late".equals(f.factValue())));
        assertTrue(facts.stream().allMatch(f ->
                PersonaCapsuleTextBuilder.ORIGIN_LEGACY.equals(f.origin())));
        assertTrue(facts.stream().allMatch(f ->
                f.confidence().compareTo(new BigDecimal("0.600")) >= 0));
    }

    @Test
    void unmarriedHeuristicSkipsHousewife() {
        Map<String, Object> vp = new LinkedHashMap<>();
        vp.put("age", "30s");
        vp.put("job", "주부");
        Map<String, Double> interests = Map.of("MARRIED", 0.2);
        List<String> avoid = PersonaCapsuleTextBuilder.cannotClaimHeuristics(vp, interests);
        assertTrue(avoid.isEmpty());
    }

    private static Persona samplePersona() {
        Map<String, Object> vp = new LinkedHashMap<>();
        vp.put("age", "20s_late");
        vp.put("gender", "F");
        vp.put("job", "직장인");
        vp.put("region", "서울");
        vp.put("voice_type", "NATEPAN");
        vp.put("formality", "casual");
        vp.put("general_style", "공정성과 개인경계를 중시하는 직설 톤");

        Map<String, Double> interests = new LinkedHashMap<>();
        interests.put("WORK", 0.9);
        interests.put("COUPLE", 0.5);
        interests.put("MARRIED", 0.2);
        interests.put("FAMILY", 0.3);
        interests.put("FRIEND", 0.4);
        interests.put("OTHER", 0.1);

        Map<String, Double> bias = Map.of("WORK", 0.2, "COUPLE", -0.1);

        return Persona.builder()
                .id("testpersona01")
                .archetype("work_toxic")
                .tier("HEAVY")
                .voiceProfile(vp)
                .interests(interests)
                .biasProfile(bias)
                .circadian(List.of())
                .active(true)
                .build();
    }
}
