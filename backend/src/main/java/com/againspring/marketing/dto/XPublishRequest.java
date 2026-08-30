package com.againspring.marketing.dto;

/**
 * ASM {@code POST /api/v1/x/publish} body (camelCase). Not mixed with createJob/x_thread.
 */
public record XPublishRequest(
    String text,
    String imageBase64,
    String imageMime,
    String replyToTweetId
) {}
