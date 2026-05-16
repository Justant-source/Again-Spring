package com.againspring.service.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V13 Phase 1 — 첫마디 템플릿 JSON 파일 로더.
 * shared/docs/templates/first_message/{major}/{middle}__{minor}.json 에서 5개 중 랜덤 1개 선택.
 * mtime 기반 핫리로드 (PromptLoader 패턴).
 */
@Slf4j
@Component
public class FirstMessageTemplateLoader {

    @Value("${app.templates.path:./shared/docs/templates/first_message}")
    private String templatesBasePath;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Random RANDOM = new Random();

    private static class CacheEntry {
        List<String> templates;
        long lastModified;

        CacheEntry(List<String> templates, long lastModified) {
            this.templates = templates;
            this.lastModified = lastModified;
        }
    }

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * 표준 소분류(allowCustomInput=false) 조합에 대한 랜덤 첫마디 반환.
     *
     * @return 템플릿 또는 empty (파일 없거나 로드 실패)
     */
    public Optional<String> getTemplate(String majorId, String middleId, String minorId) {
        String key = majorId + "/" + middleId + "__" + minorId;
        Path filePath = Paths.get(templatesBasePath, majorId, middleId + "__" + minorId + ".json");

        try {
            long mtime = Files.getLastModifiedTime(filePath).toMillis();
            CacheEntry cached = cache.get(key);

            List<String> templates;
            if (cached != null && cached.lastModified == mtime) {
                templates = cached.templates;
            } else {
                String content = Files.readString(filePath);
                JsonNode root = MAPPER.readTree(content);
                JsonNode arr = root.get("templates");
                templates = new ArrayList<>();
                if (arr != null && arr.isArray()) {
                    for (JsonNode t : arr) {
                        String text = t.asText();
                        if (!text.isBlank()) templates.add(text);
                    }
                }
                cache.put(key, new CacheEntry(templates, mtime));
                log.debug("Loaded first-message templates: {}", key);
            }

            if (templates.isEmpty()) return Optional.empty();
            return Optional.of(templates.get(RANDOM.nextInt(templates.size())));

        } catch (java.nio.file.NoSuchFileException e) {
            log.debug("No template file for: {}", key);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to load template {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }
}
