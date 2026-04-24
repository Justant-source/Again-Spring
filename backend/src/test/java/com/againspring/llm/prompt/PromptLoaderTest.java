package com.againspring.llm.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.*;

class PromptLoaderTest {

    private PromptLoader promptLoader;
    private Path tempDir;

    @BeforeEach
    void setUp(@TempDir Path tempDirectory) throws Exception {
        tempDir = tempDirectory;
        promptLoader = new PromptLoader();
        promptLoader.promptsPath = tempDir.toString();

        // Create sample files
        Files.writeString(tempDir.resolve("system.md"), "System prompt content");
        Files.createDirectory(tempDir.resolve("turns"));
        Files.writeString(tempDir.resolve("turns/turn_1_a.md"), "Turn 1 Partner A task");
    }

    @Test
    void testLoadValidFile() throws Exception {
        String content = promptLoader.get("system.md");
        assertThat(content).isEqualTo("System prompt content");
    }

    @Test
    void testLoadFileInSubdirectory() throws Exception {
        String content = promptLoader.get("turns/turn_1_a.md");
        assertThat(content).isEqualTo("Turn 1 Partner A task");
    }

    @Test
    void testFileNotFound() {
        assertThatThrownBy(() -> promptLoader.get("nonexistent.md"))
                .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void testCaching() throws Exception {
        String first = promptLoader.get("system.md");
        String second = promptLoader.get("system.md");
        assertThat(first).isEqualTo(second);
    }

    @Test
    void testReloadOnModification() throws Exception {
        String original = promptLoader.get("system.md");
        assertThat(original).isEqualTo("System prompt content");

        // Modify file
        Thread.sleep(10);  // Ensure mtime changes
        Files.writeString(tempDir.resolve("system.md"), "Modified content");

        String reloaded = promptLoader.get("system.md");
        assertThat(reloaded).isEqualTo("Modified content");
    }

    @Test
    void testReloadAllClearsCache() throws Exception {
        promptLoader.get("system.md");

        promptLoader.reloadAll();
        // Cache should be empty after reloadAll
        // Re-load to verify it works
        String reloaded = promptLoader.get("system.md");
        assertThat(reloaded).isEqualTo("System prompt content");
    }

    @Test
    void testGetAllFiles() throws Exception {
        Files.createDirectory(tempDir.resolve("gottman"));
        Files.writeString(tempDir.resolve("gottman/principles.md"), "Principles");
        Files.writeString(tempDir.resolve("gottman/conflict_factual.md"), "Factual");

        var files = promptLoader.getAll("gottman");
        assertThat(files).contains("conflict_factual.md", "principles.md");
    }

    @Test
    void testInvalidateSpecificPrompt() throws Exception {
        promptLoader.get("system.md");
        promptLoader.invalidate("system.md");
        // After invalidate, reloading should work
        String reloaded = promptLoader.get("system.md");
        assertThat(reloaded).isEqualTo("System prompt content");
    }
}
