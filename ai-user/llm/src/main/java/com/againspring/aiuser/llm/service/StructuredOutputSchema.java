package com.againspring.aiuser.llm.service;

/** Shared provider-neutral output contracts for the v2 structured endpoints. */
public enum StructuredOutputSchema {
    THREAD_PLAN("schemas/thread-plan.schema.json"),
    HUMAN_REPLIES("schemas/human-replies.schema.json");

    private final String classpathLocation;

    StructuredOutputSchema(String classpathLocation) {
        this.classpathLocation = classpathLocation;
    }

    String classpathLocation() {
        return classpathLocation;
    }
}
