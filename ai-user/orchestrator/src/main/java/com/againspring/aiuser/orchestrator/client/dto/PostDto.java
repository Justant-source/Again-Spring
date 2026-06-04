package com.againspring.aiuser.orchestrator.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PostDto {
    private String id;
    private String title;
    private String userTitle;
    private String bodyPublished;
    private String category;
    private String status;  // PUBLISHED, DRAFT, etc.
    private Instant createdAt;
    private Long voteCount;
    private Long commentCount;
    private List<VoteOptionDto> voteOptions;
    private String authorNickname;
    private Boolean paired;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VoteOptionDto {
        private Long id;       // BIGINT — used for voting
        private String label;  // "작성자" or "상대방"
        private Long count;
    }
}
