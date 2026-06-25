package com.againspring.aiuser.llm.controller;

import com.againspring.aiuser.llm.dto.PostRewriteRequest;
import com.againspring.aiuser.llm.dto.PostRewriteResponse;
import com.againspring.aiuser.llm.exception.LlmCapacityException;
import com.againspring.aiuser.llm.exception.LlmTimeoutException;
import com.againspring.aiuser.llm.service.PostRewriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/internal/rewrite")
@RequiredArgsConstructor
public class InternalRewriteController {

    private final PostRewriteService postRewriteService;

    @PostMapping("/post")
    public ResponseEntity<PostRewriteResponse> rewritePost(@RequestBody PostRewriteRequest req) {
        String corrId = corrId(req.getCorrelationId());
        long start = System.currentTimeMillis();
        try {
            PostRewriteResponse response = postRewriteService.rewrite(req, corrId, start);
            return ResponseEntity.ok(response);
        } catch (LlmCapacityException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(PostRewriteResponse.capacity(e.getMessage()));
        } catch (LlmTimeoutException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(PostRewriteResponse.timeout(corrId));
        } catch (Exception e) {
            log.error("Post rewrite error: corr={} postId={}", corrId, req.getPostId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(PostRewriteResponse.rewriteError(e.getMessage(), corrId));
        }
    }

    private String corrId(String provided) {
        return (provided != null && !provided.isBlank()) ? provided : UUID.randomUUID().toString().substring(0, 8);
    }
}
