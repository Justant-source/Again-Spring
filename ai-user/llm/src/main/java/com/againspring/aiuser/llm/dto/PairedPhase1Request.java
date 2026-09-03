package com.againspring.aiuser.llm.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Logical Call1 for AI paired posts: 작성자(A) post + phase1 comment candidates
 * grounded on the author body only (partner has not written yet).
 */
@Data
public class PairedPhase1Request {
    private String provider; // CLAUDE | CODEX
    private String model;
    private String correlationId;
    private Long timeoutMs;
    private String category;
    private String topicHint;
    private Map<String, Object> sourceContext;
    private Boolean reconstructMode;
    private Long sourceExampleId;
    private String sourceBody;
    private String dynamicExamples;
    private List<String> recentOutputs;
    /** Metaphor ids used too often recently (orchestrator-computed) — LLM should avoid repeating these. */
    private List<String> overusedMetaphorIds;
    /** Explicit 작성자 profile; prefer over assuming personas[0]. */
    private Map<String, Object> author;
    /** Comment cast (and optionally the author persona for voice grounding). */
    private List<ThreadPlanRequest.Persona> personas;
    /** Default 4 — phase1 is a small top-level set (~2–4). */
    private Integer maxTopLevel = 4;
    private Integer maxReplies = 2;
    /** Default 2 when null. */
    private Integer minTopLevel;
    /** Default = minTopLevel when null. */
    private Integer minItems;
    /** 요청별 프롬프트 가이드 오버라이드 (key="voice/paired_phase1" 등 → 본문). classpath 기본값보다 우선. 없으면 null. */
    private Map<String, String> promptOverrides;
}
