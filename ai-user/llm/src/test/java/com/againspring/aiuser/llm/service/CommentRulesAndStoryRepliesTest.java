package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.dto.PairedPhase1Request;
import com.againspring.aiuser.llm.dto.PairedPhase1Response;
import com.againspring.aiuser.llm.dto.PairedPhase2Request;
import com.againspring.aiuser.llm.dto.PairedPhase2Response;
import com.againspring.aiuser.llm.dto.ThreadPlanRequest;
import com.againspring.aiuser.llm.dto.ThreadPlanResponse;
import com.againspring.aiuser.llm.pool.LlmWorkerPool;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 댓글 규칙·실제 댓글 참고·제목 참고·verdict_lean·작성자/상대방 대댓글 허용 동작. */
class CommentRulesAndStoryRepliesTest {

    // ── 프롬프트 ────────────────────────────────────────────────────────────

    @Test
    void planPromptCarriesCommentRulesAndReferences() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = service(pool);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(planJson());
        ThreadPlanRequest req = planRequest();
        req.setCommentStyleReference(List.of("ㅋㅋ 쓰니 어쩌라고", "헐 대박", " "));
        req.setSourceTitle("썸·연애: 남친이 또 약속 파토냄");
        req.setReconstructMode(true);
        req.setSourceContext(Map.of("incident", "약속 파토", "verdict_lean", "PARTNER"));

        service.createThreadPlan(req, "corr-rules");

        String prompt = capture(pool, StructuredOutputSchema.THREAD_PLAN);
        assertTrue(prompt.contains("COMMENT_RULES (hard)"));
        assertTrue(prompt.contains("COMMENT_STYLE_REFERENCE=[\"ㅋㅋ 쓰니 어쩌라고\",\"헐 대박\"]"), prompt);
        assertTrue(prompt.contains("COMMENT_STYLE_REFERENCE_RULE"));
        assertTrue(prompt.contains("TITLE_STYLE_REFERENCE=남친이 또 약속 파토냄"), "접두어 제거");
        assertFalse(prompt.contains("TITLE_STYLE_REFERENCE=썸"));
        assertTrue(prompt.contains("8~30 characters"));
        assertTrue(prompt.contains("VERDICT_LEAN=PARTNER"));
        assertTrue(prompt.contains("\"상대방 이해\""));
        assertTrue(prompt.contains("320만원"), "뭉갠 수치는 구체 숫자로");
        assertFalse(prompt.contains("{{"), "자리표시자 미치환");
        assertTrue(prompt.contains("AT MOST 1~2 comments"));
    }

    @Test
    void planPromptWithoutNewFieldsKeepsLegacyShape() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = service(pool);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(planJson());

        service.createThreadPlan(planRequest(), "corr-legacy");

        String prompt = capture(pool, StructuredOutputSchema.THREAD_PLAN);
        assertTrue(prompt.contains("COMMENT_RULES (hard)"), "규칙은 항상 포함");
        assertFalse(prompt.contains("COMMENT_STYLE_REFERENCE="));
        assertFalse(prompt.contains("TITLE_STYLE_REFERENCE="));
        assertFalse(prompt.contains("VERDICT_LEAN="));
    }

    @Test
    void pairedPromptsCarryRules() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = service(pool);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.PAIRED_PHASE1))).thenReturn(phase1Json(List.of(top("c1", "p1"), top("c2", "p2"))));
        PairedPhase1Request r1 = phase1Request();
        r1.setCommentStyleReference(List.of("쓰니 ㅇㅈ"));
        r1.setSourceTitle("직장: 팀장이 내 기획 뺏음");
        r1.setSourceContext(Map.of("verdict_lean", "AUTHOR"));
        service.createPairedPhase1(r1, "c-p1");
        String p1 = capture(pool, StructuredOutputSchema.PAIRED_PHASE1);
        assertTrue(p1.contains("COMMENT_RULES (hard)"));
        assertTrue(p1.contains("COMMENT_STYLE_REFERENCE=[\"쓰니 ㅇㅈ\"]"));
        assertTrue(p1.contains("TITLE_STYLE_REFERENCE=팀장이 내 기획 뺏음"));
        assertTrue(p1.contains("VERDICT_LEAN=AUTHOR"));
        assertTrue(p1.contains("8~30 characters"));
        assertFalse(p1.contains("Prefer Korean product terms"));

        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.PAIRED_PHASE2))).thenReturn(phase2Json(List.of(top("c1", "p1"))));
        PairedPhase2Request r2 = phase2Request();
        r2.setCommentStyleReference(List.of("ㅋㅋㅋ"));
        r2.setSourceContext(Map.of("incident", "x"));
        service.createPairedPhase2(r2, "c-p2");
        String p2 = capture(pool, StructuredOutputSchema.PAIRED_PHASE2);
        assertTrue(p2.contains("COMMENT_RULES (hard)"));
        assertTrue(p2.contains("COMMENT_STYLE_REFERENCE=[\"ㅋㅋㅋ\"]"));
        assertTrue(p2.contains("VERDICT_LEAN=SPLIT"), "골격에 verdict_lean이 없으면 SPLIT");
    }

    @Test
    void commentStyleReferenceCapsAtTwelve() {
        List<String> refs = new ArrayList<>();
        for (int i = 0; i < 20; i++) refs.add("댓글" + i);
        String block = StructuredGenerationService.commentStyleReferenceBlock(refs);
        assertTrue(block.contains("댓글11"));
        assertFalse(block.contains("댓글12"));
        assertEquals("", StructuredGenerationService.commentStyleReferenceBlock(List.of()));
        assertEquals("", StructuredGenerationService.commentStyleReferenceBlock(null));
    }

    @Test
    void stripBoardPrefixRemovesBoardLabel() {
        assertEquals("남친이 또 약속 파토냄", StructuredGenerationService.stripBoardPrefix("썸·연애: 남친이 또 약속 파토냄"));
        assertEquals("그냥 제목", StructuredGenerationService.stripBoardPrefix("그냥 제목"));
        assertNull(StructuredGenerationService.stripBoardPrefix("  "));
        assertNull(StructuredGenerationService.stripBoardPrefix(null));
        assertEquals("", StructuredGenerationService.titleStyleReferenceBlock("   "));
    }

    @Test
    void verdictLeanDefaultsToSplitOnBadValue() {
        assertTrue(StructuredGenerationService.verdictLeanBlock(Map.of("verdict_lean", "???")).contains("VERDICT_LEAN=SPLIT"));
        assertTrue(StructuredGenerationService.verdictLeanBlock(Map.of("verdict_lean", "partner")).contains("VERDICT_LEAN=PARTNER"));
        assertEquals("", StructuredGenerationService.verdictLeanBlock(null));
    }

    // ── 작성자/상대방 대댓글 파서 ────────────────────────────────────────────

    @Test
    void planAllowsAuthorReplyButDropsAuthorTopLevel() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = service(pool);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(planJsonWith(List.of(
                top("c1", "p1"), top("c2", "p2"), top("c3", "p3"),
                top("c4", "p6"),                    // 작성자 최상위 → 버림
                reply("c5", "c4", "p6"),            // 버려진 부모의 대댓글 → 같이 버림
                reply("c6", "c1", "p6"))));         // 작성자 대댓글 → 허용
        ThreadPlanRequest req = planRequest();
        req.setAuthor(Map.of("personaId", "p6"));
        req.setMinTopLevel(1);
        req.setMinItems(1);

        ThreadPlanResponse res = service.createThreadPlan(req, "c");

        assertEquals(List.of("c1", "c2", "c3", "c6"), res.getItems().stream().map(ThreadPlanResponse.Item::getRef).toList());
        ThreadPlanResponse.Item r = res.getItems().get(3);
        assertEquals("p6", r.getPersonaId());
        assertEquals("c1", r.getParentRef());
    }

    @Test
    void planCapsAuthorRepliesAtTwo() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = service(pool);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(planJsonWith(List.of(
                top("c1", "p1"), top("c2", "p2"), top("c3", "p3"),
                reply("c4", "c1", "p6"), reply("c5", "c2", "p6"), reply("c6", "c3", "p6"))));
        ThreadPlanRequest req = planRequest();
        req.setAuthor(Map.of("personaId", "p6"));
        req.setMinTopLevel(1);
        req.setMinItems(1);

        ThreadPlanResponse res = service.createThreadPlan(req, "c");

        assertEquals(2, res.getItems().stream().filter(i -> "p6".equals(i.getPersonaId())).count());
        assertTrue(res.getItems().stream().noneMatch(i -> "c6".equals(i.getRef())));
    }

    @Test
    void phase1AllowsAuthorReplyButNotPartner() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = service(pool);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.PAIRED_PHASE1))).thenReturn(phase1Json(List.of(
                top("c1", "p1"), top("c2", "p2"), top("c3", "author1"), reply("c4", "c1", "author1"))));

        PairedPhase1Response res = service.createPairedPhase1(phase1Request(), "c");

        assertEquals(List.of("c1", "c2", "c4"), res.getItems().stream().map(ThreadPlanResponse.Item::getRef).toList());
    }

    @Test
    void phase2AllowsAuthorTwoAndPartnerOneRepliesOnly() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = service(pool);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.PAIRED_PHASE2))).thenReturn(phase2Json(List.of(
                top("c1", "p1"), top("c2", "p2"), top("c3", "p3"),
                top("c4", "partner1"),               // 상대방 최상위 → 버림
                reply("c5", "c1", "author1"), reply("c6", "c2", "author1"), reply("c7", "c3", "author1"),
                reply("c8", "c1", "partner1"), reply("c9", "c2", "partner1"))));
        PairedPhase2Request req = phase2Request();
        req.setMaxReplies(10);

        PairedPhase2Response res = service.createPairedPhase2(req, "c");

        List<String> refs = res.getItems().stream().map(ThreadPlanResponse.Item::getRef).toList();
        assertEquals(List.of("c1", "c2", "c3", "c5", "c6", "c8"), refs);
    }

    @Test
    void storySideIdHelpers() {
        PairedPhase2Request req = phase2Request();
        assertEquals(java.util.Set.of("author1"), StructuredGenerationService.authorPersonaIdsForPhase2(req));
        assertEquals(java.util.Set.of("partner1"), StructuredGenerationService.partnerPersonaIdsForPhase2(req));
        assertEquals(java.util.Set.of("a"), StructuredGenerationService.storySidePersonaIds(Map.of("personaId", "a")));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String top(String ref, String persona) { return item(ref, null, persona); }
    private static String reply(String ref, String parent, String persona) { return item(ref, parent, persona); }

    private static String item(String ref, String parent, String persona) {
        return "{\"ref\":\"" + ref + "\",\"parentRef\":" + (parent == null ? "null" : "\"" + parent + "\"")
                + ",\"personaId\":\"" + persona + "\",\"body\":\"한국어 댓글 " + ref + "\",\"stance\":\"중립\",\"priority\":1}";
    }

    private static String planJson() {
        return planJsonWith(List.of(top("c1", "p1"), top("c2", "p2"), top("c3", "p3")));
    }

    private static String planJsonWith(List<String> items) {
        return "{\"post\":{\"title\":\"한국어 제목입니다\",\"body\":\"한국어 게시글 본문입니다. 충분히 자연스러운 내용입니다.\"},\"comments\":["
                + String.join(",", items) + "]}";
    }

    private static String phase1Json(List<String> items) {
        return "{\"post\":{\"title\":\"한국어 제목입니다\",\"body\":\"한국어 게시글 본문입니다. 충분히 자연스러운 내용입니다.\",\"hook_emotion\":\"anger\"},\"comments\":["
                + String.join(",", items) + "]}";
    }

    private static String phase2Json(List<String> items) {
        return "{\"partner_post\":null,\"comments\":[" + String.join(",", items) + "]}";
    }

    private static String capture(LlmWorkerPool pool, StructuredOutputSchema schema) throws Exception {
        ArgumentCaptor<String> c = ArgumentCaptor.forClass(String.class);
        verify(pool, atLeastOnce()).executeProviderTask(c.capture(), anyString(), anyLong(), anyString(),
                eq(LlmProvider.CODEX), eq(schema));
        return c.getValue();
    }

    private static ThreadPlanRequest planRequest() {
        ThreadPlanRequest r = new ThreadPlanRequest();
        r.setKind(ThreadPlanRequest.Kind.AI_POST);
        r.setProvider("CODEX");
        r.setPersonas(List.of(persona("p1"), persona("p2"), persona("p3"), persona("p4"), persona("p5"), persona("p6")));
        r.setMinTopLevel(1);
        r.setMinItems(1);
        return r;
    }

    private static PairedPhase1Request phase1Request() {
        PairedPhase1Request r = new PairedPhase1Request();
        r.setProvider("CODEX");
        r.setCategory("COUPLE");
        r.setAuthor(Map.of("personaId", "author1"));
        r.setPersonas(List.of(persona("p1"), persona("p2"), persona("author1")));
        r.setMaxTopLevel(4);
        r.setMinTopLevel(1);
        r.setMinItems(1);
        return r;
    }

    private static PairedPhase2Request phase2Request() {
        PairedPhase2Request r = new PairedPhase2Request();
        r.setProvider("CODEX");
        r.setCategory("COUPLE");
        r.setIncludePartnerPost(false);
        PairedPhase2Request.AuthorPost a = new PairedPhase2Request.AuthorPost();
        a.setPersonaId("author1");
        a.setTitle("제목");
        a.setBody("어제 일곱 시에 만나기로 해놓고 두 시간 늦게 왔어");
        r.setAuthorPost(a);
        r.setPartner(Map.of("personaId", "partner1"));
        r.setPersonas(List.of(persona("p1"), persona("p2"), persona("p3"), persona("author1"), persona("partner1")));
        r.setPublishedTopLevelComments(List.of());
        r.setMaxTopLevel(14);
        r.setMinTopLevel(1);
        r.setMinItems(1);
        return r;
    }

    private static ThreadPlanRequest.Persona persona(String id) {
        ThreadPlanRequest.Persona p = new ThreadPlanRequest.Persona();
        p.setPersonaId(id);
        p.setNickname("nick-" + id);
        p.setFormality("casual");
        p.setVoiceProfile(Map.of("formality", "casual", "voice_type", "NATEPAN"));
        return p;
    }

    private static StructuredGenerationService service(LlmWorkerPool pool) {
        PromptAssembler pa = new PromptAssembler();
        pa.loadGuides();
        SelfCritiqueService critique = new SelfCritiqueService(null, null, null);
        ReflectionTestUtils.setField(critique, "enabled", false);
        StructuredGenerationService s = new StructuredGenerationService(pool, critique,
                mock(LlmParseFailureSampler.class), mock(StructuredSchemaCatalog.class),
                mock(com.againspring.aiuser.llm.notification.ParseFailureRateLimiter.class),
                mock(com.againspring.aiuser.llm.notification.StructuredGenerationParseFailTelegramNotifier.class),
                new com.againspring.aiuser.llm.config.LlmProperties(), pa);
        ReflectionTestUtils.setField(s, "codexTerra", "gpt-5.6-terra");
        ReflectionTestUtils.setField(s, "codexLuna", "gpt-5.6-luna");
        ReflectionTestUtils.setField(s, "claudeDefault", "claude-haiku-4-5-20251001");
        ReflectionTestUtils.setField(s, "claudePostModel", "claude-sonnet-5-5");
        ReflectionTestUtils.setField(s, "structuredPromptModeEnabled", false);
        return s;
    }

    @org.junit.jupiter.api.Test
    void shortReactionCommentsPassValidation() {
        // 실제 커뮤니티의 한 글자·자모 반응 댓글이 응답 전체를 실패시키면 안 된다.
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> StructuredGenerationService.validTextForTest("헐", "comment.body", 1, 1000));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> StructuredGenerationService.validTextForTest("ㅋㅋㅋㅋ", "comment.body", 1, 1000));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> StructuredGenerationService.validTextForTest("ㅇㅈ ㄹㅇ", "comment.body", 1, 1000));
    }

    @org.junit.jupiter.api.Test
    void longEnglishTextStillRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> StructuredGenerationService.validTextForTest(
                "This is a long english sentence that should never be posted as a comment body", "comment.body", 1, 1000));
    }
}
