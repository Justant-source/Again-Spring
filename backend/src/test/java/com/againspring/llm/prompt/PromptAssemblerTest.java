package com.againspring.llm.prompt;

import com.againspring.domain.enums.ConflictType;
import com.againspring.domain.enums.TurnRole;
import com.againspring.llm.LLMRequest;
import com.againspring.llm.PromptLayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class PromptAssemblerTest {

    private PromptAssembler assembler;
    private PromptLoader loader;
    private Path tempDir;

    @BeforeEach
    void setUp(@TempDir Path tempDirectory) throws Exception {
        tempDir = tempDirectory;
        loader = new PromptLoader();
        loader.promptsPath = tempDir.toString();
        assembler = new PromptAssembler(loader);

        // Create minimal prompt structure
        Files.writeString(tempDir.resolve("system.md"), "You are a mediator.");

        Files.createDirectory(tempDir.resolve("relations"));
        Files.writeString(tempDir.resolve("relations/partner.md"), "Partner relationship guidance.");

        Files.createDirectory(tempDir.resolve("gottman"));
        Files.writeString(tempDir.resolve("gottman/conflict_factual.md"), "Factual conflict tips.");
        Files.writeString(tempDir.resolve("gottman/principles.md"), "Gottman principles.");

        Files.createDirectory(tempDir.resolve("nvc"));
        Files.writeString(tempDir.resolve("nvc/framework.md"), "NVC four steps.");

        Files.createDirectory(tempDir.resolve("turns"));
        Files.writeString(tempDir.resolve("turns/turn_3_a.md"), "Ask Partner A about X.");
    }

    @Test
    void testAssembleBasicRequest() {
        String correlationId = UUID.randomUUID().toString();
        LLMRequest request = assembler.assemble(
                3,
                TurnRole.A,
                "partner",
                ConflictType.FACTUAL,
                "My concern is about communication",
                correlationId,
                Map.of("turnNumber", (Object)3)
        );

        assertThat(request).isNotNull();
        assertThat(request.getSystemPrompt()).contains("mediator");
        assertThat(request.getUserInput()).isEqualTo("My concern is about communication");
        assertThat(request.getCorrelationId()).isEqualTo(correlationId);
        assertThat(request.getTimeout().toSeconds()).isEqualTo(30);
    }

    @Test
    void testLayersAreInOrder() {
        LLMRequest request = assembler.assemble(
                3,
                TurnRole.A,
                "partner",
                ConflictType.FACTUAL,
                "User input",
                "corr-123",
                Map.of()
        );

        assertThat(request.getLayers()).isNotEmpty();
        // Verify layers are sorted by order
        for (int i = 1; i < request.getLayers().size(); i++) {
            PromptLayer prev = request.getLayers().get(i - 1);
            PromptLayer curr = request.getLayers().get(i);
            assertThat(prev.order()).isLessThanOrEqualTo(curr.order());
        }
    }

    @Test
    void testLayerContentPresent() {
        LLMRequest request = assembler.assemble(
                3,
                TurnRole.A,
                "partner",
                ConflictType.FACTUAL,
                "User input",
                "corr-123",
                Map.of()
        );

        // Should have loaded relation, conflict, gottman, nvc, and turn layers
        String allContent = request.getLayers().stream()
                .map(PromptLayer::content)
                .reduce("", String::concat);

        assertThat(allContent)
                .contains("Partner relationship")
                .contains("Factual conflict")
                .contains("Gottman principles")
                .contains("NVC four steps")
                .contains("Ask Partner A");
    }

    @Test
    void testEmptyLayersRemoved() {
        // Assemble with a relation that doesn't exist
        LLMRequest request = assembler.assemble(
                1,
                TurnRole.A,
                "nonexistent_relation",  // This file won't exist
                null,  // No conflict type
                "User input",
                "corr-123",
                Map.of()
        );

        // Empty layers should be filtered out
        assertThat(request.getLayers())
                .allMatch(layer -> !layer.content().isBlank());
    }

    @Test
    void testMetadataIncluded() {
        Map<String, Object> metadata = Map.of("customKey", "customValue");
        LLMRequest request = assembler.assemble(
                3,
                TurnRole.A,
                "partner",
                ConflictType.FACTUAL,
                "Input",
                "corr-123",
                metadata
        );

        assertThat(request.getMetadata()).containsEntry("customKey", "customValue");
    }
}
