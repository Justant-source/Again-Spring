package com.againspring.aiuser.orchestrator.persona;

import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.repository.PersonaActionLogRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 배치 중단·재개 내구성 검증 (LLM 미호출 — {@link PersonaProfileLlmClient}는 mock).
 * 배경: dev 12명 배치에서 6명이 Claude 세션 한도 소진으로 {@code LLM_CALL_FAILED}였다
 * ("Claude CLI error result (subtype=success): You've hit your session limit · resets 6am (UTC)").
 * 이 테스트는 그 시나리오를 재현해 (1) 시그니처 즉시 중단, (2) 연속 실패 차단기,
 * (3) style_axes IS NULL 기반 재개 skip, (4) remaining 응답 필드를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PersonaProfileRegeneratorTest {

    @Mock private PersonaRepository personaRepo;
    @Mock private PersonaQuotaPlanner quotaPlanner;
    @Mock private PersonaProfileLlmClient llmClient;
    @Mock private PersonaActionLogRepository actionLogRepository;
    @Mock private JdbcTemplate jdbcTemplate;

    private PersonaProfileRegenerator regenerator;

    @BeforeEach
    void setUp() {
        regenerator = new PersonaProfileRegenerator(
                personaRepo, quotaPlanner, llmClient, actionLogRepository, jdbcTemplate);
    }

    private static Persona persona(String id, Map<String, String> styleAxes) {
        return Persona.builder()
                .id(id).archetype("general").tier("REGULAR")
                .voiceProfile(null).interests(Map.of()).biasProfile(Map.of())
                .circadian(List.of()).active(true).createdAt(Instant.now())
                .ageYears(30).gender("F").marital("SINGLE").jobType("CORP_LARGE")
                .styleAxes(styleAxes)
                .build();
    }

    private static PersonaQuotaPlanner.IdentityAxes axes() {
        return new PersonaQuotaPlanner.IdentityAxes(30, "F", "SINGLE", null, false, "CORP_LARGE", "REGULAR",
                Map.of("speech", "BANMAL", "emoticon", "LOW", "profanity", "NONE"));
    }

    private static PersonaProfileLlmClient.ProfileResult success(String personaId) {
        return new PersonaProfileLlmClient.ProfileResult(
                Map.of("lexicon", Map.of("signature_phrases", List.of("고유문구-" + personaId))), null);
    }

    @Test
    void resumeSkipsPersonasThatAlreadyHaveStyleAxes() {
        Persona done = persona("p1", Map.of("speech", "BANMAL"));
        Persona pending = persona("p2", null);
        when(personaRepo.findActiveIdsOrderById()).thenReturn(List.of("p1", "p2"));
        when(personaRepo.findByActiveTrue()).thenReturn(List.of(done, pending));
        when(quotaPlanner.plan(anyList(), anyLong())).thenReturn(Map.of("p1", axes(), "p2", axes()));
        when(llmClient.generatePersonaProfile(eq("p2"), any(), any(), any(), any(), anyList()))
                .thenReturn(success("p2"));

        Map<String, Object> result = regenerator.regenerate(1L, 10, null, false);

        assertThat(result.get("targetCount")).isEqualTo(1); // p1은 이미 style_axes 있음 → skip
        assertThat(result.get("succeeded")).isEqualTo(1);
        assertThat(result.get("remaining")).isEqualTo(0L);
        assertThat(result.get("haltedReason")).isNull();
        verify(llmClient, never()).generatePersonaProfile(eq("p1"), any(), any(), any(), any(), anyList());
    }

    @Test
    void forceReprocessesPersonasThatAlreadyHaveStyleAxes() {
        Persona done = persona("p1", Map.of("speech", "BANMAL"));
        when(personaRepo.findActiveIdsOrderById()).thenReturn(List.of("p1"));
        when(personaRepo.findByActiveTrue()).thenReturn(List.of(done));
        when(quotaPlanner.plan(anyList(), anyLong())).thenReturn(Map.of("p1", axes()));
        when(llmClient.generatePersonaProfile(eq("p1"), any(), any(), any(), any(), anyList()))
                .thenReturn(success("p1"));

        Map<String, Object> result = regenerator.regenerate(1L, 10, null, true);

        assertThat(result.get("targetCount")).isEqualTo(1);
        assertThat(result.get("succeeded")).isEqualTo(1);
    }

    @Test
    void haltsImmediatelyOnErrorSignatureWithoutExhaustingPerPersonaRetries() {
        Persona p1 = persona("p1", null);
        Persona p2 = persona("p2", null);
        when(personaRepo.findActiveIdsOrderById()).thenReturn(List.of("p1", "p2"));
        when(personaRepo.findByActiveTrue()).thenReturn(List.of(p1, p2));
        when(quotaPlanner.plan(anyList(), anyLong())).thenReturn(Map.of("p1", axes(), "p2", axes()));
        when(llmClient.generatePersonaProfile(eq("p1"), any(), any(), any(), any(), anyList()))
                .thenReturn(new PersonaProfileLlmClient.ProfileResult(null,
                        "Claude CLI error result (subtype=success): You've hit your session limit "
                                + "· resets 6am (UTC)"));

        Map<String, Object> result = regenerator.regenerate(1L, 10, null, false);

        assertThat((String) result.get("haltedReason")).contains("LLM_ERROR_SIGNATURE");
        assertThat(result.get("succeeded")).isEqualTo(0);
        // 시그니처 감지 시 같은 페르소나도 재시도(최대 3회)하지 않고 즉시 멈춘다.
        verify(llmClient, times(1)).generatePersonaProfile(eq("p1"), any(), any(), any(), any(), anyList());
        // 남은 대상(p2)은 아예 시도하지 않는다.
        verify(llmClient, never()).generatePersonaProfile(eq("p2"), any(), any(), any(), any(), anyList());
    }

    @Test
    void haltsAfterMaxConsecutiveFailuresEvenWithoutSignatureMatch() {
        Persona p1 = persona("p1", null);
        Persona p2 = persona("p2", null);
        Persona p3 = persona("p3", null);
        when(personaRepo.findActiveIdsOrderById()).thenReturn(List.of("p1", "p2", "p3"));
        when(personaRepo.findByActiveTrue()).thenReturn(List.of(p1, p2, p3));
        when(quotaPlanner.plan(anyList(), anyLong()))
                .thenReturn(Map.of("p1", axes(), "p2", axes(), "p3", axes()));
        when(llmClient.generatePersonaProfile(any(), any(), any(), any(), any(), anyList()))
                .thenReturn(new PersonaProfileLlmClient.ProfileResult(null, "connection reset by peer"));

        Map<String, Object> result = regenerator.regenerate(1L, 10, null, false, 2);

        assertThat(result.get("haltedReason")).isEqualTo("CONSECUTIVE_FAILURES(2)");
        verify(llmClient, never()).generatePersonaProfile(eq("p3"), any(), any(), any(), any(), anyList());
        // p1, p2 각각 페르소나당 최대 3회(재시도 상한, .claude/rules/llm-safety.md §4) = 총 6회
        verify(llmClient, times(6)).generatePersonaProfile(any(), any(), any(), any(), any(), anyList());
    }

    @Test
    void remainingCountsOnlyPersonasStillWithoutStyleAxes() {
        Persona done = persona("p1", Map.of("speech", "BANMAL"));
        Persona willSucceed = persona("p2", null);
        Persona willFail = persona("p3", null);
        when(personaRepo.findActiveIdsOrderById()).thenReturn(List.of("p1", "p2", "p3"));
        when(personaRepo.findByActiveTrue()).thenReturn(List.of(done, willSucceed, willFail));
        when(quotaPlanner.plan(anyList(), anyLong()))
                .thenReturn(Map.of("p1", axes(), "p2", axes(), "p3", axes()));
        when(llmClient.generatePersonaProfile(eq("p2"), any(), any(), any(), any(), anyList()))
                .thenReturn(success("p2"));
        when(llmClient.generatePersonaProfile(eq("p3"), any(), any(), any(), any(), anyList()))
                .thenReturn(new PersonaProfileLlmClient.ProfileResult(null, "temporary glitch"));

        Map<String, Object> result = regenerator.regenerate(1L, 10, null, false, 100);

        // p1(기존 완료) + p2(이번 성공) = style_axes 있음, p3만 여전히 null
        assertThat(result.get("remaining")).isEqualTo(1L);
        assertThat(result.get("succeeded")).isEqualTo(1);
        assertThat(result.get("skipped")).isEqualTo(1);
        assertThat(result.get("haltedReason")).isNull();
    }
}
