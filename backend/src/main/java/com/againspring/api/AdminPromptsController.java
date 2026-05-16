package com.againspring.api;

import com.againspring.llm.prompt.PromptLoader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin — Prompts", description = "LLM 프롬프트 핫리로드 (app.admin.enabled=true 환경만 활성)")
public class AdminPromptsController {

    private final PromptLoader promptLoader;

    /**
     * Hot-reload all cached prompts from disk.
     * Accessible to ADMIN role.
     */
    @PostMapping("/reload")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "프롬프트 핫리로드", description = "디스크에서 모든 LLM 프롬프트를 다시 로드한다. ADMIN 역할 + app.admin.enabled=true 필요.")
    @ApiResponse(responseCode = "200", description = "리로드 성공")
    @ApiResponse(responseCode = "401", description = "인증 필요")
    @ApiResponse(responseCode = "403", description = "ADMIN 권한 없음")
    @ApiResponse(responseCode = "500", description = "프롬프트 로드 실패")
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
