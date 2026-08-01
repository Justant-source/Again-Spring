package com.againspring.aiuser.orchestrator.service.match;

import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.PersonaFactAssertion;
import com.againspring.aiuser.orchestrator.domain.PersonaMatchAudit;
import com.againspring.aiuser.orchestrator.domain.StoryProfile;
import com.againspring.aiuser.orchestrator.repository.PersonaFactAssertionRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaMatchAuditRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.service.PersonaCapsuleSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonaMatcherServiceTest {

    @Mock private PersonaCapsuleSearchService capsuleSearchService;
    @Mock private PersonaRepository personaRepository;
    @Mock private PersonaFactAssertionRepository factAssertionRepository;
    @Mock private PersonaMatchAuditRepository matchAuditRepository;

    private PersonaMatcherService matcher;

    @BeforeEach
    void setUp() {
        matcher = new PersonaMatcherService(
                capsuleSearchService,
                personaRepository,
                factAssertionRepository,
                matchAuditRepository,
                0.35);
    }

    @Test
    void hardFilter_unevaluatedAxesDoNotReject() {
        Persona p = persona("p1", true, "NATEPAN", Map.of("gender", "F", "age", "30s"));
        StoryProfile profile = profile("MARRIED", "NATEPAN", Map.of());

        FilterResult result = PersonaHardFilter.filter(p, List.of(), profile);

        assertThat(result.passed()).isTrue();
        assertThat(result.reasons()).contains(
                "UNEVALUATED:marriage",
                "UNEVALUATED:parenting",
                "UNEVALUATED:cannot_claim");
        assertThat(result.reasons()).noneMatch(r -> r.startsWith("FAIL:"));
    }

    @Test
    void hardFilter_genderMismatchRejectsWhenExplicit() {
        Persona p = persona("p1", true, "NATEPAN", Map.of("gender", "M", "age", "30s"));
        StoryProfile profile = profile("COUPLE", "NATEPAN", Map.of("gender", "F"));

        FilterResult result = PersonaHardFilter.filter(p, List.of(), profile);

        assertThat(result.passed()).isFalse();
        assertThat(result.reasons()).contains("FAIL:gender");
        assertThat(result.reasons()).contains("UNEVALUATED:marriage");
    }

    @Test
    void hardFilter_registerMismatchRejects() {
        Persona p = persona("p1", true, "BLIND", Map.of("gender", "F"));
        StoryProfile profile = profile("WORK", "NATEPAN", Map.of());

        FilterResult result = PersonaHardFilter.filter(p, List.of(), profile);

        assertThat(result.passed()).isFalse();
        assertThat(result.reasons()).contains("FAIL:register");
    }

    @Test
    void hardFilter_missingPersonaGenderIsUnevaluatedNotFail() {
        Persona p = persona("p1", true, "NATEPAN", Map.of("age", "30s"));
        StoryProfile profile = profile("COUPLE", "NATEPAN", Map.of("gender", "F"));

        FilterResult result = PersonaHardFilter.filter(p, List.of(), profile);

        assertThat(result.passed()).isTrue();
        assertThat(result.reasons()).contains("UNEVALUATED:gender");
    }

    @Test
    void hardFilter_ageBandFromAnalyzerMatchesVoiceAge() {
        Persona p = persona("p1", true, "NATEPAN", Map.of("age", "30s_late"));
        StoryProfile profile = profile("MARRIED", "NATEPAN", Map.of("age_band", "30s"));

        FilterResult result = PersonaHardFilter.filter(p, List.of(), profile);

        assertThat(result.passed()).isTrue();
        assertThat(result.reasons()).contains("PASS:age");
    }

    @Test
    void scoring_aggregationUsesSimplifiedWeights() {
        Persona p = persona("p1", true, "NATEPAN", Map.of("gender", "F"));
        p.setInterests(Map.of("MARRIED", 0.8));
        FilterResult filter = PersonaHardFilter.filter(
                p, List.of(), profile("MARRIED", "NATEPAN", Map.of("gender", "F")));
        var hit = new PersonaCapsuleSearchService.PersonaMatch(
                "p1", 0.6, List.of("INTEREST"), false);

        RankedPersona ranked = matcher.score(p, hit, filter, "MARRIED", "NATEPAN");

        // 0.45*0.6 + 0.25*1 + 0.15*1 + 0.15*0.8 = 0.79
        assertThat(ranked.score()).isCloseTo(0.79, offset(1e-9));
        assertThat(ranked.semanticScore()).isCloseTo(0.6, offset(1e-9));
        assertThat(ranked.registerMatch()).isEqualTo(1.0);
        assertThat(ranked.explicitFactMatchRatio()).isEqualTo(1.0);
        assertThat(ranked.interestCategoryScore()).isCloseTo(0.8, offset(1e-9));
    }

    @Test
    void matchAuthors_filtersAndRanksWithAudits() {
        StoryProfile story = profile("WORK", "NATEPAN", Map.of("gender", "F"));

        Persona ok = persona("ok", true, "NATEPAN", Map.of("gender", "F", "age", "30s"));
        ok.setInterests(Map.of("WORK", 0.9));
        Persona badGender = persona("badG", true, "NATEPAN", Map.of("gender", "M"));
        badGender.setInterests(Map.of("WORK", 1.0));
        Persona badReg = persona("badR", true, "BLIND", Map.of("gender", "F"));
        badReg.setInterests(Map.of("WORK", 1.0));

        when(capsuleSearchService.search(any())).thenReturn(List.of(
                new PersonaCapsuleSearchService.PersonaMatch("ok", 0.7, List.of("INTEREST"), false),
                new PersonaCapsuleSearchService.PersonaMatch("badG", 0.95, List.of("VALUE"), false),
                new PersonaCapsuleSearchService.PersonaMatch("badR", 0.9, List.of("EXPERIENCE"), false)
        ));
        when(personaRepository.findById("ok")).thenReturn(Optional.of(ok));
        when(personaRepository.findById("badG")).thenReturn(Optional.of(badGender));
        when(personaRepository.findById("badR")).thenReturn(Optional.of(badReg));
        when(factAssertionRepository.findByPersonaId(any())).thenReturn(List.of());
        when(matchAuditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<RankedPersona> ranked = matcher.matchAuthors(story, 5, 42L, "corr-1");

        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).personaId()).isEqualTo("ok");
        assertThat(ranked.get(0).reasons()).anyMatch(r -> r.startsWith("UNEVALUATED:marriage"));

        ArgumentCaptor<PersonaMatchAudit> cap = ArgumentCaptor.forClass(PersonaMatchAudit.class);
        verify(matchAuditRepository, org.mockito.Mockito.atLeast(3)).save(cap.capture());
        assertThat(cap.getAllValues()).extracting(PersonaMatchAudit::getPersonaId)
                .containsExactlyInAnyOrder("ok", "badG", "badR");
        assertThat(cap.getAllValues().stream().filter(a -> "ok".equals(a.getPersonaId())).findFirst())
                .get()
                .satisfies(a -> {
                    assertThat(a.isHardFilterPassed()).isTrue();
                    assertThat(a.isSelected()).isTrue();
                    assertThat(a.getPurpose()).isEqualTo(PersonaMatcherService.PURPOSE_AUTHOR);
                    assertThat(a.getReasons()).containsKey("unevaluatedAxes");
                });
        assertThat(cap.getAllValues().stream().filter(a -> "badG".equals(a.getPersonaId())).findFirst())
                .get()
                .satisfies(a -> assertThat(a.isHardFilterPassed()).isFalse());
    }

    @Test
    void bestAuthorAbove_respectsThreshold() {
        StoryProfile story = profile("OTHER", "NATEPAN", Map.of());
        Persona p = persona("p1", true, "NATEPAN", Map.of());
        p.setInterests(Map.of("OTHER", 0.1));

        when(capsuleSearchService.search(any())).thenReturn(List.of(
                new PersonaCapsuleSearchService.PersonaMatch("p1", 0.1, List.of(), true)));
        when(personaRepository.findById("p1")).thenReturn(Optional.of(p));
        when(factAssertionRepository.findByPersonaId("p1")).thenReturn(List.of());
        when(matchAuditRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(matcher.bestAuthorAbove(story, 0.50, 1L, "c")).isEmpty();
        assertThat(matcher.bestAuthorAbove(story, 0.40, 1L, "c")).isPresent();
    }

    @Test
    void hardFilter_usesFactAssertionWhenVoiceProfileLacksAxis() {
        Persona p = persona("p1", true, "NATEPAN", Map.of());
        List<PersonaFactAssertion> facts = List.of(
                PersonaFactAssertion.builder().personaId("p1").factKey("gender").factValue("F").build());
        StoryProfile story = profile("COUPLE", "NATEPAN", Map.of("gender", "F"));

        FilterResult result = PersonaHardFilter.filter(p, facts, story);

        assertThat(result.passed()).isTrue();
        assertThat(result.reasons()).contains("PASS:gender");
    }

    private static StoryProfile profile(String category, String register, Map<String, String> identity) {
        return new StoryProfile(
                "갈등",
                category,
                List.of("토픽"),
                identity,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                register,
                List.of(),
                "",
                "");
    }

    private static Persona persona(String id, boolean active, String voiceType, Map<String, Object> extraVp) {
        Map<String, Object> vp = new java.util.HashMap<>(extraVp);
        vp.put("voice_type", voiceType);
        return Persona.builder()
                .id(id)
                .archetype("TEST")
                .tier("REGULAR")
                .voiceProfile(vp)
                .interests(Map.of("OTHER", 0.5))
                .biasProfile(Map.of())
                .circadian(List.of())
                .active(active)
                .build();
    }
}
