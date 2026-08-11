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
    /** 마케팅 훅 제목 (≤20자). 생성 전이면 null — resolveOrFallback 사용. */
    private String promoTitle;
    /** 메타포 일러스트 ID (60종). 없으면 null. */
    private String metaphorId;
    /** 메타포 일러스트 ID 목록 (3-5개, 첫번째 = 대표). 없으면 null. */
    private List<String> metaphorIds;
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
    /** 현재 사용자가 투표한 진영: "g"=작성자, "r"=상대방, null=미투표 */
    private String myVoteSide;
    private Boolean deleted;
    private Boolean authorBodyDeleted;
    private Boolean partnerBodyDeleted;

    public static PostResponse from(Post post, List<VoteOption> options) {
        return from(post, options, 0L, 0L, null, null);
    }

    public static PostResponse from(Post post, List<VoteOption> options, Long voteCount, Long commentCount, String authorNickname) {
        return from(post, options, voteCount, commentCount, authorNickname, null);
    }

    public static PostResponse from(Post post, List<VoteOption> options, Long voteCount, Long commentCount, String authorNickname, Long votedOptionId) {
        if (post.getDeletedAt() != null) {
            return PostResponse.builder()
                    .id(post.getId())
                    .deleted(true)
                    .build();
        }

        boolean authorBodyDeleted = post.getAuthorBodyDeletedAt() != null;
        boolean partnerBodyDeleted = post.getPartnerBodyDeletedAt() != null;
        String bodyPublished = authorBodyDeleted ? null : post.getBodyPublished();

        List<VoteOptionDto> voteDtos = options.stream()
                .map(opt -> VoteOptionDto.builder()
                        .id(opt.getId())
                        .label(opt.getLabel())
                        .orderIdx(opt.getOrderIdx())
                        .build())
                .toList();

        String myVoteSide = null;
        if (votedOptionId != null) {
            myVoteSide = voteDtos.stream()
                    .filter(o -> o.getId().equals(votedOptionId))
                    .findFirst()
                    .map(o -> o.getOrderIdx() == 0 ? "g" : "r")
                    .orElse(null);
        }

        return PostResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .userTitle(post.getUserTitle())
                .promoTitle(post.getPromoTitle())
                .metaphorId(post.getMetaphorId())
                .metaphorIds(post.getMetaphorIds())
                .bodyPublished(bodyPublished)
                .category(post.getCategory() != null ? post.getCategory().name() : null)
                .visibility(post.getVisibility().name())
                .status(post.getStatus().name())
                .voteOptions(voteDtos)
                .createdAt(post.getCreatedAt())
                .voteCount(voteCount)
                .commentCount(commentCount)
                .viewCount(post.getViewCount() != null ? post.getViewCount().longValue() : 0L)
                .authorNickname(authorNickname)
                .paired(!partnerBodyDeleted && post.getPartnerAnsweredAt() != null && post.getPartnerBodyPublished() != null)
                .myVoteSide(myVoteSide)
                .deleted(false)
                .authorBodyDeleted(authorBodyDeleted)
                .partnerBodyDeleted(partnerBodyDeleted)
                .build();
    }
}
