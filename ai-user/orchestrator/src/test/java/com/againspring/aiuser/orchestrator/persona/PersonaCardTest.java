package com.againspring.aiuser.orchestrator.persona;

import com.againspring.aiuser.orchestrator.domain.Persona;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 00-shared.md 계약 4 — PersonaCard.render 필수 줄 5개.
 * 2026-09 순응도 개정으로 400자 상한은 1100자로 늘었다(PersonaCard.MAX_LEN 주석 참고) —
 * 라벨("직설/분석") 대신 축별 명령문("directness=BLUNT: 돌려 말하지 않고...")을 쓰면서
 * 실측 카드 길이가 늘었기 때문.
 */
class PersonaCardTest {

    private Persona fullPersona() {
        return Persona.builder()
                .id("p001")
                .archetype("work_toxic")
                .tier("HEAVY")
                .voiceProfile(Map.of(
                        "region", "경기",
                        "nickname", "야근일상",
                        "lexicon", Map.of(
                                "signature_phrases", List.of("결론부터", "이건 좀", "아 근데"),
                                "typing_habit", "문장 끝에 ㅇㅇ 붙임"),
                        "hot_buttons", Map.of("triggers", List.of("회사 갑질", "육아 분담 안 하는 배우자"))
                ))
                .interests(Map.of("WORK", 0.9, "FAMILY", 0.7, "FRIEND", 0.6))
                .biasProfile(Map.of())
                .circadian(List.of())
                .active(true)
                .createdAt(Instant.now())
                .ageYears(34)
                .gender("M")
                .marital("MARRIED")
                .marriedYears(6)
                .hasKids(true)
                .jobType("CORP_MID")
                .jobTitle("중견 제조업 구매팀 대리")
                .styleAxes(Map.of(
                        "directness", "BLUNT", "affect", "ANALYTIC", "humor", "SERIOUS",
                        "stance", "DEFENSIVE", "length", "SHORT", "speech", "BANMAL",
                        "emoticon", "LOW", "spelling", "CLEAN", "linebreak", "CHOPPED", "profanity", "NONE"))
                .build();
    }

    @Test
    void render_isWithinLengthBudget() {
        String card = PersonaCard.render(fullPersona(), "야근일상");
        // 실측(2026-09-05, 이 픽스처): 623자. 여유를 두고 1100자 상한을 지킨다.
        assertThat(card.length()).isLessThanOrEqualTo(1100);
    }

    @Test
    void render_hasAllFiveRequiredLines() {
        String card = PersonaCard.render(fullPersona(), "야근일상");
        assertThat(card).contains("[페르소나]").contains("[말투]").contains("[버릇]")
                .contains("[관심]").contains("[지뢰]");
    }

    @Test
    void render_containsCoreIdentityFacts() {
        String card = PersonaCard.render(fullPersona(), "야근일상");
        assertThat(card).contains("야근일상").contains("34세 남").contains("기혼 6년차")
                .contains("중견 제조업 구매팀 대리").contains("경기");
    }

    /**
     * 2026-09 순응도 개정 — 압축 라벨(예: "직설/분석") 대신 축=값 태그 + 한국어 명령문을 낸다.
     * axis=VALUE 토큰은 self-critique 파서(StructuredGenerationService)가 의도 값을 되짚는
     * 데도 쓰이므로 형식이 안정적이어야 한다.
     */
    @Test
    void render_styleAxesAreImperativeDirectives() {
        String card = PersonaCard.render(fullPersona(), "야근일상");
        assertThat(card)
                .contains("평소 말버릇이다")
                .contains("directness=BLUNT: 돌려 말하지 않고")
                .contains("affect=ANALYTIC: 감정보다 상황을 따져")
                .contains("humor=SERIOUS: 농담 없이")
                .contains("stance=DEFENSIVE: 확신이 없어 남들 생각을 묻는 편")
                .contains("length=SHORT: 문장이 짧게 끊기는 편")
                .contains("speech=BANMAL: 반말로 쓴다")
                .contains("emoticon=LOW: ㅋㅋ·ㅠㅠ 같은 표현은 드물게")
                .contains("spelling=CLEAN: 맞춤법·띄어쓰기를 지키는 편")
                .contains("linebreak=CHOPPED: 줄을 자주 바꾸는 편")
                .contains("profanity=NONE: 욕설을 쓰지 않는 편");
    }

    /** HEAVY/HIGH/WALL/JOKER/OFFENSIVE 등 반대쪽 값도 올바른 명령문으로 렌더링되는지. */
    @Test
    void render_styleAxesOppositeValues() {
        Persona p = fullPersona();
        p.setStyleAxes(Map.of(
                "directness", "SOFT", "affect", "EMOTIONAL", "humor", "JOKER",
                "stance", "OFFENSIVE", "length", "LONG", "speech", "JONDAE",
                "emoticon", "HIGH", "spelling", "SLOPPY", "linebreak", "WALL", "profanity", "HEAVY"));
        String card = PersonaCard.render(p, "야근일상");
        assertThat(card)
                .contains("directness=SOFT: 직접 말하기보다 에둘러")
                .contains("affect=EMOTIONAL: 감정이 말에 그대로")
                .contains("humor=JOKER: 분위기상 어울릴 때")
                .contains("stance=OFFENSIVE: 자기가 옳다고 확신하고 상대 탓을 하는 편")
                .contains("length=LONG: 문장이 긴 편")
                .contains("speech=JONDAE: 존댓말로 쓴다")
                .contains("emoticon=HIGH: ㅋㅋ·ㅠㅠ 같은 표현을 자주")
                .contains("spelling=SLOPPY: 맞춤법·띄어쓰기가 느슨하고")
                .contains("linebreak=WALL: 줄바꿈이 적고")
                .contains("profanity=MILD: 욕설은 아주 가끔");
    }

    /** speech=MIXED 지시문 — 3지 축 중 나머지 하나. */
    @Test
    void render_speechMixedDirective() {
        Persona p = fullPersona();
        p.setStyleAxes(Map.of("speech", "MIXED"));
        assertThat(PersonaCard.render(p, "야근일상"))
                .contains("speech=MIXED: 반말과 존댓말이 섞이는 편");
    }

    @Test
    void render_singleArgOverloadFallsBackToVoiceProfileNickname() {
        String card = PersonaCard.render(fullPersona());
        assertThat(card).contains("닉네임=야근일상");
    }

    @Test
    void render_missingOptionalFieldsDoesNotThrow() {
        Persona minimal = Persona.builder()
                .id("p002")
                .archetype("general")
                .tier("LIGHT")
                .voiceProfile(Map.of())
                .interests(Map.of())
                .biasProfile(Map.of())
                .circadian(List.of())
                .active(true)
                .createdAt(Instant.now())
                .ageYears(25)
                .gender("F")
                .marital("SINGLE")
                .jobType("STARTUP")
                .build();
        String card = PersonaCard.render(minimal);
        assertThat(card).contains("[페르소나]").contains("[말투]");
        assertThat(card.length()).isLessThanOrEqualTo(1100);
    }

    private static final java.util.regex.Pattern TAG = java.util.regex.Pattern.compile("-\\s*(\\w+)=(\\w+):");

    @Test
    void render_noQuotedExamplesOnAnyAxis() {
        for (String v : List.of("DEFENSIVE", "OFFENSIVE")) {
            Persona p = fullPersona();
            p.setStyleAxes(Map.of("stance", v, "spelling", "SLOPPY", "humor", "JOKER"));
            String card = PersonaCard.render(p, "닉");
            assertThat(card).doesNotContain("예민").doesNotContain("어케").doesNotContain("ㄱㅊ")
                    .doesNotContain("예:").doesNotContain("최소 1번").doesNotContain("실제로 넣는다");
        }
    }

    @Test
    void render_defensiveAndOffensiveWording() {
        Persona p = fullPersona();
        p.setStyleAxes(Map.of("stance", "DEFENSIVE"));
        assertThat(PersonaCard.render(p, "닉")).doesNotContain("내 잘못");
        p.setStyleAxes(Map.of("stance", "OFFENSIVE"));
        assertThat(PersonaCard.render(p, "닉")).contains("상대 탓을 하는 편");
    }

    @Test
    void render_jondaeHeavyDowngradedToMildInTag() {
        Persona p = fullPersona();
        p.setStyleAxes(Map.of("speech", "JONDAE", "profanity", "HEAVY"));
        String card = PersonaCard.render(p, "닉");
        assertThat(card).contains("profanity=MILD:").doesNotContain("profanity=HEAVY");
        assertThat(p.getStyleAxes().get("profanity")).isEqualTo("HEAVY"); // DB 값 불변
    }

    @Test
    void render_softHeavyDowngraded_andBanmalHeavyKept() {
        Persona p = fullPersona();
        p.setStyleAxes(Map.of("directness", "SOFT", "profanity", "HEAVY"));
        assertThat(PersonaCard.render(p, "닉")).contains("profanity=MILD:");
        p.setStyleAxes(Map.of("speech", "BANMAL", "profanity", "HEAVY"));
        assertThat(PersonaCard.render(p, "닉")).contains("profanity=HEAVY:");
    }

    @Test
    void render_emoticonHighDowngradedOnlyWhenSeriousAndAnalytic() {
        Persona p = fullPersona();
        p.setStyleAxes(Map.of("emoticon", "HIGH", "humor", "SERIOUS", "affect", "ANALYTIC"));
        assertThat(PersonaCard.render(p, "닉")).contains("emoticon=LOW:").doesNotContain("emoticon=HIGH");
        p.setStyleAxes(Map.of("emoticon", "HIGH", "humor", "SERIOUS", "affect", "EMOTIONAL"));
        assertThat(PersonaCard.render(p, "닉")).contains("emoticon=HIGH:");
    }

    @Test
    void render_tagsParseWithLlmRegex_andWithinBudget() {
        Persona p = fullPersona();
        p.setStyleAxes(Map.of(
                "directness", "SOFT", "affect", "EMOTIONAL", "humor", "JOKER",
                "stance", "OFFENSIVE", "length", "LONG", "speech", "JONDAE",
                "emoticon", "HIGH", "spelling", "SLOPPY", "linebreak", "WALL", "profanity", "HEAVY"));
        String card = PersonaCard.render(p, "야근일상");
        java.util.Map<String, String> found = new java.util.HashMap<>();
        var m = TAG.matcher(card);
        while (m.find()) found.put(m.group(1), m.group(2));
        assertThat(found).hasSize(10).containsEntry("profanity", "MILD").containsEntry("speech", "JONDAE");
        assertThat(card.length()).isLessThanOrEqualTo(1100);
    }

    @Test
    void render_signatureAtMostTwoAndSoftened_seedRotates() {
        Persona p = fullPersona();
        Map<String, Object> vp = new java.util.HashMap<>();
        vp.put("lexicon", Map.of("signature_phrases", List.of("가", "나", "다", "라", "마")));
        p.setVoiceProfile(vp);
        String card = PersonaCard.render(p, "닉");
        assertThat(card).contains("가끔 쓰는 표현 — 안 써도 되고").contains("\"가\", \"나\"")
                .doesNotContain("\"다\"");
        assertThat(PersonaCard.render(p, "닉", 2)).contains("\"다\", \"라\"");
        assertThat(PersonaCard.render(p, "닉", 2)).isEqualTo(PersonaCard.render(p, "닉", 2));
    }

    @Test
    void render_backgroundFactNoteOnPersonaLine() {
        assertThat(PersonaCard.render(fullPersona(), "닉"))
                .contains("직업을 소재로 농담하지 않는다");
    }
}
