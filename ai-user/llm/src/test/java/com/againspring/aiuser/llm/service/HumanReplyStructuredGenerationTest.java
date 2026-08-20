package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.dto.HumanReplyBatchRequest;
import com.againspring.aiuser.llm.dto.HumanReplyBatchResponse;
import com.againspring.aiuser.llm.dto.ThreadPlanRequest;
import com.againspring.aiuser.llm.pool.LlmWorkerPool;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HumanReplyStructuredGenerationTest {

    @Test
    void acceptsZeroToThreeRepliesPerHumanComment() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configured(pool);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.HUMAN_REPLIES))).thenReturn("""
                {"replies":[
                  {"humanCommentId":1,"personaId":"p1","body":"첫 번째 자연스러운 한국어 답글입니다."},
                  {"humanCommentId":1,"personaId":"p2","body":"두 번째 자연스러운 한국어 답글입니다."},
                  {"humanCommentId":2,"personaId":"p1","body":"다른 댓글에 대한 짧은 답입니다요."}
                ]}
                """);

        HumanReplyBatchResponse response = service.createHumanReplies(replyRequest(), "corr-hr");

        assertEquals(3, response.getReplies().size());
        assertEquals(2, response.getReplies().stream().filter(r -> r.getHumanCommentId() == 1L).count());
    }

    @Test
    void allowsEmptyRepliesArrayAsNoResponse() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configured(pool);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.HUMAN_REPLIES))).thenReturn("{\"replies\":[]}");

        HumanReplyBatchResponse response = service.createHumanReplies(replyRequest(), "corr-empty");
        assertTrue(response.getReplies().isEmpty());
    }

    @Test
    void rejectsMoreThanThreeRepliesForSameHumanComment() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configured(pool);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.HUMAN_REPLIES))).thenReturn("""
                {"replies":[
                  {"humanCommentId":1,"personaId":"p1","body":"첫 번째 자연스러운 한국어 답글입니다."},
                  {"humanCommentId":1,"personaId":"p2","body":"두 번째 자연스러운 한국어 답글입니다."},
                  {"humanCommentId":1,"personaId":"p3","body":"세 번째 자연스러운 한국어 답글입니다."},
                  {"humanCommentId":1,"personaId":"p4","body":"네 번째는 거부되어야 하는 답글입니다."}
                ]}
                """);

        assertThrows(StructuredGenerationException.class,
                () -> service.createHumanReplies(replyRequest(), "corr-too-many"));
    }

    @Test
    void rejectsPersonaOutsideCandidateResponders() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configured(pool);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.HUMAN_REPLIES))).thenReturn("""
                {"replies":[
                  {"humanCommentId":1,"personaId":"ghost","body":"후보에 없는 페르소나 답글입니다요."}
                ]}
                """);

        assertThrows(StructuredGenerationException.class,
                () -> service.createHumanReplies(replyRequest(), "corr-ghost"));
    }

    @Test
    void promptMentionsCandidateRespondersAndZeroToThree() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configured(pool);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.HUMAN_REPLIES))).thenAnswer(inv -> {
            String prompt = inv.getArgument(0);
            assertTrue(prompt.contains("candidateResponders"));
            assertTrue(prompt.contains("0 to 3") || prompt.contains("0~3") || prompt.contains("0 to 3 replies"));
            assertTrue(!prompt.contains("\"responder\""), "legacy single responder must not be required");
            return "{\"replies\":[]}";
        });

        service.createHumanReplies(replyRequest(), "corr-prompt");
    }

    private static StructuredGenerationService configured(LlmWorkerPool pool) {
        LlmParseFailureSampler sampler = org.mockito.Mockito.mock(LlmParseFailureSampler.class);
        StructuredGenerationService service = new StructuredGenerationService(pool, disabledCritique(), sampler);
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

    private static HumanReplyBatchRequest replyRequest() {
        HumanReplyBatchRequest req = new HumanReplyBatchRequest();
        req.setProvider("CODEX");
        HumanReplyBatchRequest.Item a = item(1L, List.of("p1", "p2", "p3", "p4"));
        HumanReplyBatchRequest.Item b = item(2L, List.of("p1", "p2"));
        req.setItems(List.of(a, b));
        return req;
    }

    private static HumanReplyBatchRequest.Item item(long humanId, List<String> personaIds) {
        HumanReplyBatchRequest.Item item = new HumanReplyBatchRequest.Item();
        item.setPostId("post-1");
        item.setHumanCommentId(humanId);
        item.setHumanBody("사람이 남긴 댓글 본문입니다. 충분히 길어요.");
        item.setPostTitle("사연");
        item.setPostBody("사연 본문");
        item.setCandidateResponders(personaIds.stream().map(HumanReplyStructuredGenerationTest::persona).toList());
        return item;
    }

    private static ThreadPlanRequest.Persona persona(String id) {
        ThreadPlanRequest.Persona p = new ThreadPlanRequest.Persona();
        p.setPersonaId(id);
        p.setNickname("nick-" + id);
        p.setFormality("casual");
        p.setVoiceProfile(Map.of("formality", "casual", "voice_type", "NATEPAN"));
        return p;
    }
}
