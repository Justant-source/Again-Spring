package com.againspring.aiuser.orchestrator.persona;

import com.againspring.aiuser.orchestrator.domain.Persona;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 00-shared.md 계약 4 — PersonaCard.render 400자 이내 · 필수 줄 5개. */
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
    void render_isWithin400Chars() {
        String card = PersonaCard.render(fullPersona(), "야근일상");
        assertThat(card.length()).isLessThanOrEqualTo(400);
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

    @Test
    void render_styleAxesKoreanTranslation() {
        String card = PersonaCard.render(fullPersona(), "야근일상");
        assertThat(card).contains("직설").contains("분석").contains("진지").contains("방어")
                .contains("단문").contains("반말").contains("ㅋㅋ 낮음").contains("맞춤법 정확")
                .contains("줄바꿈 잘게").contains("욕설 없음");
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
        assertThat(card.length()).isLessThanOrEqualTo(400);
    }
}
