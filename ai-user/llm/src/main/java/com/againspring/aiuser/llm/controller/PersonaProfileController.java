package com.againspring.aiuser.llm.controller;

import com.againspring.aiuser.llm.dto.PersonaProfileGenRequest;
import com.againspring.aiuser.llm.exception.LlmCapacityException;
import com.againspring.aiuser.llm.exception.LlmTimeoutException;
import com.againspring.aiuser.llm.service.PersonaProfileService;
import com.againspring.aiuser.llm.service.StructuredGenerationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * WP1 — {@code POST /generate/persona-profile} (01-wp1-persona-data.md §4).
 * 기존 {@code /generate/persona}({@link GenerationController})는 strengthener 전용이라 건드리지
 * 않고, 페르소나 다양성 v4 재생성 전용 새 경로로 분리한다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PersonaProfileController {

    private final PersonaProfileService personaProfileService;

    @PostMapping("/generate/persona-profile")
    public ResponseEntity<Map<String, Object>> generatePersonaProfile(@RequestBody PersonaProfileGenRequest req) {
        String corr = correlation(req == null ? null : req.getCorrelationId());
        try {
            Map<String, Object> profile = personaProfileService.generate(req, corr);
            return ResponseEntity.ok(profile);
        } catch (StructuredGenerationException e) {
            log.warn("[{}] persona-profile validation failed: {}", corr, e.getMessage());
            return ResponseEntity.badRequest().body(error("PERSONA_PROFILE_INVALID", e.getMessage(), corr));
        } catch (LlmCapacityException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error("CAPACITY", e.getMessage(), corr));
        } catch (LlmTimeoutException e) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(error("TIMEOUT", e.getMessage(), corr));
        } catch (Exception e) {
            log.error("[{}] persona-profile generation failed", corr, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error("GENERATION_FAILED", "generation failed", corr));
        }
    }

    private static String correlation(String provided) {
        return (provided != null && !provided.isBlank()) ? provided : UUID.randomUUID().toString().substring(0, 8);
    }

    private static Map<String, Object> error(String code, String message, String corr) {
        return Map.of("errorCode", code, "message", message == null ? "" : message, "correlationId", corr);
    }
}
