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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 배치 중단·재개 내구성 검증 (LLM 미호출 — {@link PersonaProfileLlmClient}는 mock).
 * 배경: dev 12명 배치에서 6명이 Claude 세션 한도 소진으로 {@code LLM_CALL_FAILED}였다
 * ("Claude CLI error result (subtype=success): You've hit your session limit · resets 6am (UTC)").
 * 이 테스트는 그 시나리오를 재현해 (1) 시그니처 즉시 중단, (2) 연속 실패 차단기,
 * (3) {@code voice_profile.profile_rev} 마커 기반 재개 skip(오염 12명 사례 이후 style_axes
 * 단독 판정에서 전환), (4) remaining 응답 필드, (5) 필수 키 누락 시 실패 처리,
 * (6) persona 저장 + 감사 기록의 원자성을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PersonaProfileRegeneratorTest {

    @Mock private PersonaRepository personaRepo;
    @Mock private PersonaQuotaPlanner quotaPlanner;
    @Mock private PersonaProfileLlmClient llmClient;
    @Mock private PersonaActionLogRepository actionLogRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private PlatformTransactionManager transactionManager;

    private PersonaProfileRegenerator regenerator;

    @BeforeEach
    void setUp() {
        // TransactionTemplate이 실제 트랜잭션 리소스 없이도 동작하도록 최소 스텁만 둔다 —
        // getTransaction()이 유효한 TransactionStatus를 반환해야 commit/rollback 경로가 NPE 없이 돈다.
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        regenerator = new PersonaProfileRegenerator(
                personaRepo, quotaPlanner, llmClient, actionLogRepository, jdbcTemplate, transactionManager);
    }

    private static Persona persona(String id, Map<String, String> styleAxes) {
        return persona(id, styleAxes, null);
    }

    /**
     * voiceProfile을 명시적으로 지정하는 오버로드 — {@code profile_rev} 마커의 유무를
     * 테스트별로 통제하기 위함(마커 없이 style_axes만 채워진 것 = 오염 상태 재현).
     */
    private static Persona persona(String id, Map<String, String> styleAxes, Map<String, Object> voiceProfile) {
        return Persona.builder()
                .id(id).archetype("general").tier("REGULAR")
                .voiceProfile(voiceProfile).interests(Map.of()).biasProfile(Map.of())
                .circadian(List.of()).active(true).createdAt(Instant.now())
                .ageYears(30).gender("F").marital("SINGLE").jobType("CORP_LARGE")
                .styleAxes(styleAxes)
                .build();
    }

    /** style_axes + voice_profile.profile_rev 마커까지 갖춘, 정상적으로 완료된 페르소나. */
    /** 완료 상태: profile_rev 마커 + 계획과 일치하는 voice_type. 둘 다 맞아야 재개가 건너뛴다. */
    private static Persona donePersona(String id) {
        return persona(id, Map.of("speech", "BANMAL"),
                Map.of(PersonaProfileRegenerator.PROFILE_REV_KEY, PersonaProfileRegenerator.CURRENT_PROFILE_REV,
                        "voice_type", "NATEPAN"));
    }

    private static PersonaQuotaPlanner.IdentityAxes axes() {
        return new PersonaQuotaPlanner.IdentityAxes(30, "F", "SINGLE", null, false, "CORP_LARGE", "REGULAR",
                Map.of("speech", "BANMAL", "emoticon", "LOW", "profanity", "NONE"), "NATEPAN");
    }

    /** §4 응답 스키마 필수 키를 전부 채운 완전한 응답 — 성공 케이스 픽스처. */
    private static PersonaProfileLlmClient.ProfileResult success(String personaId) {
        return new PersonaProfileLlmClient.ProfileResult(completeResponse(personaId), null);
    }

    private static Map<String, Object> completeResponse(String personaId) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("general_style", "이 사람만의 문체 설명 - " + personaId);
        resp.put("lexicon", Map.of("signature_phrases", List.of("고유문구-" + personaId), "typing_habit", "짧게"));
        resp.put("writing_quirks", Map.of("spelling_level", "정확", "consistent_errors", List.of(), "mobile_typos", false));
        resp.put("hot_buttons", Map.of("triggers", List.of("t1"), "soft_spots", List.of("s1"), "upvote_when", "u"));
        resp.put("reactions", Map.of("agree", List.of("a1"), "disagree", List.of("d1"), "curious", List.of("c1")));
        resp.put("example_post_openers", List.of("오프너1", "오프너2", "오프너3"));
        resp.put("example_comments", List.of("댓글1", "댓글2", "댓글3", "댓글4", "댓글5"));
        resp.put("example_replies", List.of("대댓글1", "대댓글2", "대댓글3"));
        resp.put("post_style", "글 규칙");
        resp.put("comment_style", "댓글 규칙");
        resp.put("reply_style", "대댓글 규칙");
        return resp;
    }

    @Test
    void resumeSkipsPersonasThatAreFullyDone() {
        Persona done = donePersona("p1");
        Persona pending = persona("p2", null);
        when(personaRepo.findActiveIdsOrderById()).thenReturn(List.of("p1", "p2"));
        when(personaRepo.findByActiveTrue()).thenReturn(List.of(done, pending));
        when(quotaPlanner.plan(anyList(), anyLong())).thenReturn(Map.of("p1", axes(), "p2", axes()));
        when(llmClient.generatePersonaProfile(eq("p2"), any(), any(), any(), any(), anyList()))
                .thenReturn(success("p2"));

        Map<String, Object> result = regenerator.regenerate(1L, 10, null, false);

        assertThat(result.get("targetCount")).isEqualTo(1); // p1은 이미 profile_rev 마커 있음 → skip
        assertThat(result.get("succeeded")).isEqualTo(1);
        assertThat(result.get("remaining")).isEqualTo(0L);
        assertThat(result.get("haltedReason")).isNull();
        verify(llmClient, never()).generatePersonaProfile(eq("p1"), any(), any(), any(), any(), anyList());
    }

    @Test
    void resumeReprocessesPersonaWhoseVoiceTypeDiffersFromPlan() {
        // 마커는 최신인데 voice_type이 계획과 다른 경우. 이 축은 크롤 예시 풀 소스를 가르므로
        // 값만 갈아끼우면 옛 소스로 쓴 문체가 남는다 — 프로필째 다시 만들어야 한다(2026-09-06).
        Persona staleVoice = persona("p1", Map.of("speech", "BANMAL"),
                Map.of(PersonaProfileRegenerator.PROFILE_REV_KEY, PersonaProfileRegenerator.CURRENT_PROFILE_REV,
                        "voice_type", "BLIND"));
        when(personaRepo.findActiveIdsOrderById()).thenReturn(List.of("p1"));
        when(personaRepo.findByActiveTrue()).thenReturn(List.of(staleVoice));
        when(quotaPlanner.plan(anyList(), anyLong())).thenReturn(Map.of("p1", axes())); // 계획은 NATEPAN
        when(llmClient.generatePersonaProfile(eq("p1"), any(), any(), any(), any(), anyList()))
                .thenReturn(success("p1"));

        Map<String, Object> result = regenerator.regenerate(1L, 10, null, false);

        assertThat(result.get("targetCount")).isEqualTo(1);
        assertThat(result.get("succeeded")).isEqualTo(1);
        verify(llmClient).generatePersonaProfile(eq("p1"), any(), any(), any(), any(), anyList());
    }

    @Test
    void resumeReprocessesContaminatedPersonasWithStyleAxesButNoProfileRevMarker() {
        // 오염 상태 재현: style_axes는 채워졌지만 voice_profile에 profile_rev 마커가 없다.
        // 구버전은 style_axes IS NULL만으로 재개 대상을 골라 이런 페르소나를 영원히 건너뛰었다.
        Persona contaminated = persona("p1", Map.of("speech", "BANMAL")); // voiceProfile=null → 마커 없음
        when(personaRepo.findActiveIdsOrderById()).thenReturn(List.of("p1"));
        when(personaRepo.findByActiveTrue()).thenReturn(List.of(contaminated));
        when(quotaPlanner.plan(anyList(), anyLong())).thenReturn(Map.of("p1", axes()));
        when(llmClient.generatePersonaProfile(eq("p1"), any(), any(), any(), any(), anyList()))
                .thenReturn(success("p1"));

        Map<String, Object> result = regenerator.regenerate(1L, 10, null, false);

        assertThat(result.get("targetCount")).isEqualTo(1); // style_axes만으론 완료로 안 본다 → 재처리 대상
        assertThat(result.get("succeeded")).isEqualTo(1);
        assertThat(result.get("remaining")).isEqualTo(0L);
        verify(llmClient, times(1)).generatePersonaProfile(eq("p1"), any(), any(), any(), any(), anyList());
    }

    @Test
    void forceReprocessesPersonasThatAreFullyDone() {
        Persona done = donePersona("p1");
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
    void incompleteResponseIsTreatedAsFailureNotSilentPartialSuccess() {
        // 실제 오염 12명의 재현: general_style·writing_quirks 등 필수 키가 빠진 반쪽 응답을
        // "성공"으로 세면 축은 새 값인데 문체·시그니처는 옛 템플릿 그대로인 모순이 생긴다.
        Persona p1 = persona("p1", null);
        when(personaRepo.findActiveIdsOrderById()).thenReturn(List.of("p1"));
        when(personaRepo.findByActiveTrue()).thenReturn(List.of(p1));
        when(quotaPlanner.plan(anyList(), anyLong())).thenReturn(Map.of("p1", axes()));
        Map<String, Object> incomplete = Map.of("lexicon", Map.of("signature_phrases", List.of("유일문구")));
        when(llmClient.generatePersonaProfile(eq("p1"), any(), any(), any(), any(), anyList()))
                .thenReturn(new PersonaProfileLlmClient.ProfileResult(incomplete, null));

        Map<String, Object> result = regenerator.regenerate(1L, 10, null, false, 100);

        assertThat(result.get("succeeded")).isEqualTo(0);
        assertThat(result.get("skipped")).isEqualTo(1);
        assertThat(result.get("remaining")).isEqualTo(1L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failures = (List<Map<String, Object>>) result.get("failures");
        assertThat(failures).anySatisfy(f -> assertThat((String) f.get("reason")).startsWith("INCOMPLETE_PROFILE"));
        verify(personaRepo, never()).save(any());
        verify(actionLogRepository, never()).save(any());
        // 매 시도(최대 3회)마다 같은 불완전 응답이 오므로 결국 skip으로 끝난다.
        verify(llmClient, times(3)).generatePersonaProfile(eq("p1"), any(), any(), any(), any(), anyList());
    }

    @Test
    void auditSaveFailureRollsBackPersonaSaveAndDoesNotCountAsSuccess() {
        // 원자성 검증: persona_action_log 저장이 실패하면 personas 갱신도 함께 취소돼야 한다.
        // 예전엔 logAudit이 예외를 삼켜서 axes/voice_profile만 커밋되고 감사가 누락되는
        // 상태(오염 12명)가 가능했다 — 지금은 하나의 트랜잭션으로 묶여 있어 전체가 실패 처리된다.
        Persona p1 = persona("p1", null);
        when(personaRepo.findActiveIdsOrderById()).thenReturn(List.of("p1"));
        when(personaRepo.findByActiveTrue()).thenReturn(List.of(p1));
        when(quotaPlanner.plan(anyList(), anyLong())).thenReturn(Map.of("p1", axes()));
        when(llmClient.generatePersonaProfile(eq("p1"), any(), any(), any(), any(), anyList()))
                .thenReturn(success("p1"));
        doThrow(new RuntimeException("db blip")).when(actionLogRepository).save(any());

        Map<String, Object> result = regenerator.regenerate(1L, 10, null, false, 100);

        assertThat(result.get("succeeded")).isEqualTo(0);
        assertThat(result.get("skipped")).isEqualTo(1);
        assertThat(result.get("remaining")).isEqualTo(1L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failures = (List<Map<String, Object>>) result.get("failures");
        assertThat(failures).anySatisfy(f -> assertThat(f.get("reason")).isEqualTo("PERSIST_FAILED"));
        verify(personaRepo, times(1)).save(any());
        verify(actionLogRepository, times(1)).save(any());
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
    void remainingCountsOnlyPersonasNotYetFullyDone() {
        Persona done = donePersona("p1");
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
