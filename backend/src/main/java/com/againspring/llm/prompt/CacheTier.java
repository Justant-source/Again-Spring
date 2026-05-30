package com.againspring.llm.prompt;

/**
 * Cache tier for prompt segments.
 * Determines how long the segment is cached in the LLM API's prompt cache.
 *
 * GLOBAL_STATIC: System, Gottman, NVC, chat mode — changes rarely, cache for full session life
 * SESSION_STATIC: Mediator style, user profile, relations guide — per session, cache until session ends
 * HISTORY: Conversation history — grows per turn, incremental updates to cache
 * DYNAMIC: Current feedback, issue context, current message, response instructions — changes per turn, no cache
 */
public enum CacheTier {
    GLOBAL_STATIC,     // Cached for entire model lifetime
    SESSION_STATIC,    // Cached for session duration
    HISTORY,           // Cached, but grows per turn
    DYNAMIC            // Not cached, changes per turn
}
