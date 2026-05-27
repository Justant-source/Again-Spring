package com.againspring.service.marketing.image;

/**
 * Metadata for a single rendered PNG file.
 * filename: saved under app.features.marketing.image-dir.
 * role: semantic role (QUOTE_CARD, CHAT_PREVIEW, CARD_SLIDE, REPORT_NEEDS, REPORT_RATIO).
 * slot: template slot marker (e.g. "TWEET_1", "SLIDE_1", "IMG:chat-preview").
 * alt: accessibility alt text.
 * order: display order (1-based).
 */
public record RenderedImage(
        String filename,
        String role,
        String slot,
        String alt,
        int order
) {}
