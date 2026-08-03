package com.againspring.aiuser.llm.service;

/** Shared provider-neutral output contracts for the v2 structured endpoints. */
public enum StructuredOutputSchema {
    THREAD_PLAN("schemas/thread-plan.schema.json"),
    HUMAN_REPLIES("schemas/human-replies.schema.json"),
    /** Logical Call1 — 작성자 post + phase1 comments (author-only grounding). */
    PAIRED_PHASE1("schemas/paired-phase1.schema.json"),
    /** Logical Call2 — 상대방 body + phase2 comments (author+partner[+published comments]). */
    PAIRED_PHASE2("schemas/paired-phase2.schema.json");

    /** Stable workload id returned on responses and used by orchestrator clients. */
    public static final String WORKLOAD_PAIRED_PHASE1 = "PAIRED_PHASE1";
    public static final String WORKLOAD_PAIRED_PHASE2 = "PAIRED_PHASE2";

    private final String classpathLocation;

    StructuredOutputSchema(String classpathLocation) {
        this.classpathLocation = classpathLocation;
    }

    String classpathLocation() {
        return classpathLocation;
    }
}
