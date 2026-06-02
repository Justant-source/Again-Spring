package com.againspring.api.community.dto;

import com.againspring.domain.community.Post;
import com.againspring.domain.community.VoteOption;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 포스트 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostResponse {

    private String id;

    private String title;

    private String bodyPublished;

    private String category;

    private String visibility;

    private String status;

    private List<VoteOptionDto> voteOptions;

    private Instant createdAt;

    /**
     * Post + VoteOption 목록으로부터 PostResponse 생성
     */
    public static PostResponse from(Post post, List<VoteOption> options) {
        List<VoteOptionDto> voteDtos = options.stream()
                .map(opt -> VoteOptionDto.builder()
                        .id(opt.getId())
                        .label(opt.getLabel())
                        .orderIdx(opt.getOrderIdx())
                        .build())
                .toList();

        return PostResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .bodyPublished(post.getBodyPublished())
                .category(post.getCategory() != null ? post.getCategory().name() : null)
                .visibility(post.getVisibility().name())
                .status(post.getStatus().name())
                .voteOptions(voteDtos)
                .createdAt(post.getCreatedAt())
                .build();
    }
}
