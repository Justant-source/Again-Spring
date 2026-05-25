package com.againspring.api.dto.response;

import com.againspring.domain.marketing.MarketingHashtag;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HashtagResponse {
    private Long id;
    private String platform;
    private String tag;
    private String category;
    private Integer usageCount;
    private Instant lastUsedAt;
    private Instant createdAt;

    public static HashtagResponse from(MarketingHashtag h) {
        return HashtagResponse.builder()
                .id(h.getId())
                .platform(h.getPlatform() != null ? h.getPlatform().name() : null)
                .tag(h.getTag())
                .category(h.getCategory())
                .usageCount(h.getUsageCount())
                .lastUsedAt(h.getLastUsedAt())
                .createdAt(h.getCreatedAt())
                .build();
    }
}
