package com.againspring.llm.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches prompt markdown files from disk.
 * Supports hot-reload: detects file modifications by mtime.
 */
@Slf4j
@Component
public class PromptLoader {

    @Value("${app.prompts.path:./shared/docs/prompts}")
    public String promptsPath;

    private static class CacheEntry {
        String content;
        long lastModified;

        CacheEntry(String content, long lastModified) {
            this.content = content;
            this.lastModified = lastModified;
        }
    }

    private final Map<String, CacheEntry> promptCache = new ConcurrentHashMap<>();

    /**
     * Load a prompt file by relative path.
     * Caches result; on subsequent access, checks mtime and reloads if modified.
     *
     * @param relativePath e.g. "system.md", "turns/turn_1_a.md"
     * @return file content
     * @throws NoSuchFileException if file does not exist
     */
    public String get(String relativePath) throws NoSuchFileException {
        Path filePath = Paths.get(promptsPath, relativePath);

        try {
            long currentMtime = Files.getLastModifiedTime(filePath).toMillis();

            CacheEntry cached = promptCache.get(relativePath);
            if (cached != null && cached.lastModified == currentMtime) {
                return cached.content;
            }

            // Load or reload
            String content = Files.readString(filePath);
            promptCache.put(relativePath, new CacheEntry(content, currentMtime));
            log.debug("Loaded prompt: {}", relativePath);
            return content;

        } catch (NoSuchFileException e) {
            log.error("Prompt file not found: {}", relativePath);
            throw e;
        } catch (Exception e) {
            log.error("Failed to load prompt {}: {}", relativePath, e.getMessage());
            throw new RuntimeException("Failed to load prompt: " + relativePath, e);
        }
    }

    /**
     * Load all files from a subdirectory, sorted alphabetically.
     *
     * @param subdir e.g. "gottman", "nvc", "turns"
     * @return sorted list of files in subdir
     */
    public List<String> getAll(String subdir) {
        Path dirPath = Paths.get(promptsPath, subdir);
        List<String> files = new ArrayList<>();

        try {
            Files.list(dirPath)
                    .filter(p -> p.toFile().isFile() && p.toString().endsWith(".md"))
                    .sorted()
                    .forEach(p -> files.add(p.getFileName().toString()));
            log.debug("Listed {} files in {}", files.size(), subdir);
        } catch (Exception e) {
            log.error("Failed to list directory {}: {}", subdir, e.getMessage());
        }

        return files;
    }

    /**
     * Clear all cached prompts. Used for hot-reload.
     */
    public void reloadAll() {
        promptCache.clear();
        log.info("Cleared all prompt caches");
    }

    /**
     * Manual cache invalidation for a specific prompt.
     */
    public void invalidate(String relativePath) {
        promptCache.remove(relativePath);
        log.debug("Invalidated cache for: {}", relativePath);
    }
}
