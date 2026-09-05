package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.dto.ThreadPlanRequest;
import com.againspring.aiuser.llm.dto.ThreadPlanResponse;
import com.againspring.aiuser.llm.notification.ParseFailureRateLimiter;
import com.againspring.aiuser.llm.notification.StructuredGenerationParseFailTelegramNotifier;
import com.againspring.aiuser.llm.pool.LlmWorkerPool;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * persona-diversity-v4 순응도 보강(2026-09) — {@code StructuredGenerationService}의 축 준수
 * 결정론적 안전망 테스트. emoticon·linebreak만 다룬다(profanity·humor·stance는 의도적으로
 * 미구현 — 클래스 본문 주석 참고, AGENTS.md 절대 규칙 #3).
 */
class StructuredGenerationServiceAxisComplianceTest {

    private static final String CARD_EMOTICON_HIGH =
        "[페르소나] 닉네임=테스트 · 30세 여\n"
        + "[말투] 아래 문체 지시는 라벨이 아니라 명령이다 — 전부 실제 문장에 반영할 것:\n"
        + "- emoticon=HIGH: 문단마다 ㅋㅋ·ㅠㅠ 같은 표현을 실제로 넣는다\n"
        + "- linebreak=CHOPPED: 한두 문장마다 줄을 바꾼다";

    // ── 순수 함수 단위 테스트 ────────────────────────────────────────────

    @Test
    void parseStyleAxesFromCard_extractsAxisValuePairs() {
        Map<String, String> axes = StructuredGenerationService.parseStyleAxesFromCard(CARD_EMOTICON_HIGH);
        assertEquals("HIGH", axes.get("emoticon"));
        assertEquals("CHOPPED", axes.get("linebreak"));
    }

    @Test
    void parseStyleAxesFromCard_blankOrNullReturnsEmpty() {
        assertTrue(StructuredGenerationService.parseStyleAxesFromCard(null).isEmpty());
        assertTrue(StructuredGenerationService.parseStyleAxesFromCard("").isEmpty());
        assertTrue(StructuredGenerationService.parseStyleAxesFromCard("설명 텍스트만 있고 태그 없음").isEmpty());
    }

    @Test
    void axisViolations_emoticonHighButAbsent() {
        List<String> v = StructuredGenerationService.axisViolations(
            "그냥 오늘 회사에서 있었던 일 얘기하려고 씀", Map.of("emoticon", "HIGH"));
        assertTrue(v.contains("emoticon_high_but_absent"));
    }

    @Test
    void axisViolations_emoticonNoneButPresent() {
        List<String> v = StructuredGenerationService.axisViolations(
            "진짜 어이없었음 ㅋㅋㅋ", Map.of("emoticon", "NONE"));
        assertTrue(v.contains("emoticon_none_but_present"));
    }

    @Test
    void axisViolations_emoticonHighAndPresent_noViolation() {
        List<String> v = StructuredGenerationService.axisViolations(
            "진짜 어이없었음 ㅋㅋㅋ 완전 황당하네 ㅠㅠ", Map.of("emoticon", "HIGH"));
        assertFalse(v.contains("emoticon_high_but_absent"));
    }

    @Test
    void axisViolations_emoticonLow_neverFlagged() {
        // LOW는 "한두 번" 허용 범위라 결정론적으로 판정하지 않는다 — 오탐 방지.
        List<String> v1 = StructuredGenerationService.axisViolations("이모티콘 전혀 없는 글", Map.of("emoticon", "LOW"));
        List<String> v2 = StructuredGenerationService.axisViolations("ㅋㅋㅋㅋㅋㅋ", Map.of("emoticon", "LOW"));
        assertTrue(v1.isEmpty());
        assertTrue(v2.isEmpty());
    }

    @Test
    void axisViolations_linebreakWallButChopped() {
        String choppedBody = "오늘 진짜 있었던 일 말할게\n아침부터 팀장이 갑자기 불러서\n혼났음 진짜 어이없어서 눈물 남";
        List<String> v = StructuredGenerationService.axisViolations(choppedBody, Map.of("linebreak", "WALL"));
        assertTrue(v.contains("linebreak_wall_but_chopped"));
    }

    @Test
    void axisViolations_linebreakChoppedButWall() {
        String wallBody = "오늘 진짜 있었던 일 말할게 아침부터 팀장이 갑자기 불러서 혼났음 진짜 어이없어서 눈물 날 뻔함";
        List<String> v = StructuredGenerationService.axisViolations(wallBody, Map.of("linebreak", "CHOPPED"));
        assertTrue(v.contains("linebreak_chopped_but_wall"));
    }

    @Test
    void axisViolations_linebreakSkippedForShortText() {
        // 초단문 댓글은 잘게/통짜를 구분할 여지가 없어 검사 자체를 생략한다.
        List<String> v = StructuredGenerationService.axisViolations("ㅋㅋ 완전 공감", Map.of("linebreak", "WALL"));
        assertTrue(v.isEmpty());
    }

    @Test
    void axisViolations_profanityAxisNeverChecked() {
        // profanity는 의도적으로 미구현(AGENTS.md 절대 규칙 #3 — 욕설 denylist 금지).
        // NONE인데 욕설이 있어도, HEAVY인데 욕설이 없어도 위반으로 잡지 않는다.
        List<String> v1 = StructuredGenerationService.axisViolations(
            "아 진짜 개짜증나네 시발", Map.of("profanity", "NONE"));
        List<String> v2 = StructuredGenerationService.axisViolations(
            "그냥 오늘 있었던 일을 담담하게 적어봄", Map.of("profanity", "HEAVY"));
        assertTrue(v1.isEmpty());
        assertTrue(v2.isEmpty());
    }

    @Test
    void axisViolations_noAxesOrBlankBody_returnsEmpty() {
        assertTrue(StructuredGenerationService.axisViolations("본문", Map.of()).isEmpty());
        assertTrue(StructuredGenerationService.axisViolations("", Map.of("emoticon", "HIGH")).isEmpty());
        assertTrue(StructuredGenerationService.axisViolations(null, Map.of("emoticon", "HIGH")).isEmpty());
    }

    @Test
    void buildAxisRetryPrompt_containsFixInstructionsAndOriginalDraft() {
        String prompt = StructuredGenerationService.buildAxisRetryPrompt(
            "원본 초안", List.of("emoticon_high_but_absent", "linebreak_wall_but_chopped"), "post");
        assertTrue(prompt.contains("문단마다 최소 1개씩 실제로 넣어라"));
        assertTrue(prompt.contains("줄바꿈을 없애라"));
        assertTrue(prompt.contains("원본 초안"));
        assertFalse(prompt.contains("PERSONAS="), "스키마·페르소나 목록 재첨부 금지");
    }

    // ── 배선 통합 테스트 (dark launch 플래그) ──────────────────────────────

    @Test
    void enforceAxisCompliance_disabledByDefault_neverCallsPool() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool);
        // axisComplianceEnabled 기본값(false) 유지 — 명시적으로 세팅하지 않음.

        ThreadPlanRequest request = planRequestWithCard(CARD_EMOTICON_HIGH);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN)))
            .thenReturn(planJsonWithPostBody("이모티콘 하나도 없는 담담한 본문입니다"));

        service.createThreadPlan(request, "corr-axis-off");

        verify(pool, never()).executeSyncTask(anyString(), anyString(), anyLong(), anyString(), any());
    }

    @Test
    void enforceAxisCompliance_enabledAndViolated_retriesOnceAndUsesImprovedBody() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool);
        ReflectionTestUtils.setField(service, "axisComplianceEnabled", true);

        ThreadPlanRequest request = planRequestWithCard(CARD_EMOTICON_HIGH);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN)))
            .thenReturn(planJsonWithPostBody("이모티콘 하나도 없는 담담한 본문입니다"));
        when(pool.executeSyncTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CLAUDE)))
            .thenReturn("이모티콘 넣어서 다시 씀 ㅋㅋㅋ 완전 웃김 ㅠㅠ");

        ThreadPlanResponse response = service.createThreadPlan(request, "corr-axis-on");

        assertEquals("이모티콘 넣어서 다시 씀 ㅋㅋㅋ 완전 웃김 ㅠㅠ", response.getPost().getBody());
        verify(pool, times(1)).executeSyncTask(anyString(), anyString(), anyLong(),
            eq("corr-axis-on-axis-retry"), eq(LlmProvider.CLAUDE));
    }

    @Test
    void enforceAxisCompliance_retryDoesNotImprove_keepsOriginal() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool);
        ReflectionTestUtils.setField(service, "axisComplianceEnabled", true);

        ThreadPlanRequest request = planRequestWithCard(CARD_EMOTICON_HIGH);
        String original = "이모티콘 하나도 없는 담담한 본문입니다";
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN)))
            .thenReturn(planJsonWithPostBody(original));
        // 재작성도 여전히 이모티콘이 없음 — 개선 실패
        when(pool.executeSyncTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CLAUDE)))
            .thenReturn("여전히 이모티콘 없는 본문");

        ThreadPlanResponse response = service.createThreadPlan(request, "corr-axis-noimprove");

        assertEquals(original, response.getPost().getBody(), "개선 실패 시 원본을 그대로 유지");
    }

    @Test
    void enforceAxisCompliance_noViolation_neverCallsPool() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool);
        ReflectionTestUtils.setField(service, "axisComplianceEnabled", true);

        ThreadPlanRequest request = planRequestWithCard(CARD_EMOTICON_HIGH);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN)))
            .thenReturn(planJsonWithPostBody("이미 이모티콘 잘 넣었음 ㅋㅋㅋ 진짜 웃김 ㅠㅠ"));

        service.createThreadPlan(request, "corr-axis-compliant");

        verify(pool, never()).executeSyncTask(anyString(), anyString(), anyLong(), anyString(), any());
    }

    // ── 테스트 헬퍼 ──────────────────────────────────────────────────────

    private static StructuredGenerationService configuredService(LlmWorkerPool pool) {
        SelfCritiqueService critique = new SelfCritiqueService(null, null, null);
        ReflectionTestUtils.setField(critique, "enabled", false);

        LlmParseFailureSampler sampler = mock(LlmParseFailureSampler.class);
        StructuredSchemaCatalog schemaCatalog = mock(StructuredSchemaCatalog.class);
        ParseFailureRateLimiter rateLimiter = mock(ParseFailureRateLimiter.class);
        StructuredGenerationParseFailTelegramNotifier notifier = mock(StructuredGenerationParseFailTelegramNotifier.class);
        com.againspring.aiuser.llm.config.LlmProperties props = new com.againspring.aiuser.llm.config.LlmProperties();
        PromptAssembler promptAssembler = new PromptAssembler();
        promptAssembler.loadGuides();

        StructuredGenerationService service = new StructuredGenerationService(
            pool, critique, sampler, schemaCatalog, rateLimiter, notifier, props, promptAssembler);
        ReflectionTestUtils.setField(service, "codexTerra", "gpt-5.6-terra");
        ReflectionTestUtils.setField(service, "codexLuna", "gpt-5.6-luna");
        ReflectionTestUtils.setField(service, "claudeDefault", "claude-haiku-4-5-20251001");
        ReflectionTestUtils.setField(service, "claudePostModel", "claude-sonnet-5");
        ReflectionTestUtils.setField(service, "structuredPromptModeEnabled", false);
        return service;
    }

    private static ThreadPlanRequest planRequestWithCard(String card) {
        ThreadPlanRequest request = new ThreadPlanRequest();
        request.setKind(ThreadPlanRequest.Kind.AI_POST);
        request.setProvider("CODEX");
        ThreadPlanRequest.Persona author = persona("p1");
        author.setPersonaCard(card);
        request.setPersonas(List.of(author, persona("p2"), persona("p3"), persona("p4"), persona("p5"), persona("p6")));
        return request;
    }

    private static ThreadPlanRequest.Persona persona(String id) {
        ThreadPlanRequest.Persona persona = new ThreadPlanRequest.Persona();
        persona.setPersonaId(id);
        persona.setNickname("nick-" + id);
        persona.setFormality("casual");
        persona.setVoiceProfile(Map.of("formality", "casual", "voice_type", "NATEPAN"));
        return persona;
    }

    /** 계약(legacy floor): top-level ≥6, 전체 items ≥12 — 원본 StructuredGenerationServiceTest와 동일 패턴. */
    private static String planJsonWithPostBody(String postBody) {
        // personaId "p1"은 planRequestWithCard()에서 CARD_EMOTICON_HIGH(emoticon=HIGH)를 달고 오고,
        // 라운드로빈(i=1,7)으로 댓글에도 배정된다 — 댓글 본문에 이모티콘을 넣어 두지 않으면
        // enforceAxisCompliance_noViolation_neverCallsPool처럼 "글은 순응·댓글은 위반"이 섞여
        // pool.executeSyncTask가 의도치 않게 호출된다. 모든 댓글에 이모티콘을 붙여 그 경로를 차단한다
        // (짧은 문장이라 linebreak 검사는 LINEBREAK_CHECK_MIN_LEN 미만이라 애초에 대상이 아님).
        List<String> items = new java.util.ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            String parent = i <= 6 ? "null" : "\"c" + (i - 6) + "\"";
            String persona = "p" + ((i - 1) % 6 + 1);
            items.add("{\"ref\":\"c" + i + "\",\"parentRef\":" + parent + ",\"personaId\":\"" + persona
                    + "\",\"body\":\"한국어 댓글 " + i + "입니다 ㅋㅋ\"}");
        }
        String escapedBody = postBody.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"post\":{\"title\":\"한국어 제목입니다\",\"body\":\"" + escapedBody
                + "\",\"promo_title\":\"한국어 훅\",\"hook_emotion\":\"tension\",\"capture_split_after_lines\":null},\"comments\":["
                + String.join(",", items) + "]}";
    }
}
