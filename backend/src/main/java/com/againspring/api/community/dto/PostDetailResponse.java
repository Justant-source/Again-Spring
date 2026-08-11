package com.againspring.api.community.dto;

import com.againspring.domain.community.Post;
import com.againspring.domain.community.VoteOption;
import com.againspring.service.community.VoteCountBreakdown;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 포스트 상세 응답 DTO (투표 결과 포함)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PostDetailResponse {

    private String id;

    private String title;

    private String promoTitle;

    private String metaphorId;

    private List<String> metaphorIds;

    private String bodyPublished;

    private String category;

    private String visibility;

    private String status;

    private List<VoteOptionDto> voteOptions;

    private Boolean isVoted;

    private Long myVotedOptionId;

    private VoteResultResponse voteResult;

    private Instant createdAt;

    private Long commentCount;

    private Long viewCount;

    private Boolean paired;

    private String partnerBodyPublished;

    private Instant partnerAnsweredAt;

    private String inviteToken;

    /** 요청자가 작성자이면 true — 작성자 전용 UI 노출 여부 결정 */
    private Boolean isAuthor;

    private String authorNickname;

    private String partnerNickname;

    private Boolean isPartner;

    /** 포스트 soft full-delete 여부 ({@code deletedAt != null}) */
    private Boolean deleted;

    /** 작성자 본문 tombstone */
    private Boolean authorBodyDeleted;

    /** 상대 본문 tombstone */
    private Boolean partnerBodyDeleted;

    /** Soft-deleted 포스트용 최소 응답 */
    public static PostDetailResponse deleted(String id) {
        return PostDetailResponse.builder()
                .id(id)
                .deleted(true)
                .build();
    }

    /**
     * Post + VoteOption + 투표 결과로부터 PostDetailResponse 생성 (가중치 적용)
     * @param voteResultWithBreakdown 옵션별 사람/AI 카운트
     * @param weightedPercentages 가중치 적용된 비율 (%)
     */
    public static PostDetailResponse from(Post post, List<VoteOption> options,
                                         Map<Long, VoteCountBreakdown> voteResultWithBreakdown,
                                         Map<Long, Double> weightedPercentages,
                                         Optional<Long> myVote,
                                         long commentCount, String requestUserId,
                                         String authorNickname, String partnerNickname,
                                         boolean isPartner) {
        if (post.getDeletedAt() != null) {
            return deleted(post.getId());
        }

        boolean authorBodyDeleted = post.getAuthorBodyDeletedAt() != null;
        boolean partnerBodyDeleted = post.getPartnerBodyDeletedAt() != null;
        String bodyPublished = authorBodyDeleted ? null : post.getBodyPublished();
        String partnerBodyPublished = partnerBodyDeleted ? null : post.getPartnerBodyPublished();

        List<VoteOptionDto> voteDtos = options.stream()
                .map(opt -> VoteOptionDto.builder()
                        .id(opt.getId())
                        .label(opt.getLabel())
                        .orderIdx(opt.getOrderIdx())
                        .build())
                .toList();

        long totalVotes = voteResultWithBreakdown.values().stream()
                .mapToLong(VoteCountBreakdown::getTotalCount)
                .sum();

        List<VoteOptionResultDto> voteResultDtos = options.stream()
                .map(opt -> {
                    long count = voteResultWithBreakdown.getOrDefault(opt.getId(), new VoteCountBreakdown(0L, 0L)).getTotalCount();
                    double percentage = weightedPercentages.getOrDefault(opt.getId(), 0.0);
                    return VoteOptionResultDto.builder()
                            .id(opt.getId())
                            .label(opt.getLabel())
                            .count(count)
                            .percentage(percentage)
                            .build();
                })
                .toList();

        VoteResultResponse voteResultResponse = VoteResultResponse.builder()
                .options(voteResultDtos)
                .totalVotes(totalVotes)
                .myVotedOptionId(myVote.orElse(null))
                .build();

        return PostDetailResponse.builder()
                .id(post.getId())
                .title(post.getTitle())
                .promoTitle(post.getPromoTitle())
                .metaphorId(post.getMetaphorId())
                .metaphorIds(post.getMetaphorIds())
                .bodyPublished(bodyPublished)
                .category(post.getCategory() != null ? post.getCategory().name() : null)
                .visibility(post.getVisibility().name())
                .status(post.getStatus().name())
                .voteOptions(voteDtos)
                .isVoted(myVote.isPresent())
                .myVotedOptionId(myVote.orElse(null))
                .voteResult(voteResultResponse)
                .createdAt(post.getCreatedAt())
                .commentCount(commentCount)
                .viewCount(post.getViewCount() != null ? post.getViewCount().longValue() : 0L)
                .paired(!partnerBodyDeleted && post.getPartnerAnsweredAt() != null && partnerBodyPublished != null)
                .partnerBodyPublished(partnerBodyPublished)
                .partnerAnsweredAt(post.getPartnerAnsweredAt())
                .inviteToken(post.getInviteToken())
                .isAuthor(requestUserId != null && requestUserId.equals(post.getAuthorId()))
                .authorNickname(authorNickname)
                .partnerNickname(partnerNickname)
                .isPartner(isPartner)
                .deleted(false)
                .authorBodyDeleted(authorBodyDeleted)
                .partnerBodyDeleted(partnerBodyDeleted)
                .build();
    }
}
