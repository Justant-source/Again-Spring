package com.againspring.aiuser.llm.controller;

import com.againspring.aiuser.llm.dto.GenResponse;
import com.againspring.aiuser.llm.dto.ProofreadRequest;
import com.againspring.aiuser.llm.pool.LlmWorkerPool;
import com.againspring.aiuser.llm.service.OutputSanitizer;
import com.againspring.aiuser.llm.service.PromptAssembler;
import com.againspring.aiuser.llm.service.SelfCritiqueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * POST /generate/proofread — 게시 직전 맞춤법 교정 엔드포인트 (2026-08-16 shortform-content-quality fix).
 * persona/voice가 없는 좁은 목적 호출이므로 실제 PromptAssembler/OutputSanitizer를 사용하고
 * LlmWorkerPool만 모킹한다.
 */
class GenerationControllerProofreadTest {

    private LlmWorkerPool pool;
    private GenerationController controller;

    @BeforeEach
    void setUp() {
        pool = mock(LlmWorkerPool.class);
        PromptAssembler promptAssembler = new PromptAssembler();
        promptAssembler.reload(); // jdbcTemplate 없음 → classpath 폴백
        OutputSanitizer outputSanitizer = new OutputSanitizer();
        SelfCritiqueService selfCritique = mock(SelfCritiqueService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        controller = new GenerationController(pool, promptAssembler, outputSanitizer, selfCritique, objectMapper);
    }

    private ProofreadRequest req(String body) {
        return ProofreadRequest.builder().body(body).correlationId("test-corr").timeoutMs(5000L).build();
    }

    @Test
    void bareJsonResponse_extractsCorrectedBody() throws Exception {
        when(pool.executeSyncTask(anyString(), any(), anyLong(), anyString(), any()))
            .thenReturn("{\"corrected_body\":\"이거 진짜 됐어\"}");

        ResponseEntity<GenResponse> resp = controller.proofreadPost(req("이거 진짜 됬어"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("이거 진짜 됐어", resp.getBody().getText());
    }

    @Test
    void codeFencedJsonResponse_extractsCorrectedBody() throws Exception {
        when(pool.executeSyncTask(anyString(), any(), anyLong(), anyString(), any()))
            .thenReturn("```json\n{\"corrected_body\":\"교정된 문장\"}\n```");

        ResponseEntity<GenResponse> resp = controller.proofreadPost(req("교정될 문장"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("교정된 문장", resp.getBody().getText());
    }

    @Test
    void noCorrectionNeeded_echoesOriginal() throws Exception {
        when(pool.executeSyncTask(anyString(), any(), anyLong(), anyString(), any()))
            .thenReturn("{\"corrected_body\":\"이미 맞는 문장이야\"}");

        ResponseEntity<GenResponse> resp = controller.proofreadPost(req("이미 맞는 문장이야"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("이미 맞는 문장이야", resp.getBody().getText());
    }

    @Test
    void malformedResponse_returnsServerErrorNotSilentPassthrough() throws Exception {
        // 실패 시 미교정 원문을 슬쩍 흘려보내지 않고 명시적으로 실패해야 한다 — 호출자(orchestrator)가
        // fail-closed로 처리할 신호. (LLM 안전 원칙: 오류/거절 감지 → ERROR → 미게시)
        when(pool.executeSyncTask(anyString(), any(), anyLong(), anyString(), any()))
            .thenReturn("교정할 수 없습니다. 이유를 설명드리자면...");

        ResponseEntity<GenResponse> resp = controller.proofreadPost(req("원문"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        assertNotNull(resp.getBody().getError());
    }

    @Test
    void emptyCorrectedBody_returnsServerError() throws Exception {
        when(pool.executeSyncTask(anyString(), any(), anyLong(), anyString(), any()))
            .thenReturn("{\"corrected_body\":\"\"}");

        ResponseEntity<GenResponse> resp = controller.proofreadPost(req("원문"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
    }
}
