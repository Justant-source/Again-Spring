package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.dto.SkeletonExtractRequest;
import com.againspring.aiuser.llm.dto.SkeletonExtractResponse;
import com.againspring.aiuser.llm.pool.LlmWorkerPool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.isNull;

/**
 * persona-diversity-v4 WP2 완료조건 — 스켈레톤 파서 테스트(픽스처 3건):
 * 정상, 필수 키 누락, sequence 3개 미만.
 */
class SkeletonExtractionServiceTest {

    @Test
    void validSkeletonJsonParsesIntoAllFields() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        when(pool.executeSyncTask(anyString(), isNull(), anyLong(), anyString())).thenReturn("""
                {"category":"WORK","author_role":"3년차 대리","counterpart_role":"직속 팀장",
                 "relationship":"직장 상사-부하","incident":"팀장이 내 기획안을 자기 이름으로 임원 보고함",
                 "sequence":["기획안을 냈다","팀장이 회의에서 자기 아이디어처럼 발표했다","임원이 팀장을 칭찬했다"],
                 "stakes":"고과·이직 여부","author_claim":"내가 낸 안이다","counterpart_claim":"팀 성과다",
                 "emotion":"억울함","gray_zone":"작성자도 사전에 공유 안 한 점","b_side_viable":false}
                """);
        SkeletonExtractionService service = new SkeletonExtractionService(pool);
        SkeletonExtractRequest req = new SkeletonExtractRequest();
        req.setSourceExampleId(123L);
        req.setCategory("WORK");
        req.setTitle("팀장이 내 기획 뺏음");
        req.setContent("장문의 크롤 원본 본문...");

        SkeletonExtractResponse resp = service.extract(req, "corr-1");

        assertTrue(resp.isOk());
        assertEquals("WORK", resp.getCategory());
        assertEquals(3, resp.getSequence().size());
        assertEquals(Boolean.FALSE, resp.getBSideViable());
        assertEquals(123L, resp.getSourceExampleId());
    }

    @Test
    void missingRequiredKeyYieldsOkFalseNotException() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        // gray_zone missing
        when(pool.executeSyncTask(anyString(), isNull(), anyLong(), anyString())).thenReturn("""
                {"category":"WORK","author_role":"대리","counterpart_role":"팀장",
                 "relationship":"직장 상사-부하","incident":"기획안 가로챔",
                 "sequence":["기획","보고","칭찬"],
                 "stakes":"고과","author_claim":"내 안","counterpart_claim":"팀 성과",
                 "emotion":"억울함","b_side_viable":false}
                """);
        SkeletonExtractionService service = new SkeletonExtractionService(pool);
        SkeletonExtractRequest req = new SkeletonExtractRequest();
        req.setCategory("WORK");
        req.setTitle("t");
        req.setContent("c");

        SkeletonExtractResponse resp = service.extract(req, "corr-2");

        assertFalse(resp.isOk());
        assertTrue(resp.getReason().contains("gray_zone"));
    }

    @Test
    void sequenceWithFewerThanThreeItemsYieldsOkFalse() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        when(pool.executeSyncTask(anyString(), isNull(), anyLong(), anyString())).thenReturn("""
                {"category":"COUPLE","author_role":"여친","counterpart_role":"남친",
                 "relationship":"연인","incident":"약속을 자주 어김",
                 "sequence":["약속을 잡았다","안 나타났다"],
                 "stakes":"이별 여부","author_claim":"믿음이 깨짐","counterpart_claim":"바빴다",
                 "emotion":"실망","gray_zone":"미리 말 안 한 점","b_side_viable":true}
                """);
        SkeletonExtractionService service = new SkeletonExtractionService(pool);
        SkeletonExtractRequest req = new SkeletonExtractRequest();
        req.setCategory("COUPLE");
        req.setTitle("t");
        req.setContent("c");

        SkeletonExtractResponse resp = service.extract(req, "corr-3");

        assertFalse(resp.isOk());
        assertTrue(resp.getReason().toLowerCase().contains("sequence"));
    }

    @Test
    void unparsableResponseYieldsOkFalse() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        when(pool.executeSyncTask(anyString(), isNull(), anyLong(), anyString())).thenReturn("죄송하지만 도와드릴 수 없습니다");
        SkeletonExtractionService service = new SkeletonExtractionService(pool);
        SkeletonExtractRequest req = new SkeletonExtractRequest();
        req.setCategory("WORK");
        req.setTitle("t");
        req.setContent("c");

        SkeletonExtractResponse resp = service.extract(req, "corr-4");

        assertFalse(resp.isOk());
    }

    @Test
    void blankContentYieldsOkFalseWithoutCallingPool() throws Exception {
        LlmWorkerPool pool = mock(LlmWorkerPool.class);
        SkeletonExtractionService service = new SkeletonExtractionService(pool);
        SkeletonExtractRequest req = new SkeletonExtractRequest();
        req.setCategory("WORK");
        req.setTitle("t");
        req.setContent("  ");

        SkeletonExtractResponse resp = service.extract(req, "corr-5");

        assertFalse(resp.isOk());
    }
}
