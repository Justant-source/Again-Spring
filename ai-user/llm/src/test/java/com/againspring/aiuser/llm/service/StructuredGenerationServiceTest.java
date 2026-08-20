package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.dto.ThreadPlanRequest;
import com.againspring.aiuser.llm.dto.ThreadPlanResponse;
import com.againspring.aiuser.llm.pool.LlmWorkerPool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StructuredGenerationServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void rejectsTitleLongerThanFortyChars() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool, disabledCritique());
        String longTitle = "남친이 오늘도 퇴근 통화 시작하자마자 내일 장 전망부터 꺼냄 앱 지웠다더니";
        assertTrue(longTitle.length() > 40);
        String json = planJsonWithTitleBody(longTitle, "어제 통화했는데 또 주식 얘기만 하더라 나는 그냥 듣는 기계 된 느낌");
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(json);

        assertThrows(StructuredGenerationException.class, () -> service.createThreadPlan(planRequest(), "corr-title-len"));
    }

    @Test
    void rejectsIdenticalTitleAndBody() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool, disabledCritique());
        String same = "남친이 또 회사 스트레스로 나한테 욱했음";
        String json = planJsonWithTitleBody(same, same);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(json);

        assertThrows(StructuredGenerationException.class, () -> service.createThreadPlan(planRequest(), "corr-title-eq"));
    }

    @Test
    void planPromptIncludesCaptureSplitRules() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool, disabledCritique());
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(validPlanJson("한국어 댓글입니다"));

        service.createThreadPlan(planRequest(), "corr-prompt-split");

        var promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(pool).executeProviderTask(promptCaptor.capture(), anyString(), anyLong(), anyString(),
                eq(LlmProvider.CODEX), eq(StructuredOutputSchema.THREAD_PLAN));
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("capture_split_after_lines"), "split field in schema");
        assertTrue(prompt.contains("more than 8 non-empty lines"), "8-block threshold");
    }

    @Test
    void acceptsValidCaptureSplitForLongBody() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool, disabledCritique());
        String body = longBodyWithBlocks(15);
        String json = planJsonWithTitleBodyAndSplit("한국어 제목입니다", body, 8, "한국어 댓글입니다");
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(json);

        ThreadPlanResponse response = service.createThreadPlan(planRequest(), "corr-split-ok");
        assertEquals(List.of(8), response.getPost().getCaptureSplitAfterLines());
        assertEquals(8, response.getPost().getCaptureSplitAfterLine());
    }

    @Test
    void demotesCaptureSplitWhenBodyIsShort() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool, disabledCritique());
        String body = "한국어 게시글 본문입니다. 충분히 자연스러운 내용입니다.";
        String json = planJsonWithTitleBodyAndSplit("한국어 제목입니다", body, 3, "한국어 댓글입니다");
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(json);

        ThreadPlanResponse response = service.createThreadPlan(planRequest(), "corr-split-short");
        assertEquals(null, response.getPost().getCaptureSplitAfterLine());
    }

    @Test
    void demotesCaptureSplitOutOfRange() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool, disabledCritique());
        String body = longBodyWithBlocks(15);
        String json = planJsonWithTitleBodyAndSplit("한국어 제목입니다", body, 15, "한국어 댓글입니다");
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(json);

        ThreadPlanResponse response = service.createThreadPlan(planRequest(), "corr-split-oor");
        assertEquals(null, response.getPost().getCaptureSplitAfterLine());
    }

    @Test
    void sanitizeCaptureSplitHelpers() {
        String longBody = longBodyWithBlocks(14);
        assertEquals(14, StructuredGenerationService.countNonEmptyBlocks(longBody));
        assertEquals(8, StructuredGenerationService.sanitizeCaptureSplit(longBody, 8));
        assertEquals(null, StructuredGenerationService.sanitizeCaptureSplit(longBody, 14));
        assertEquals(null, StructuredGenerationService.sanitizeCaptureSplit("한 줄만", 1));
    }

    @Test
    void planPromptIncludesTitleBodySeparationRules() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool, disabledCritique());
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(validPlanJson("한국어 댓글입니다"));

        service.createThreadPlan(planRequest(), "corr-prompt-rules");

        var promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(pool).executeProviderTask(promptCaptor.capture(), anyString(), anyLong(), anyString(),
                eq(LlmProvider.CODEX), eq(StructuredOutputSchema.THREAD_PLAN));
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("12~40 characters"), "title length rule");
        assertTrue(prompt.contains("never set title equal to body"), "title≠body rule");
        assertTrue(prompt.contains("promo_title"), "promo_title in schema");
        assertTrue(prompt.contains("hook_emotion"), "hook_emotion in schema");
        assertTrue(prompt.contains("MASTER SNS") || prompt.contains("scroll-stop"), "promo as SNS hook");
        assertTrue(prompt.contains("shock|anger|tension|sad|hype"), "hook_emotion enum");
        assertTrue(prompt.contains("informational Korean plaza") || prompt.contains("plaza headline"),
                "title is plaza informational, not SNS rewrite");
        assertTrue(prompt.contains("STORY_PERSONA_RULE"), "author must not own bystander comments");
    }

    @Test
    void dropsAuthorPersonaCommentsFromThreadPlan() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool, disabledCritique());
        // Sparse plan: c1 uses author p1, c2..c6 use other personas — enough after drop for min=1.
        String json = sparsePlanJson(6);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(json);

        ThreadPlanRequest request = planRequest();
        request.setAuthor(Map.of("personaId", "p1", "nickname", "글쓴이"));
        request.setMinTopLevel(1);
        request.setMinItems(1);

        ThreadPlanResponse response = service.createThreadPlan(request, "corr-no-author-comment");

        assertTrue(response.getItems().stream().noneMatch(i -> "p1".equals(i.getPersonaId())));
        assertTrue(response.getItems().size() >= 5);
    }

    @Test
    void sanitizePromoTitleHelpers() {
        String title = "도와줬더니 모든 걸 저한테";
        // Independent SNS hook (different from title) must be kept
        String snappy = "도와줬더니\n배신당함";
        assertEquals(snappy, StructuredGenerationService.sanitizePromoTitle(title, snappy));
        // Same-as-title with newlines still ok
        String mirrored = "도와줬더니\n모든 걸\n저한테";
        assertEquals(mirrored, StructuredGenerationService.sanitizePromoTitle(title, mirrored));
        // Blank → wrap title fallback
        String wrapped = StructuredGenerationService.sanitizePromoTitle(title, "   ");
        assertEquals(title.replaceAll("\\s+", ""), wrapped.replace("\n", "").replaceAll("\\s+", ""));
        for (String line : wrapped.split("\n")) {
            assertTrue(line.length() <= 10, line);
        }
        // Overlong flattened → fallback
        String tooLong = "가".repeat(81);
        String demoted = StructuredGenerationService.sanitizePromoTitle(title, tooLong);
        assertEquals(wrapped, demoted);
        // Line >20 → fallback
        String longLine = "가나다라마바사아자차카타파하가나다가나다라";
        assertTrue(longLine.length() > 20);
        assertEquals(wrapped, StructuredGenerationService.sanitizePromoTitle(title, longLine));
    }

    @Test
    void sanitizeHookEmotionHelpers() {
        assertEquals("shock", StructuredGenerationService.sanitizeHookEmotion("shock"));
        assertEquals("anger", StructuredGenerationService.sanitizeHookEmotion("ANGER"));
        assertEquals("tension", StructuredGenerationService.sanitizeHookEmotion(null));
        assertEquals("tension", StructuredGenerationService.sanitizeHookEmotion(""));
        assertEquals("tension", StructuredGenerationService.sanitizeHookEmotion("joy"));
        assertEquals("hype", StructuredGenerationService.sanitizeHookEmotion("hype"));
        assertEquals("sad", StructuredGenerationService.sanitizeHookEmotion("sad"));
    }

    @Test
    void rejectIdenticalTitleBodyHelperIgnoresWhitespace() {
        assertThrows(StructuredGenerationException.class,
                () -> StructuredGenerationService.rejectIdenticalTitleBody("남친이 욱함", "남친이   욱함"));
        StructuredGenerationService.rejectIdenticalTitleBody("남친이 욱함", "어제 또 회사 일로 나한테 소리질렀음");
    }

    @Test
    void passesSharedThreadPlanSchemaToProviderTask() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool, disabledCritique());
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(validPlanJson("한국어 댓글입니다"));

        service.createThreadPlan(planRequest(), "corr-2");

        verify(pool).executeProviderTask(anyString(), eq("gpt-5.6-terra"), anyLong(), eq("corr-2"),
                eq(LlmProvider.CODEX), eq(StructuredOutputSchema.THREAD_PLAN));
    }

    @Test
    void convertsLiteralBackslashNInStructuredPostBody() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool, disabledCritique());
        // JSON에 리터럴 백슬래시+n을 넣으려면 JSON 텍스트에 \\n → Java 문자열에는 \\\\n
        String json = validPlanJsonWithPostBody("첫 줄입니다.\\\\n둘째 줄입니다. 충분히 자연스러운 본문입니다.");
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(json);

        var response = service.createThreadPlan(planRequest(), "corr-nl");

        assertNotNull(response.getPost());
        assertFalse(response.getPost().getBody().contains("\\n"),
                "리터럴 백슬래시+n이 남아있으면 안 됨: " + response.getPost().getBody());
        assertTrue(response.getPost().getBody().contains("\n"), "실제 개행으로 변환돼야 함");
    }

    @Test
    void personaVoiceProfileRoundTripsAsStructuredMapWithoutLosingNicknameOrFormality() throws Exception {
        ThreadPlanRequest.Persona persona = new ThreadPlanRequest.Persona();
        persona.setPersonaId("u_author");
        persona.setNickname("봄이네");
        persona.setFormality("casual");
        Map<String, Object> voice = new LinkedHashMap<>();
        voice.put("formality", "casual");
        voice.put("voice_type", "NATEPAN");
        voice.put("age", "30대");
        voice.put("gender", "F");
        voice.put("general_style", "담백한 반말");
        persona.setVoiceProfile(voice);

        String encoded = JSON.writeValueAsString(persona);
        assertTrue(encoded.contains("\"nickname\":\"봄이네\""));
        assertTrue(encoded.contains("\"formality\":\"casual\""));
        assertTrue(encoded.contains("\"voice_type\":\"NATEPAN\""));
        assertFalse(encoded.contains("formality=casual"), "must not be Map.toString()");

        ThreadPlanRequest.Persona decoded = JSON.readValue(encoded, ThreadPlanRequest.Persona.class);
        assertEquals("봄이네", decoded.getNickname());
        assertEquals("casual", decoded.getFormality());
        assertInstanceOf(Map.class, decoded.getVoiceProfile());
        assertEquals("NATEPAN", decoded.getVoiceProfile().get("voice_type"));
        assertEquals("casual", decoded.getVoiceProfile().get("formality"));

        // Orchestrator-style envelope: voiceProfile as nested JSON object
        String requestJson = """
                {"kind":"AI_POST","provider":"CODEX","personas":[{
                  "personaId":"u_author","nickname":"봄이네","formality":"polite",
                  "voiceProfile":{"formality":"polite","voice_type":"BLIND","age":"40대"}
                }]}
                """;
        ThreadPlanRequest req = JSON.readValue(requestJson, ThreadPlanRequest.class);
        assertEquals("봄이네", req.getPersonas().get(0).getNickname());
        assertEquals("polite", req.getPersonas().get(0).getFormality());
        assertEquals("BLIND", req.getPersonas().get(0).getVoiceProfile().get("voice_type"));
        assertEquals("polite", StructuredGenerationService.resolveFormality(req.getPersonas().get(0)));
    }

    @Test
    void planPromptSerializesVoiceProfileAsJsonObjectNotMapToString() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool, disabledCritique());
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(validPlanJson("한국어 댓글입니다"));

        ThreadPlanRequest request = planRequest();
        ThreadPlanRequest.Persona author = request.getPersonas().get(0);
        author.setNickname("닉네임실명");
        author.setFormality("casual");
        author.setVoiceProfile(Map.of("formality", "casual", "voice_type", "NATEPAN", "age", "20대"));

        service.createThreadPlan(request, "corr-voice");

        var promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(pool).executeProviderTask(promptCaptor.capture(), anyString(), anyLong(), anyString(),
                eq(LlmProvider.CODEX), eq(StructuredOutputSchema.THREAD_PLAN));
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("PERSONAS="));
        assertTrue(prompt.contains("\"nickname\":\"닉네임실명\""));
        assertTrue(prompt.contains("\"formality\":\"casual\""));
        assertTrue(prompt.contains("\"voice_type\":\"NATEPAN\""));
        assertFalse(prompt.contains("voiceProfile={"), "Map.toString() must not appear in prompt");

        int personasIdx = prompt.indexOf("PERSONAS=");
        String personasJson = prompt.substring(personasIdx + "PERSONAS=".length(), prompt.indexOf("\nLIMITS="));
        List<Map<String, Object>> personas = JSON.readValue(personasJson, new TypeReference<>() {});
        assertEquals("닉네임실명", personas.get(0).get("nickname"));
        assertInstanceOf(Map.class, personas.get(0).get("voiceProfile"));
        @SuppressWarnings("unchecked")
        Map<String, Object> vp = (Map<String, Object>) personas.get(0).get("voiceProfile");
        assertEquals("NATEPAN", vp.get("voice_type"));
    }

    @Test
    void planPromptIncludesSourceGroundingFields() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool, disabledCritique());
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(validPlanJson("한국어 댓글입니다"));

        ThreadPlanRequest request = planRequest();
        request.setSourceContext(Map.of("source", "natepan", "register", "NATEPAN"));
        request.setReconstructMode(true);
        request.setSourceExampleId(42L);
        request.setSourceBody("원본 사연 본문입니다. 친구에게 돈을 빌려줬는데 연락이 두절됐어요.");
        request.setDynamicExamples("문체 앵커 예시");
        request.setRecentOutputs(List.of("최근 글 요약"));
        request.setAuthor(Map.of("personaId", "p1", "nickname", "작성자닉"));
        // Author p1 is stripped from comments; keep floors low so remaining cast still passes.
        request.setMinTopLevel(1);
        request.setMinItems(1);

        service.createThreadPlan(request, "corr-ground");

        var promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(pool).executeProviderTask(promptCaptor.capture(), anyString(), anyLong(), anyString(),
                eq(LlmProvider.CODEX), eq(StructuredOutputSchema.THREAD_PLAN));
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("SOURCE_CONTEXT="));
        assertTrue(prompt.contains("RECONSTRUCT_MODE=true"));
        assertTrue(prompt.contains("SOURCE_BODY="));
        assertTrue(prompt.contains("STYLE_EXAMPLES="));
        assertTrue(prompt.contains("RECENT_OUTPUTS="));
        assertTrue(prompt.contains("AUTHOR="));
        assertTrue(prompt.contains("원본 사연 본문"));
    }

    @Test
    void createThreadPlanInvokesSelfCritiqueOnPostAndTopLevelComments() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        SelfCritiqueService critique = mock(SelfCritiqueService.class);
        when(critique.critiqueAndRefine(anyString(), anyString(), anyString(), anyString(), any(), any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
        StructuredGenerationService service = configuredService(pool, critique);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(validPlanJson("한국어 댓글입니다"));

        ThreadPlanResponse response = service.createThreadPlan(planRequest(), "corr-sc");
        assertNotNull(response.getPost());

        // post once + 6 top-level comments (replies skipped)
        verify(critique, times(1)).critiqueAndRefine(
                eq(response.getPost().getBody()), eq("post"), anyString(), eq("corr-sc"),
                eq("CLI"), any(), eq("gpt-5.6-terra"), any());
        verify(critique, times(6)).critiqueAndRefine(
                anyString(), eq("comment"), anyString(), startsWith("corr-sc-"),
                eq("CLI"), any(), eq("gpt-5.6-terra"), any());
        verify(critique, never()).critiqueAndRefine(
                anyString(), eq("reply"), anyString(), anyString(), any(), any(), any(), any());
    }

    @Test
    void resolveFormalityPrefersTopLevelThenVoiceProfile() {
        ThreadPlanRequest.Persona p = new ThreadPlanRequest.Persona();
        p.setVoiceProfile(Map.of("formality", "polite"));
        assertEquals("polite", StructuredGenerationService.resolveFormality(p));
        p.setFormality("casual");
        assertEquals("casual", StructuredGenerationService.resolveFormality(p));
    }

    @Test
    void parsePlanRejectsSparsePlanWhenMinsUnspecifiedLegacyFloor() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool, disabledCritique());
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(sparsePlanJson(1));

        ThreadPlanRequest request = planRequest(); // minTopLevel/minItems null → legacy 6/12
        assertThrows(StructuredGenerationException.class, () -> service.createThreadPlan(request, "corr-legacy-floor"));
    }

    @Test
    void parsePlanAcceptsSparsePlanWhenExplicitMinOne() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool, disabledCritique());
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(sparsePlanJson(1));

        ThreadPlanRequest request = planRequest();
        request.setMinTopLevel(1);
        request.setMinItems(1);

        ThreadPlanResponse response = service.createThreadPlan(request, "corr-min-one");
        assertNotNull(response.getPost());
        assertEquals(1, response.getItems().size());
    }

    @Test
    void parsePlanHonorsExplicitMinsAboveOne() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool, disabledCritique());
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(sparsePlanJson(2));

        ThreadPlanRequest request = planRequest();
        request.setMinTopLevel(3);
        request.setMinItems(3);

        assertThrows(StructuredGenerationException.class, () -> service.createThreadPlan(request, "corr-min-three"));
    }

    private static StructuredGenerationService configuredService(LlmWorkerPool pool, SelfCritiqueService critique) {
        LlmParseFailureSampler sampler = org.mockito.Mockito.mock(LlmParseFailureSampler.class);
        StructuredSchemaCatalog schemaCatalog = org.mockito.Mockito.mock(StructuredSchemaCatalog.class);
        com.againspring.aiuser.llm.notification.ParseFailureRateLimiter rateLimiter =
            org.mockito.Mockito.mock(com.againspring.aiuser.llm.notification.ParseFailureRateLimiter.class);
        com.againspring.aiuser.llm.notification.StructuredGenerationParseFailTelegramNotifier notifier =
            org.mockito.Mockito.mock(com.againspring.aiuser.llm.notification.StructuredGenerationParseFailTelegramNotifier.class);
        com.againspring.aiuser.llm.config.LlmProperties props = new com.againspring.aiuser.llm.config.LlmProperties();
        StructuredGenerationService service = new StructuredGenerationService(pool, critique, sampler, schemaCatalog, rateLimiter, notifier, props);
        ReflectionTestUtils.setField(service, "codexTerra", "gpt-5.6-terra");
        ReflectionTestUtils.setField(service, "codexLuna", "gpt-5.6-luna");
        ReflectionTestUtils.setField(service, "claudeDefault", "claude-haiku-4-5-20251001");
        ReflectionTestUtils.setField(service, "claudePostModel", "claude-sonnet-5");
        ReflectionTestUtils.setField(service, "structuredPromptModeEnabled", false);
        return service;
    }

    private static SelfCritiqueService disabledCritique() {
        SelfCritiqueService svc = new SelfCritiqueService(null, null, null);
        ReflectionTestUtils.setField(svc, "enabled", false);
        return svc;
    }

    private static ThreadPlanRequest planRequest() {
        ThreadPlanRequest request = new ThreadPlanRequest();
        request.setKind(ThreadPlanRequest.Kind.AI_POST);
        request.setProvider("CODEX");
        request.setPersonas(List.of(persona("p1"), persona("p2"), persona("p3"), persona("p4"), persona("p5"), persona("p6")));
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

    private static String validPlanJson(String firstBody) {
        return validPlanJsonWithPostBody("한국어 게시글 본문입니다. 충분히 자연스러운 내용입니다.", firstBody);
    }

    private static String validPlanJsonWithPostBody(String postBody) {
        return validPlanJsonWithPostBody(postBody, "한국어 댓글입니다");
    }

    private static String validPlanJsonWithPostBody(String postBody, String firstBody) {
        return planJsonWithTitleBody("한국어 제목입니다", postBody, firstBody);
    }

    private static String planJsonWithTitleBody(String title, String postBody) {
        return planJsonWithTitleBody(title, postBody, "한국어 댓글입니다");
    }

    private static String planJsonWithTitleBody(String title, String postBody, String firstBody) {
        return planJsonWithTitleBodyAndSplit(title, postBody, null, firstBody);
    }

    private static String planJsonWithTitleBodyAndSplit(String title, String postBody, Integer split, String firstBody) {
        List<String> items = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            String body = i == 1 ? firstBody : "한국어 댓글 " + i + "입니다";
            String parent = i <= 6 ? "null" : "\"c" + (i - 6) + "\"";
            String persona = "p" + ((i - 1) % 6 + 1);
            items.add("{\"ref\":\"c" + i + "\",\"parentRef\":" + parent + ",\"personaId\":\"" + persona + "\",\"body\":\"" + body + "\"}");
        }
        String splitJson = split == null ? "null" : ("[" + split + "]");
        String escapedBody = postBody.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        String promo = StructuredGenerationService.wrapPromoLines(title);
        String escapedPromo = promo == null ? "" : promo.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"post\":{\"title\":\"" + title + "\",\"body\":\"" + escapedBody
                + "\",\"promo_title\":\"" + escapedPromo
                + "\",\"hook_emotion\":\"tension\",\"capture_split_after_lines\":" + splitJson + "},\"comments\":["
                + String.join(",", items) + "]}";
    }

    private static String longBodyWithBlocks(int blocks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= blocks; i++) {
            if (i > 1) sb.append('\n');
            sb.append("한국어 사연 문장 ").append(i).append(" 번째입니다.");
        }
        return sb.toString();
    }

    /** Sparse plan with {@code topCount} top-level comments only (no replies). */
    private static String sparsePlanJson(int topCount) {
        List<String> items = new ArrayList<>();
        for (int i = 1; i <= topCount; i++) {
            String persona = "p" + ((i - 1) % 6 + 1);
            items.add("{\"ref\":\"c" + i + "\",\"parentRef\":null,\"personaId\":\"" + persona
                    + "\",\"body\":\"한국어 댓글 " + i + "입니다\"}");
        }
        return "{\"post\":{\"title\":\"한국어 제목입니다\",\"body\":\"한국어 게시글 본문입니다. 충분히 자연스러운 내용입니다.\"},\"comments\":["
                + String.join(",", items) + "]}";
    }

    @Test
    void rejectsCommentBodyThatIsThreadPlanSchemaLeak() {
        String leak = """
            {
              post: null,
              comments: [
                {
                  ref: c1,
                  parentRef: null,
                  personaId: 4a7305dac5ed4160b927998c3b0864f6,
                  body: "남자들 심리 참 모르겠지만 뭐라도 노력하려는 시도는 좋은 거 맞음",
            """;
        assertTrue(StructuredGenerationService.looksLikeStructuredSchemaLeak(leak));
        assertFalse(StructuredGenerationService.looksLikeStructuredSchemaLeak(
                "갑자기 달라진 남편 적응이 안 되네요 ㅠㅠ"));
    }

}
