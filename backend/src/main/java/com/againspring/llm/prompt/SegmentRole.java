package com.againspring.llm.prompt;

/**
 * Role classification for prompt segments.
 * Used to identify the semantic purpose of each segment.
 */
public enum SegmentRole {
    SYSTEM,                   // System prompt (instructions to the model)
    FRAMEWORK,                // Psychological frameworks (Gottman, NVC)
    USER_CONTEXT,             // User profile, psychology, state
    CONVERSATION_HISTORY,     // Past conversation turns
    CURRENT_INPUT,            // Current user message or query
    INSTRUCTIONS              // Response instructions, rules
}
