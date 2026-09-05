package com.againspring.aiuser.llm.service;

import com.againspring.aiuser.llm.dto.SkeletonExtractRequest;
import com.againspring.aiuser.llm.dto.SkeletonExtractResponse;
import com.againspring.aiuser.llm.pool.LlmWorkerPool;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * persona-diversity-v4 WP2 — 크롤 원본에서 "뼈대"(계약 7)만 뽑아내는 1회성 Haiku 호출.
 * 실패(파싱 실패·필수 키 누락·sequence 3개 미만)는 예외가 아니라
 * {@link SkeletonExtractResponse#failure(String)}로 흡수한다 — 컨트롤러는 항상 200을 반환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkeletonExtractionService {
    private static final long TIMEOUT_MS = 60_000L;
    private static final int MIN_SEQUENCE = 3;

    private final LlmWorkerPool pool;

    private volatile String promptTemplate;

    public SkeletonExtractResponse extract(SkeletonExtractRequest req, String correlationId) throws Exception {
        if (req == null || blank(req.getContent())) {
            return SkeletonExtractResponse.failure("content is required");
        }
        String prompt = buildPrompt(req);
        // 모델 미지정 → LlmWorkerPool 기본값(claudeDefault=Haiku) 사용.
        String raw = pool.executeSyncTask(prompt, null, TIMEOUT_MS, correlationId);
        return parse(raw, req);
    }

    private String buildPrompt(SkeletonExtractRequest req) {
        String template = loadTemplate();
        String category = req.getCategory() == null ? "" : req.getCategory();
        String title = req.getTitle() == null ? "" : req.getTitle();
        String content = req.getContent() == null ? "" : req.getContent();
        return template
                .replace("{{CATEGORY}}", category)
                .replace("{{TITLE}}", title)
                .replace("{{CONTENT}}", content);
    }

    private String loadTemplate() {
        String cached = promptTemplate;
        if (cached != null) return cached;
        try {
            String loaded = new ClassPathResource("voice/skeleton_extract.md").getContentAsString(StandardCharsets.UTF_8);
            promptTemplate = loaded;
            return loaded;
        } catch (IOException e) {
            throw new IllegalStateException("skeleton_extract.md missing from classpath", e);
        }
    }

    private SkeletonExtractResponse parse(String raw, SkeletonExtractRequest req) {
        JsonNode node;
        try {
            node = JsonExtractorUtil.extract(raw);
        } catch (RuntimeException e) {
            log.warn("skeleton extract parse failed sourceExampleId={}: {}", req.getSourceExampleId(), e.getMessage());
            return SkeletonExtractResponse.failure("invalid JSON response: " + e.getMessage());
        }

        String category = text(node, "category");
        String authorRole = text(node, "author_role");
        String counterpartRole = text(node, "counterpart_role");
        String relationship = text(node, "relationship");
        String incident = text(node, "incident");
        String stakes = text(node, "stakes");
        String authorClaim = text(node, "author_claim");
        String counterpartClaim = text(node, "counterpart_claim");
        String emotion = text(node, "emotion");
        String grayZone = text(node, "gray_zone");
        List<String> sequence = textArray(node, "sequence");
        Boolean bSideViable = node.path("b_side_viable").isBoolean() ? node.path("b_side_viable").asBoolean() : null;

        List<String> missing = new ArrayList<>();
        if (blank(category)) missing.add("category");
        if (blank(authorRole)) missing.add("author_role");
        if (blank(counterpartRole)) missing.add("counterpart_role");
        if (blank(relationship)) missing.add("relationship");
        if (blank(incident)) missing.add("incident");
        if (blank(stakes)) missing.add("stakes");
        if (blank(authorClaim)) missing.add("author_claim");
        if (blank(counterpartClaim)) missing.add("counterpart_claim");
        if (blank(emotion)) missing.add("emotion");
        if (blank(grayZone)) missing.add("gray_zone");
        if (bSideViable == null) missing.add("b_side_viable");
        if (!missing.isEmpty()) {
            return SkeletonExtractResponse.failure("missing required keys: " + String.join(",", missing));
        }
        if (sequence.size() < MIN_SEQUENCE) {
            return SkeletonExtractResponse.failure("sequence has fewer than " + MIN_SEQUENCE + " items");
        }

        return SkeletonExtractResponse.builder()
                .ok(true)
                .category(category)
                .authorRole(authorRole)
                .counterpartRole(counterpartRole)
                .relationship(relationship)
                .incident(incident)
                .sequence(sequence)
                .stakes(stakes)
                .authorClaim(authorClaim)
                .counterpartClaim(counterpartClaim)
                .emotion(emotion)
                .grayZone(grayZone)
                .bSideViable(bSideViable)
                .sourceExampleId(req.getSourceExampleId())
                .build();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? "" : v.asText("").trim();
    }

    private static List<String> textArray(JsonNode node, String field) {
        JsonNode arr = node.path(field);
        List<String> out = new ArrayList<>();
        if (arr.isArray()) {
            for (JsonNode item : arr) {
                String s = item.asText("").trim();
                if (!s.isBlank()) out.add(s);
            }
        }
        return out;
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
