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

    /** FE 배심원 폴링 종료 조건: 기대 배심원 수 */
    private Integer jurorCount;

    /**
     * Post + VoteOption + 투표 결과로부터 PostDetailResponse 생성
     */
    public static PostDetailResponse from(Post post, List<VoteOption> options,
                                         Map<Long, Long> voteResult, Optional<Long> myVote,
                                         long commentCount) {
        List<VoteOptionDto> voteDtos = options.stream()
                .map(opt -> VoteOptionDto.builder()
                        .id(opt.getId())
                        .label(opt.getLabel())
                        .orderIdx(opt.getOrderIdx())
                        .build())
                .toList();

        long totalVotes = voteResult.values().stream().mapToLong(Long::longValue).sum();

        List<VoteOptionResultDto> voteResultDtos = options.stream()
                .map(opt -> {
                    long count = voteResult.getOrDefault(opt.getId(), 0L);
                    double percentage = totalVotes > 0 ? (count * 100.0) / totalVotes : 0.0;
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
                .bodyPublished(post.getBodyPublished())
                .category(post.getCategory() != null ? post.getCategory().name() : null)
                .visibility(post.getVisibility().name())
                .status(post.getStatus().name())
                .voteOptions(voteDtos)
                .isVoted(myVote.isPresent())
                .myVotedOptionId(myVote.orElse(null))
                .voteResult(voteResultResponse)
                .createdAt(post.getCreatedAt())
                .commentCount(commentCount)
                .jurorCount(post.getJurorCount())
                .build();
    }
}
