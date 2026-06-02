package com.againspring.api.community.dto;

import com.againspring.domain.community.PostComment;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 댓글 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommentResponse {

    private Long id;

    private String authorId;

    private String body;

    private Long likeCount;

    private Boolean isLiked;

    private Instant createdAt;

    private Boolean isAuthor;

    private Boolean isPartner;

    /**
     * PostComment로부터 CommentResponse 생성
     */
    public static CommentResponse from(PostComment comment, boolean isLiked) {
        return CommentResponse.builder()
                .id(comment.getId())
                .authorId(comment.getAuthorId())
                .body(comment.getBody())
                .likeCount((long) comment.getLikeCount())
                .isLiked(isLiked)
                .createdAt(comment.getCreatedAt())
                .build();
    }

    /**
     * PostComment로부터 CommentResponse 생성 (isAuthor, isPartner 포함)
     */
    public static CommentResponse from(PostComment comment, boolean isLiked, boolean isAuthor, boolean isPartner) {
        return CommentResponse.builder()
                .id(comment.getId())
                .authorId(comment.getAuthorId())
                .body(comment.getBody())
                .likeCount((long) comment.getLikeCount())
                .isLiked(isLiked)
                .createdAt(comment.getCreatedAt())
                .isAuthor(isAuthor)
                .isPartner(isPartner)
                .build();
    }
}
