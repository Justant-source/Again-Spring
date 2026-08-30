package com.againspring.marketing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * ASM {@code GET /api/v1/x/inbox} envelope.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record XInboxResponse(List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
        String tweetId,
        String parentTweetId,
        String ourPostTweetId,
        String authorHandle,
        String text,
        Instant createdAt
    ) {}
}
