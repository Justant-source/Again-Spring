package com.againspring.marketing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * ASM {@code GET /api/v1/x/outbound-candidates} envelope.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record XOutboundCandidatesResponse(List<Item> items) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
        String tweetId,
        String authorHandle,
        String text,
        Integer replyCount,
        Double ageHours,
        Boolean alreadyRepliedByUs,
        String ourReplyTweetId
    ) {}
}
