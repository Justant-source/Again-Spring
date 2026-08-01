package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.dto.ThreadPlanRequest;
import com.againspring.aiuser.llm.pool.LlmWorkerPool;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StructuredGenerationServiceTest {

    @Test
    void rejectsEnglishRefusalInsideJsonEnvelopeAtItemLevel() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool);
        ThreadPlanRequest request = planRequest();
        String unsafeJson = validPlanJson("I can't help with this request");
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(unsafeJson);

        assertThrows(StructuredGenerationException.class, () -> service.createThreadPlan(request, "corr-1"));
        verify(pool, times(2)).executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN));
    }

    @Test
    void passesSharedThreadPlanSchemaToProviderTask() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool);
        when(pool.executeProviderTask(anyString(), anyString(), anyLong(), anyString(), eq(LlmProvider.CODEX),
                eq(StructuredOutputSchema.THREAD_PLAN))).thenReturn(validPlanJson("한국어 댓글입니다"));

        service.createThreadPlan(planRequest(), "corr-2");

        verify(pool).executeProviderTask(anyString(), eq("gpt-5.6-terra"), anyLong(), eq("corr-2"),
                eq(LlmProvider.CODEX), eq(StructuredOutputSchema.THREAD_PLAN));
    }

    @Test
    void convertsLiteralBackslashNInStructuredPostBody() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        StructuredGenerationService service = configuredService(pool);
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

    private static StructuredGenerationService configuredService(LlmWorkerPool pool) {
        StructuredGenerationService service = new StructuredGenerationService(pool);
        ReflectionTestUtils.setField(service, "codexTerra", "gpt-5.6-terra");
        ReflectionTestUtils.setField(service, "codexLuna", "gpt-5.6-luna");
        ReflectionTestUtils.setField(service, "claudeDefault", "claude-haiku-4-5-20251001");
        ReflectionTestUtils.setField(service, "claudePostModel", "claude-sonnet-4-6");
        return service;
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
        return persona;
    }

    private static String validPlanJson(String firstBody) {
        return validPlanJsonWithPostBody("한국어 게시글 본문입니다. 충분히 자연스러운 내용입니다.", firstBody);
    }

    private static String validPlanJsonWithPostBody(String postBody) {
        return validPlanJsonWithPostBody(postBody, "한국어 댓글입니다");
    }

    private static String validPlanJsonWithPostBody(String postBody, String firstBody) {
        List<String> items = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            String body = i == 1 ? firstBody : "한국어 댓글 " + i + "입니다";
            String parent = i <= 6 ? "null" : "\"c" + (i - 6) + "\"";
            String persona = "p" + ((i - 1) % 6 + 1);
            items.add("{\"ref\":\"c" + i + "\",\"parentRef\":" + parent + ",\"personaId\":\"" + persona + "\",\"body\":\"" + body + "\"}");
        }
        return "{\"post\":{\"title\":\"한국어 제목입니다\",\"body\":\"" + postBody + "\"},\"comments\":[" + String.join(",", items) + "]}";
    }
}
