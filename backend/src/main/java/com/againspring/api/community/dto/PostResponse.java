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

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostResponse {

    private String id;
    private String title;
    private String userTitle;
    private String bodyPublished;
    private String category;
    private String visibility;
    private String status;
    private List<VoteOptionDto> voteOptions;
    private Instant createdAt;
    private Long voteCount;
    private Long commentCount;
    private Long viewCount;
    private String authorNickname;
    private Boolean paired;

    public static PostResponse from(Post post, List<VoteOption> options) {
        return from(post, options, 0L, 0L, null);
    }

    public static PostResponse from(Post post, List<VoteOption> options, Long voteCount, Long commentCount, String authorNickname) {
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
                .userTitle(post.getUserTitle())
                .bodyPublished(post.getBodyPublished())
                .category(post.getCategory() != null ? post.getCategory().name() : null)
                .visibility(post.getVisibility().name())
                .status(post.getStatus().name())
                .voteOptions(voteDtos)
                .createdAt(post.getCreatedAt())
                .voteCount(voteCount)
                .commentCount(commentCount)
                .viewCount(post.getViewCount() != null ? post.getViewCount().longValue() : 0L)
                .authorNickname(authorNickname)
                .paired(post.getPartnerAnsweredAt() != null && post.getPartnerBodyPublished() != null)
                .build();
    }
}
