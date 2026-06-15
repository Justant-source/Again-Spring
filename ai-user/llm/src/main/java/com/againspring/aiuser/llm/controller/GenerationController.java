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

    /** 글(POST) 전용 모델 오버라이드 — 빈 값이면 풀 기본 모델 (문체 현실화 S5: 글만 Sonnet 승격). */
    @org.springframework.beans.factory.annotation.Value("${llm.post-model:}")
    private String postModel;

    @PostMapping("/post")
    public ResponseEntity<GenResponse> generatePost(@RequestBody PostGenRequest req) {
        String corrId = corrId(req.getCorrelationId());
        long start = System.currentTimeMillis();
        try {
            String model = (postModel != null && !postModel.isBlank()) ? postModel.trim() : null;
            String prompt = promptAssembler.assemblePostPrompt(req);
            String raw = pool.executeSyncTask(prompt, model, req.getTimeoutMs(), corrId, req.getBackend());
            String text = outputSanitizer.sanitizePost(raw, req.getVoiceType());
            // 자기비평 루프 (enabled 시) — 동일 backend·model 승계, formality 전달
            text = selfCritique.critiqueAndRefine(text, "post", prompt, corrId, req.getBackend(), req.getFormality(), model);
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
            String raw = pool.executeSyncTask(prompt, null, req.getTimeoutMs(), corrId, req.getBackend());
            // 센티넬 분리 먼저 (sanitize/critique 전에) — OutputSanitizer가 <<<REACT>>> 이하를 파괴하기 전에 추출
            String[] split = splitReactions(raw);
            String reactionsJson = split[1];  // 최초 raw에서 캡처 (critique 재생성으로도 보존됨)
            String text = outputSanitizer.sanitizeComment(split[0], req.getVoiceType());
            // 자기비평 루프 (enabled 시, 댓글은 점수 기준 완화) — 동일 backend 승계, formality 전달
            text = selfCritique.critiqueAndRefine(text, "comment", prompt, corrId, req.getBackend(), req.getFormality());
            return ResponseEntity.ok(GenResponse.success(text, reactionsJson, System.currentTimeMillis() - start, corrId));
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
            String raw = pool.executeSyncTask(prompt, null, req.getTimeoutMs(), corrId, req.getBackend());
            // 센티넬 분리 먼저 — OutputSanitizer가 <<<REACT>>> 이하를 파괴하기 전에 추출
            String[] split = splitReactions(raw);
            String text = outputSanitizer.sanitizeComment(split[0]); // same sanitizer (short text)
            return ResponseEntity.ok(GenResponse.success(text, split[1], System.currentTimeMillis() - start, corrId));
        } catch (LlmCapacityException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(GenResponse.capacity(e.getMessage()));
        } catch (LlmTimeoutException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(GenResponse.timeout(corrId));
        } catch (Exception e) {
            log.error("Reply generation error: corr={}", corrId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(GenResponse.genError(e.getMessage()));
        }
    }

    @PostMapping("/persona")
    public ResponseEntity<GenResponse> generatePersona(@RequestBody PersonaGenRequest req) {
        String corrId = corrId(req.getCorrelationId());
        long start = System.currentTimeMillis();
        try {
            String prompt = promptAssembler.assemblePersonaPrompt(req);
            if (prompt.isBlank()) {
                return ResponseEntity.badRequest().body(GenResponse.genError("empty prompt"));
            }
            long timeout = req.getTimeoutMs() != null ? req.getTimeoutMs() : 60000L;
            String raw = pool.executeSyncTask(prompt, null, timeout, corrId);
            // 페르소나 응답은 JSON — OutputSanitizer를 거치지 않음 (JSON 구조 보존)
            String text = raw != null ? raw.trim() : "";
            return ResponseEntity.ok(GenResponse.success(text, System.currentTimeMillis() - start, corrId));
        } catch (LlmCapacityException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(GenResponse.capacity(e.getMessage()));
        } catch (LlmTimeoutException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(GenResponse.timeout(corrId));
        } catch (Exception e) {
            log.error("Persona generation error: corr={}", corrId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(GenResponse.genError(e.getMessage()));
        }
    }

    private String corrId(String provided) {
        return (provided != null && !provided.isBlank()) ? provided : UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 원문(raw)에서 <<<REACT>>> 센티넬을 기준으로 본문과 반응 JSON을 분리한다.
     * split[0] = 본문(센티넬 이전), split[1] = 반응 JSON 문자열 또는 null.
     * 센티넬 없거나 JSON 파싱 불가 → split[1] = null (graceful degrade).
     */
    private String[] splitReactions(String raw) {
        if (raw == null) return new String[]{"", null};
        int sentinelIdx = raw.indexOf("<<<REACT>>>");
        if (sentinelIdx < 0) return new String[]{raw, null};
        String textPart = raw.substring(0, sentinelIdx).trim();
        String after = raw.substring(sentinelIdx + "<<<REACT>>>".length()).trim();
        int jsonStart = after.indexOf('{');
        int jsonEnd = after.lastIndexOf('}');
        String reactionsJson = (jsonStart >= 0 && jsonEnd > jsonStart) ? after.substring(jsonStart, jsonEnd + 1) : null;
        return new String[]{textPart, reactionsJson};
    }
}
