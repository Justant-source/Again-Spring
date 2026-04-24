package com.againspring.api;

import com.againspring.llm.prompt.PromptLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;

/**
 * Admin endpoint for LLM prompt management (development only).
 * Disabled in production.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/prompts")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
public class AdminPromptsController {

    private final PromptLoader promptLoader;

    /**
     * Hot-reload all cached prompts from disk.
     * Accessible to ADMIN role.
     */
    @PostMapping("/reload")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> reloadPrompts() {
        try {
            promptLoader.reloadAll();
            log.info("Prompts reloaded successfully");
            var response = new HashMap<String, String>();
            response.put("status", "success");
            response.put("message", "All prompts reloaded from disk");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to reload prompts: {}", e.getMessage());
            var response = new HashMap<String, String>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
