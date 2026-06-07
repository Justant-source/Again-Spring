package com.againspring.aiuser.orchestrator.service;

import com.againspring.aiuser.orchestrator.client.LlmAiUserClient;
import com.againspring.aiuser.orchestrator.client.dto.PostDto;
import com.againspring.aiuser.orchestrator.domain.PostAnalysis;
import com.againspring.aiuser.orchestrator.engine.ArchetypeCatalog;
import com.againspring.aiuser.orchestrator.repository.PostAnalysisRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 글 분석 캐시 서비스 — "글마다 1회 분석, 페르소나별 로컬 매칭" 아키텍처의 핵심.
 * 캐시 조회 → 미스 시 LLM 1회 분석 → 영구 저장. 실패 시 null 반환(호출측 graceful fallback).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostAnalysisService {

    private final PostAnalysisRepository analysisRepo;
    private final LlmAiUserClient llmClient;
    private final ArchetypeCatalog archetypeCatalog;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 인메모리 캐시 — 분석값은 불변이므로 안전. ActionPlanner가 틱당 글당 여러 번 조회하는
     * 중복 DB PK 룩업 제거. 상한 초과 시 전체 비움(단순 leak 방지; 분석값은 DB에 영구 보존).
     */
    private final Map<String, PostAnalysis> memCache = new ConcurrentHashMap<>();
    private static final int MEM_CACHE_MAX = 2000;

    /** 캐시만 조회 (LLM 호출 안 함). 미분석 글이면 null. */
    public PostAnalysis getCached(String postId) {
        if (postId == null) return null;
        PostAnalysis m = memCache.get(postId);
        if (m != null) return m;
        PostAnalysis db = analysisRepo.findById(postId).orElse(null);
        if (db != null) putMem(postId, db);
        return db;
    }

    private void putMem(String postId, PostAnalysis a) {
        if (memCache.size() >= MEM_CACHE_MAX) memCache.clear();
        memCache.put(postId, a);
    }

    /** 캐시 조회 → 없으면 LLM 분석·저장. 실패 시 null. */
    public PostAnalysis getOrAnalyze(PostDto post) {
        if (post == null || post.getId() == null) return null;
        PostAnalysis cached = getCached(post.getId());
        if (cached != null) return cached;
        return analyzeAndSave(post);
    }

    /** LLM 분석 후 저장 (캐시 미스 전제). 동시성: 같은 글 중복 분석 시 마지막 save가 덮어씀(무해). */
    public PostAnalysis analyzeAndSave(PostDto post) {
        if (post == null || post.getId() == null) return null;
        String hints = archetypeHints(post.getCategory());
        Optional<String> jsonOpt = llmClient.analyzePost(
            post.getId(), post.getTitle(), post.getBodyPublished(), post.getCategory(), hints);
        if (jsonOpt.isEmpty()) {
            log.warn("Post analysis failed (LLM) for post {}", post.getId());
            return null;
        }
        try {
            PostAnalysis analysis = parse(post.getId(), jsonOpt.get());
            PostAnalysis saved = analysisRepo.save(analysis);
            putMem(post.getId(), saved);
            log.debug("Post analysis cached: post={} sympathy={} ambiguity={} severity={} frame={}",
                post.getId(), saved.getAuthorSympathy(), saved.getAmbiguity(),
                saved.getSeverity(), saved.getArchetypeFrame());
            return saved;
        } catch (Exception e) {
            log.warn("Post analysis parse/save failed for post {}: {}", post.getId(), e.getMessage());
            return null;
        }
    }

    /** 카테고리별 archetype id 후보 (콤마 구분) — LLM이 archetype_frame을 고르는 힌트. */
    private String archetypeHints(String category) {
        if (category == null) return null;
        List<String> ids = archetypeCatalog.idsForCategory(category);
        return ids.isEmpty() ? null : String.join(",", ids);
    }

    private PostAnalysis parse(String postId, String raw) throws Exception {
        JsonNode n = MAPPER.readTree(extractJson(raw));
        String frame = textOrNull(n, "archetype_frame");
        // best-effort 검증: 카탈로그에 없는 id면 null 처리 (저가중치라 무해)
        if (frame != null && !archetypeCatalog.isValidId(frame)) frame = null;
        return PostAnalysis.builder()
            .postId(postId)
            .authorSympathy(clampBd(n.path("author_sympathy").asDouble(0.5)))
            .ambiguity(clampBd(n.path("ambiguity").asDouble(0.5)))
            .severity(clampBd(n.path("severity").asDouble(0.5)))
            .topics(toStrList(n.get("topics")))
            .emotions(toStrList(n.get("emotions")))
            .archetypeFrame(frame)
            .politicalHint(normalizeHint(textOrNull(n, "political_hint")))
            .analyzedAt(Instant.now())
            .build();
    }

    /** LLM이 코드펜스/잡텍스트를 섞어도 첫 '{' ~ 마지막 '}' 추출. */
    private String extractJson(String raw) {
        if (raw == null) return "{}";
        int s = raw.indexOf('{');
        int e = raw.lastIndexOf('}');
        return (s >= 0 && e > s) ? raw.substring(s, e + 1) : raw;
    }

    private BigDecimal clampBd(double v) {
        double c = Math.max(0.0, Math.min(1.0, v));
        return BigDecimal.valueOf(Math.round(c * 100) / 100.0);
    }

    private String textOrNull(JsonNode n, String field) {
        JsonNode f = n.get(field);
        if (f == null || f.isNull()) return null;
        String s = f.asText("").trim();
        return (s.isBlank() || "null".equalsIgnoreCase(s)) ? null : s;
    }

    private String normalizeHint(String h) {
        if (h == null) return "neutral";
        String l = h.toLowerCase();
        if (l.contains("progress")) return "progressive";
        if (l.contains("conserv")) return "conservative";
        return "neutral";
    }

    private List<String> toStrList(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode item : arr) {
                String s = item.asText("").trim();
                if (!s.isBlank() && out.size() < 5) out.add(s);
            }
        }
        return out;
    }
}
