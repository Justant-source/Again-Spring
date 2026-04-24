package com.againspring.llm.prompt;

import com.againspring.llm.LLMRequest;
import com.againspring.llm.PromptLayer;
import com.againspring.domain.enums.ConflictType;
import com.againspring.domain.enums.TurnRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.NoSuchFileException;
import java.time.Duration;
import java.util.*;

/**
 * Assembles layered prompts for LLM invocation.
 * Combines system prompt, context layers (gottman, nvc, relations, turn-specific),
 * and user input into a structured request per LLM_BRIDGE_ARCHITECTURE.md.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromptAssembler {

    private final PromptLoader promptLoader;

    /**
     * Build a complete LLMRequest for a given turn context.
     *
     * @param turnNumber turn number (1-6)
     * @param role whose turn (A or B)
     * @param relationType e.g. "partner", "friend"
     * @param conflictType e.g. "factual", "difference"
     * @param userInput sanitized user input
     * @param correlationId for tracing
     * @param metadata arbitrary metadata
     * @return assembled LLMRequest
     */
    public LLMRequest assemble(int turnNumber, TurnRole role, String relationType,
                               ConflictType conflictType, String userInput,
                               String correlationId, Map<String, Object> metadata) {
        try {
            // 1. Load system prompt
            String systemPrompt = promptLoader.get("system.md");

            // 2. Build layers in order (per architecture doc)
            List<PromptLayer> layers = new ArrayList<>();
            int layerOrder = 0;

            // Layer 1: System base is in systemPrompt, no layer for it

            // Layer 2: Relationship type guidance
            layers.add(new PromptLayer(
                    "relation_" + relationType,
                    loadWithFallback("relations/" + relationType + ".md", ""),
                    layerOrder++
            ));

            // Layer 3: Conflict type context (if applicable)
            if (conflictType != null) {
                String conflictFile = "gottman/conflict_" + conflictType.name().toLowerCase() + ".md";
                layers.add(new PromptLayer(
                        "conflict_" + conflictType.name(),
                        loadWithFallback(conflictFile, ""),
                        layerOrder++
                ));
            }

            // Layer 4: Gottman principles (generic)
            layers.add(new PromptLayer(
                    "gottman_principles",
                    loadWithFallback("gottman/principles.md", ""),
                    layerOrder++
            ));

            // Layer 5: NVC framework
            layers.add(new PromptLayer(
                    "nvc_framework",
                    loadWithFallback("nvc/framework.md", ""),
                    layerOrder++
            ));

            // Layer 6: Turn-specific task
            String turnFile = String.format("turns/turn_%d_%s.md", turnNumber, role.name().toLowerCase());
            layers.add(new PromptLayer(
                    "turn_" + turnNumber + "_" + role.name(),
                    loadWithFallback(turnFile, ""),
                    layerOrder++
            ));

            // Remove empty layers
            layers.removeIf(layer -> layer.content().isBlank());

            log.debug("Assembled prompt: turn={}, role={}, layers={}, correlation={}",
                    turnNumber, role, layers.size(), correlationId);

            return LLMRequest.builder()
                    .systemPrompt(systemPrompt)
                    .layers(layers)
                    .userInput(userInput)
                    .timeout(Duration.ofSeconds(30))
                    .correlationId(correlationId)
                    .metadata(metadata != null ? metadata : Map.of())
                    .build();

        } catch (NoSuchFileException e) {
            log.error("Failed to assemble prompt: missing file: {}", e.getMessage());
            throw new RuntimeException("Prompt assembly failed: " + e.getMessage(), e);
        }
    }

    /**
     * Attempt to load a file; if not found, return fallback (typically empty string).
     */
    private String loadWithFallback(String path, String fallback) {
        try {
            return promptLoader.get(path);
        } catch (NoSuchFileException e) {
            log.warn("Prompt file not found, using fallback: {}", path);
            return fallback;
        }
    }
}
