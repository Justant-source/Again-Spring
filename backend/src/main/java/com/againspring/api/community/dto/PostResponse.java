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
    /** SNS 마스터 훅 (도발적, IG 개행 허용). 생성 전이면 null — resolveOrFallback 사용. */
    private String promoTitle;
    /** 마스터 훅 감정: shock|anger|tension|sad|hype. 없으면 null. */
    private String hookEmotion;
    /** 메타포 일러스트 ID (60종). 레거시 — 영상 경로 무시. 없으면 null. */
    private String metaphorId;
    /** 메타포 일러스트 ID 목록 (3-5개, 첫번째 = 대표). 레거시 — 영상 경로 무시. */
    private List<String> metaphorIds;
    /** 시봄이 캐릭터 id 숏리스트(≤12). 본문 keyword 스코어. */
    private List<String> sibomCandidates;
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
    /** 작성자(orderIdx=0) 공감 비율 0–100. 표 없으면 null(FE는 중립 50). */
    private Integer authorPct;
    /** 상대방 공감 비율 = 100 - authorPct. 표 없으면 null. */
    private Integer partnerPct;
    private Boolean deleted;
    private Boolean authorBodyDeleted;
    private Boolean partnerBodyDeleted;

    public static PostResponse from(Post post, List<VoteOption> options) {
        return from(post, options, 0L, 0L, null, null, null);
    }

    public static PostResponse from(Post post, List<VoteOption> options, Long voteCount, Long commentCount, String authorNickname) {
        return from(post, options, voteCount, commentCount, authorNickname, null, null);
    }

    public static PostResponse from(Post post, List<VoteOption> options, Long voteCount, Long commentCount, String authorNickname, Long votedOptionId) {
        return from(post, options, voteCount, commentCount, authorNickname, votedOptionId, null);
    }

    /**
     * @param authorOptionVoteCount orderIdx=0(작성자) 표 수. null이면 authorPct 미계산.
     */
    public static PostResponse from(Post post, List<VoteOption> options, Long voteCount, Long commentCount,
                                    String authorNickname, Long votedOptionId, Long authorOptionVoteCount) {
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

        Integer authorPct = null;
        Integer partnerPct = null;
        if (voteCount != null && voteCount > 0 && authorOptionVoteCount != null) {
            authorPct = (int) Math.round(authorOptionVoteCount * 100.0 / voteCount);
            partnerPct = 100 - authorPct;
        }

        return PostResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .userTitle(post.getUserTitle())
                .promoTitle(post.getPromoTitle())
                .hookEmotion(post.getHookEmotion())
                .metaphorId(post.getMetaphorId())
                .metaphorIds(post.getMetaphorIds())
                .sibomCandidates(post.getSibomCandidates())
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
                .authorPct(authorPct)
                .partnerPct(partnerPct)
                .deleted(false)
                .authorBodyDeleted(authorBodyDeleted)
                .partnerBodyDeleted(partnerBodyDeleted)
                .build();
    }
}
