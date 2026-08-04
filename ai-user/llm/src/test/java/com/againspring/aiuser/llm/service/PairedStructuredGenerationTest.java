package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.dto.PairedPhase1Request;
import com.againspring.aiuser.llm.dto.PairedPhase1Response;
import com.againspring.aiuser.llm.dto.PairedPhase2Request;
import com.againspring.aiuser.llm.dto.PairedPhase2Response;
import com.againspring.aiuser.llm.dto.ThreadPlanRequest;
import com.againspring.aiuser.llm.pool.LlmWorkerPool;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PairedStructuredGenerationTest {

    @Test
    void phase1HappyPathParsesAuthorPostAndSmallCommentSet() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configured(pool);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.PAIRED_PHASE1))).thenReturn(phase1Json(3));

        PairedPhase1Response response = service.createPairedPhase1(phase1Request(), "corr-p1");

        assertEquals(StructuredOutputSchema.WORKLOAD_PAIRED_PHASE1, response.getWorkload());
        assertNotNull(response.getPost());
        assertEquals("남친이 또 약속 파토냄", response.getPost().getTitle());
        assertTrue(response.getPost().getBody().length() >= 20);
        assertEquals(3, response.getItems().size());
        assertNull(response.getItems().get(0).getParentRef());
        verify(pool).executeProviderTask(anyString(), eq("gpt-5.6-terra"), anyLong(), eq("corr-p1"),
                eq(LlmProvider.CODEX), eq(StructuredOutputSchema.PAIRED_PHASE1));
    }

    @Test
    void phase1PromptForbidsAssumingPartnerAlreadyWrote() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configured(pool);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.PAIRED_PHASE1))).thenReturn(phase1Json(2));

        service.createPairedPhase1(phase1Request(), "corr-p1-prompt");

        var promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(pool).executeProviderTask(promptCaptor.capture(), anyString(), anyLong(), anyString(),
                eq(LlmProvider.CODEX), eq(StructuredOutputSchema.PAIRED_PHASE1));
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("PAIRED_PHASE1"));
        assertTrue(prompt.contains("has NOT written yet") || prompt.contains("상대방(B) has NOT"));
        assertTrue(prompt.contains("12~40 characters"));
        assertTrue(prompt.contains("작성자"));
    }

    @Test
    void phase1RejectsIdenticalTitleBody() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configured(pool);
        String same = "남친이 또 회사 스트레스로 나한테 욱했음";
        String json = "{\"post\":{\"title\":\"" + same + "\",\"body\":\"" + same
                + "\",\"promo_title\":null,\"capture_split_after_lines\":null},\"comments\":["
                + comment("c1", null, "p1") + "," + comment("c2", null, "p2") + "]}";
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.PAIRED_PHASE1))).thenReturn(json);

        assertThrows(StructuredGenerationException.class,
                () -> service.createPairedPhase1(phase1Request(), "corr-p1-eq"));
    }

    @Test
    void phase2HappyPathParsesPartnerBodyAndComments() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configured(pool);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.PAIRED_PHASE2))).thenReturn(phase2Json(true, 6));

        PairedPhase2Response response = service.createPairedPhase2(phase2Request(true), "corr-p2");

        assertEquals(StructuredOutputSchema.WORKLOAD_PAIRED_PHASE2, response.getWorkload());
        assertNotNull(response.getPartnerPost());
        assertTrue(response.getPartnerPost().getBody().contains("야근"));
        assertEquals(6, response.getItems().size());
        verify(pool).executeProviderTask(anyString(), eq("gpt-5.6-terra"), anyLong(), anyString(),
                eq(LlmProvider.CODEX), eq(StructuredOutputSchema.PAIRED_PHASE2));
    }

    @Test
    void phase2CommentOnlyContinuationRequiresNullPartnerPost() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configured(pool);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.PAIRED_PHASE2))).thenReturn(phase2Json(false, 4));

        PairedPhase2Request req = phase2Request(false);
        req.setMinTopLevel(1);
        req.setMinItems(1);
        PairedPhase2Response response = service.createPairedPhase2(req, "corr-p2-mb");

        assertNull(response.getPartnerPost());
        assertEquals(4, response.getItems().size());

        var promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(pool).executeProviderTask(promptCaptor.capture(), anyString(), anyLong(), anyString(),
                eq(LlmProvider.CODEX), eq(StructuredOutputSchema.PAIRED_PHASE2));
        assertTrue(promptCaptor.getValue().contains("INCLUDE_PARTNER_POST=false"));
        assertTrue(promptCaptor.getValue().contains("comment-only"));
    }

    @Test
    void phase2RejectsPartnerPostWhenCommentOnly() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configured(pool);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.PAIRED_PHASE2))).thenReturn(phase2Json(true, 4));

        PairedPhase2Request req = phase2Request(false);
        req.setMinTopLevel(1);
        req.setMinItems(1);
        assertThrows(StructuredGenerationException.class,
                () -> service.createPairedPhase2(req, "corr-p2-bad"));
    }

    @Test
    void phase2PromptIncludesPublishedCommentsAndEmptyOk() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configured(pool);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.PAIRED_PHASE2))).thenReturn(phase2Json(true, 4));

        PairedPhase2Request req = phase2Request(true);
        req.setMinTopLevel(1);
        req.setMinItems(1);
        PairedPhase2Request.PublishedComment pub = new PairedPhase2Request.PublishedComment();
        pub.setBody("나도 비슷한 일 있었음 진짜 답답하더라");
        pub.setNickname("이웃1");
        req.setPublishedTopLevelComments(List.of(pub));

        service.createPairedPhase2(req, "corr-p2-ctx");

        var promptCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(pool).executeProviderTask(promptCaptor.capture(), anyString(), anyLong(), anyString(),
                eq(LlmProvider.CODEX), eq(StructuredOutputSchema.PAIRED_PHASE2));
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("PUBLISHED_TOP_LEVEL_COMMENTS"));
        assertTrue(prompt.contains("나도 비슷한 일"));
        assertTrue(prompt.contains("empty"));
        assertTrue(prompt.contains("작성자"));
    }

    @Test
    void phase2RejectsMoreThanEightPublishedComments() {
        StructuredGenerationService service = configured(mock(LlmWorkerPool.class));
        PairedPhase2Request req = phase2Request(true);
        List<PairedPhase2Request.PublishedComment> tooMany = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            PairedPhase2Request.PublishedComment c = new PairedPhase2Request.PublishedComment();
            c.setBody("공개 댓글 " + i + " 본문입니다");
            tooMany.add(c);
        }
        req.setPublishedTopLevelComments(tooMany);
        assertThrows(IllegalArgumentException.class, () -> service.createPairedPhase2(req, "corr-p2-8"));
    }

    private static StructuredGenerationService configured(LlmWorkerPool pool) {
        StructuredGenerationService service = new StructuredGenerationService(pool, disabledCritique());
        ReflectionTestUtils.setField(service, "codexTerra", "gpt-5.6-terra");
        ReflectionTestUtils.setField(service, "codexLuna", "gpt-5.6-luna");
        ReflectionTestUtils.setField(service, "claudeDefault", "claude-haiku-4-5-20251001");
        ReflectionTestUtils.setField(service, "claudePostModel", "claude-sonnet-4-6");
        return service;
    }

    private static SelfCritiqueService disabledCritique() {
        SelfCritiqueService svc = new SelfCritiqueService(null, null, null);
        ReflectionTestUtils.setField(svc, "enabled", false);
        return svc;
    }

    private static PairedPhase1Request phase1Request() {
        PairedPhase1Request req = new PairedPhase1Request();
        req.setProvider("CODEX");
        req.setCategory("COUPLE");
        req.setTopicHint("약속 파토");
        req.setAuthor(Map.of("personaId", "author1", "nickname", "작성자닉"));
        req.setPersonas(List.of(persona("p1"), persona("p2"), persona("p3"), persona("p4")));
        req.setMaxTopLevel(4);
        req.setMinTopLevel(2);
        req.setMinItems(2);
        return req;
    }

    private static PairedPhase2Request phase2Request(boolean includePartner) {
        PairedPhase2Request req = new PairedPhase2Request();
        req.setProvider("CODEX");
        req.setCategory("COUPLE");
        req.setIncludePartnerPost(includePartner);
        PairedPhase2Request.AuthorPost author = new PairedPhase2Request.AuthorPost();
        author.setTitle("남친이 또 약속 파토냄");
        author.setBody("어제 일곱 시에 만나기로 해놓고 두 시간 늦게 왔어 밥도 못 먹고 기다렸음");
        req.setAuthorPost(author);
        req.setPartner(Map.of("personaId", "partner1", "nickname", "상대닉"));
        req.setPersonas(List.of(persona("p1"), persona("p2"), persona("p3"), persona("p4"), persona("p5"), persona("p6")));
        req.setPublishedTopLevelComments(List.of());
        req.setMaxTopLevel(14);
        req.setMinTopLevel(4);
        req.setMinItems(4);
        return req;
    }

    private static ThreadPlanRequest.Persona persona(String id) {
        ThreadPlanRequest.Persona p = new ThreadPlanRequest.Persona();
        p.setPersonaId(id);
        p.setNickname("nick-" + id);
        p.setFormality("casual");
        p.setVoiceProfile(Map.of("formality", "casual", "voice_type", "NATEPAN"));
        return p;
    }

    private static String phase1Json(int topCount) {
        String title = "남친이 또 약속 파토냄";
        String body = "어제 일곱 시에 만나기로 해놓고 두 시간 늦게 왔어 밥도 못 먹고 기다렸음 진짜 미치겠음";
        String promo = StructuredGenerationService.wrapPromoLines(title);
        List<String> items = new ArrayList<>();
        for (int i = 1; i <= topCount; i++) {
            items.add(comment("c" + i, null, "p" + ((i - 1) % 4 + 1)));
        }
        return "{\"post\":{\"title\":\"" + title + "\",\"body\":\"" + body
                + "\",\"promo_title\":\"" + promo.replace("\n", "\\n")
                + "\",\"capture_split_after_lines\":null},\"comments\":["
                + String.join(",", items) + "]}";
    }

    private static String phase2Json(boolean withPartner, int topCount) {
        List<String> items = new ArrayList<>();
        for (int i = 1; i <= topCount; i++) {
            items.add(comment("c" + i, null, "p" + ((i - 1) % 6 + 1)));
        }
        String partner = withPartner
                ? "{\"body\":\"평일에 야근이 많아서 주말엔 좀 쉬어야 다음 주를 버텨 나도 미안한 마음은 있음\"}"
                : "null";
        return "{\"partner_post\":" + partner + ",\"comments\":[" + String.join(",", items) + "]}";
    }

    private static String comment(String ref, String parent, String persona) {
        String parentJson = parent == null ? "null" : "\"" + parent + "\"";
        return "{\"ref\":\"" + ref + "\",\"parentRef\":" + parentJson + ",\"personaId\":\"" + persona
                + "\",\"body\":\"한국어 댓글 " + ref + " 본문입니다\",\"stance\":null,\"priority\":1}";
    }
}
