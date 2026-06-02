package com.againspring.api.community.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 댓글 + 대댓글 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommentWithRepliesResponse {

    private Long id;

    private String authorId;

    private String body;

    private Long likeCount;

    private Boolean isLiked;

    private Instant createdAt;

    private List<CommentResponse> replies;

    private Boolean isAuthor;

    private Boolean isPartner;
}
