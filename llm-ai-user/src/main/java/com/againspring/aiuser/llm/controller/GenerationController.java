package com.againspring.aiuser.llm.controller;

import com.againspring.aiuser.llm.dto.*;
import com.againspring.aiuser.llm.exception.*;
import com.againspring.aiuser.llm.pool.LlmWorkerPool;
import com.againspring.aiuser.llm.service.OutputSanitizer;
import com.againspring.aiuser.llm.service.PromptAssembler;
import com.againspring.aiuser.llm.service.SelfCritiqueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/generate")
@RequiredArgsConstructor
public class GenerationController {

    private final LlmWorkerPool pool;
    private final PromptAssembler promptAssembler;
    private final OutputSanitizer outputSanitizer;
    private final SelfCritiqueService selfCritique;

    @PostMapping("/post")
    public ResponseEntity<GenResponse> generatePost(@RequestBody PostGenRequest req) {
        String corrId = corrId(req.getCorrelationId());
        long start = System.currentTimeMillis();
        try {
            String prompt = promptAssembler.assemblePostPrompt(req);
            String raw = pool.executeSyncTask(prompt, null, req.getTimeoutMs(), corrId);
            String text = outputSanitizer.sanitizePost(raw);
            // 자기비평 루프 (enabled 시)
            text = selfCritique.critiqueAndRefine(text, "post", prompt, corrId);
            return ResponseEntity.ok(GenResponse.success(text, System.currentTimeMillis() - start, corrId));
        } catch (LlmCapacityException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(GenResponse.capacity(e.getMessage()));
        } catch (LlmTimeoutException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(GenResponse.timeout(corrId));
        } catch (Exception e) {
            log.error("Post generation error: corr={}", corrId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(GenResponse.genError(e.getMessage()));
        }
    }

    @PostMapping("/comment")
    public ResponseEntity<GenResponse> generateComment(@RequestBody CommentGenRequest req) {
        String corrId = corrId(req.getCorrelationId());
        long start = System.currentTimeMillis();
        try {
            String prompt = promptAssembler.assembleCommentPrompt(req);
            String raw = pool.executeSyncTask(prompt, null, req.getTimeoutMs(), corrId);
            String text = outputSanitizer.sanitizeComment(raw);
            // 자기비평 루프 (enabled 시, 댓글은 점수 기준 완화)
            text = selfCritique.critiqueAndRefine(text, "comment", prompt, corrId);
            return ResponseEntity.ok(GenResponse.success(text, System.currentTimeMillis() - start, corrId));
        } catch (LlmCapacityException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(GenResponse.capacity(e.getMessage()));
        } catch (LlmTimeoutException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(GenResponse.timeout(corrId));
        } catch (Exception e) {
            log.error("Comment generation error: corr={}", corrId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(GenResponse.genError(e.getMessage()));
        }
    }

    @PostMapping("/reply")
    public ResponseEntity<GenResponse> generateReply(@RequestBody ReplyGenRequest req) {
        String corrId = corrId(req.getCorrelationId());
        long start = System.currentTimeMillis();
        try {
            String prompt = promptAssembler.assembleReplyPrompt(req);
            String raw = pool.executeSyncTask(prompt, null, req.getTimeoutMs(), corrId);
            String text = outputSanitizer.sanitizeComment(raw); // same sanitizer (short text)
            return ResponseEntity.ok(GenResponse.success(text, System.currentTimeMillis() - start, corrId));
        } catch (LlmCapacityException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(GenResponse.capacity(e.getMessage()));
        } catch (LlmTimeoutException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(GenResponse.timeout(corrId));
        } catch (Exception e) {
            log.error("Reply generation error: corr={}", corrId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(GenResponse.genError(e.getMessage()));
        }
    }

    private String corrId(String provided) {
        return (provided != null && !provided.isBlank()) ? provided : UUID.randomUUID().toString().substring(0, 8);
    }
}
