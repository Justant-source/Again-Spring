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
                .contains("라벨이 아니라 명령이다")
                .contains("directness=BLUNT: 돌려 말하지 않고")
                .contains("affect=ANALYTIC: 감정보다 상황을 분석하듯")
                .contains("humor=SERIOUS: 농담 없이")
                .contains("stance=DEFENSIVE: 내 잘못일 가능성을 먼저 방어적으로")
                .contains("length=SHORT: 문장을 짧게 끊어 쓴다")
                .contains("speech=BANMAL: 반말만 쓴다")
                .contains("emoticon=LOW: 이모티콘은 글 전체에서 한두 번만 쓴다")
                .contains("spelling=CLEAN: 맞춤법·띄어쓰기를 정확히 지킨다")
                .contains("linebreak=CHOPPED: 한두 문장마다 줄을 바꾼다")
                .contains("profanity=NONE: 욕설을 전혀 쓰지 않는다");
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
                .contains("directness=SOFT: 직접 말하지 않고 에둘러 표현한다")
                .contains("affect=EMOTIONAL: 감정을 억누르지 않고")
                .contains("humor=JOKER: 자조나 드립을 최소 1번은 실제로 넣는다")
                .contains("stance=OFFENSIVE: 상대 잘못을 직접 지적하며 몰아붙인다")
                .contains("length=LONG: 문장을 길게 늘여 쓴다")
                .contains("speech=JONDAE: 존댓말만 쓴다")
                .contains("emoticon=HIGH: 문단마다 ㅋㅋ·ㅠㅠ 같은 표현을 실제로 넣는다")
                .contains("spelling=SLOPPY: 오탈자·축약을 자연스럽게 섞는다")
                .contains("linebreak=WALL: 줄바꿈 없이 한 문단으로 몰아 쓴다")
                .contains("profanity=HEAVY: 욕설·비속어를 실제로 섞어 쓴다 — 순화하지 않는다");
    }

    /** speech=MIXED 지시문 — 3지 축 중 나머지 하나. */
    @Test
    void render_speechMixedDirective() {
        Persona p = fullPersona();
        p.setStyleAxes(Map.of("speech", "MIXED"));
        assertThat(PersonaCard.render(p, "야근일상"))
                .contains("speech=MIXED: 반말과 존댓말을 문장마다 섞어 쓴다");
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
}
