package com.againspring.aiuser.orchestrator.service;

import com.againspring.aiuser.orchestrator.client.AiLearningClient;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PersonaMatchAudit;
import com.againspring.aiuser.orchestrator.repository.PersonaMatchAuditRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class PersonaCapsuleSearchServiceTest {

    @Mock private AiLearningClient aiLearningClient;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private PersonaRepository personaRepository;
    @Mock private PersonaMatchAuditRepository matchAuditRepository;

    private PersonaCapsuleSearchService service;

    @BeforeEach
    void setUp() {
        service = new PersonaCapsuleSearchService(
                aiLearningClient, jdbcTemplate, personaRepository, matchAuditRepository);
    }

    // ── pure aggregation ────────────────────────────────────────────────────

    @Test
    void aggregateByPersona_takesBestWeightedScoreAndCollectsTypes() {
        List<PersonaCapsuleSearchService.CapsuleHit> hits = List.of(
                new PersonaCapsuleSearchService.CapsuleHit("p1", "INTEREST", 0.9, 1.0),
                new PersonaCapsuleSearchService.CapsuleHit("p1", "VALUE", 0.8, 1.0),
                new PersonaCapsuleSearchService.CapsuleHit("p2", "EXPERIENCE", 0.95, 0.5), // 0.475
                new PersonaCapsuleSearchService.CapsuleHit("p3", "INTEREST", 0.7, 1.0)
        );

        List<PersonaCapsuleSearchService.PersonaMatch> ranked =
                PersonaCapsuleSearchService.aggregateByPersona(hits, 2);

        assertThat(ranked).hasSize(2);
        assertThat(ranked.get(0).personaId()).isEqualTo("p1");
        assertThat(ranked.get(0).score()).isCloseTo(0.9, offset(1e-9));
        assertThat(ranked.get(0).matchedCapsuleTypes()).containsExactlyInAnyOrder("INTEREST", "VALUE");
        assertThat(ranked.get(0).fromFallback()).isFalse();
        assertThat(ranked.get(1).personaId()).isEqualTo("p3");
    }

    @Test
    void aggregateByPersona_emptyOrNull_returnsEmpty() {
        assertThat(PersonaCapsuleSearchService.aggregateByPersona(List.of(), 5)).isEmpty();
        assertThat(PersonaCapsuleSearchService.aggregateByPersona(null, 5)).isEmpty();
    }

    @Test
    void toVecFromText_formatsBracketCommaList() {
        assertThat(PersonaCapsuleSearchService.toVecFromText(List.of(0.1, -0.2)))
                .isEqualTo("[0.10000000,-0.20000000]");
    }

    @Test
    void resolveCategory_fromExplicitOrSearchPrefix() {
        assertThat(PersonaCapsuleSearchService.resolveCategory("work", null)).isEqualTo("WORK");
        assertThat(PersonaCapsuleSearchService.resolveCategory(null, "FAMILY 부모 갈등"))
                .isEqualTo("FAMILY");
        assertThat(PersonaCapsuleSearchService.resolveCategory(null, "아무말")).isEqualTo("OTHER");
    }

    // ── interests fallback ───────────────────────────────────────────────────

    @Test
    void fallbackByInterests_ordersByCategoryInterestAndRegister() {
        Persona blindWork = persona("b1", "BLIND", Map.of("WORK", 0.9, "COUPLE", 0.1));
        Persona natepanWork = persona("n1", "NATEPAN", Map.of("WORK", 0.8));
        Persona blindCouple = persona("b2", "BLIND", Map.of("WORK", 0.2, "COUPLE", 0.9));
        when(personaRepository.findByActiveTrue()).thenReturn(List.of(blindWork, natepanWork, blindCouple));

        List<PersonaCapsuleSearchService.PersonaMatch> ranked =
                service.fallbackByInterests("WORK", 10, "BLIND");

        assertThat(ranked).extracting(PersonaCapsuleSearchService.PersonaMatch::personaId)
                .containsExactly("b1", "b2");
        assertThat(ranked.get(0).score()).isCloseTo(0.9, offset(1e-9));
        assertThat(ranked.get(0).fromFallback()).isTrue();
        assertThat(ranked.get(0).matchedCapsuleTypes()).isEmpty();
    }

    // ── search degrade / capsule path ───────────────────────────────────────

    @Test
    void search_whenNoCapsules_degradesToInterestsWithoutEmbed() {
        when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Integer.class))).thenReturn(0);
        when(personaRepository.findByActiveTrue()).thenReturn(List.of(
                persona("p1", "NATEPAN", Map.of("COUPLE", 0.7))));

        List<PersonaCapsuleSearchService.PersonaMatch> results =
                service.search("COUPLE 배신감", 3, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).fromFallback()).isTrue();
        verify(aiLearningClient, never()).embedOptional(anyString());
    }

    @Test
    void search_whenEmbedFails_degradesToInterests() {
        when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Integer.class))).thenReturn(10);
        when(aiLearningClient.embedOptional(anyString())).thenReturn(Optional.empty());
        when(personaRepository.findByActiveTrue()).thenReturn(List.of(
                persona("p1", "NATEPAN", Map.of("WORK", 0.5))));

        List<PersonaCapsuleSearchService.PersonaMatch> results =
                service.search(PersonaCapsuleSearchService.SearchQuery.builder()
                        .searchText("WORK 야근")
                        .topK(2)
                        .category("WORK")
                        .build());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).fromFallback()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_whenCapsulesHit_aggregatesByPersona() {
        when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Integer.class))).thenReturn(5);
        when(aiLearningClient.embedOptional("MARRIED 생활비")).thenReturn(Optional.of(List.of(0.1, 0.2)));

        List<PersonaCapsuleSearchService.CapsuleHit> hits = List.of(
                new PersonaCapsuleSearchService.CapsuleHit("a", "INTEREST", 0.88, 1.0),
                new PersonaCapsuleSearchService.CapsuleHit("a", "VALUE", 0.70, 1.0),
                new PersonaCapsuleSearchService.CapsuleHit("b", "EXPERIENCE", 0.80, 1.0)
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any()))
                .thenReturn(hits);

        List<PersonaCapsuleSearchService.PersonaMatch> results =
                service.search("MARRIED 생활비", 5, null);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).personaId()).isEqualTo("a");
        assertThat(results.get(0).matchedCapsuleTypes()).containsExactlyInAnyOrder("INTEREST", "VALUE");
        assertThat(results.get(0).fromFallback()).isFalse();
        assertThat(results.get(1).personaId()).isEqualTo("b");
        verify(personaRepository, never()).findByActiveTrue();
    }

    @Test
    void search_withPurpose_writesMatchAuditsBestEffort() {
        when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Integer.class))).thenReturn(0);
        when(personaRepository.findByActiveTrue()).thenReturn(List.of(
                persona("p9", "BLIND", Map.of("WORK", 1.0))));
        when(matchAuditRepository.save(any(PersonaMatchAudit.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.search(PersonaCapsuleSearchService.SearchQuery.builder()
                .searchText("WORK")
                .topK(1)
                .register("BLIND")
                .purpose(PersonaCapsuleSearchService.PURPOSE_AUTHOR)
                .correlationId("corr-1")
                .sourceExampleId(42L)
                .build());

        ArgumentCaptor<PersonaMatchAudit> captor = ArgumentCaptor.forClass(PersonaMatchAudit.class);
        verify(matchAuditRepository).save(captor.capture());
        PersonaMatchAudit audit = captor.getValue();
        assertThat(audit.getCorrelationId()).isEqualTo("corr-1");
        assertThat(audit.getSourceExampleId()).isEqualTo(42L);
        assertThat(audit.getPurpose()).isEqualTo("AUTHOR_CANDIDATE");
        assertThat(audit.getPersonaId()).isEqualTo("p9");
        assertThat(audit.getReasons()).containsEntry("mode", "interests_fallback");
    }

    private static Persona persona(String id, String voiceType, Map<String, Double> interests) {
        return Persona.builder()
                .id(id)
                .archetype("TEST")
                .tier("REGULAR")
                .voiceProfile(Map.of("voice_type", voiceType))
                .interests(interests)
                .biasProfile(Map.of())
                .circadian(List.of())
                .active(true)
                .build();
    }
}
