package com.againspring.marketing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * ASM {@code POST /api/v1/x/publish} and {@code POST /api/v1/x/ritual} result.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record XPublishResponse(
    Boolean ok,
    String tweetId,
    String url,
    String photo
) {
    public boolean succeeded() {
        return Boolean.TRUE.equals(ok) || (tweetId != null && !tweetId.isBlank());
    }
}
