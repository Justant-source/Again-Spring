package com.againspring.api.community.dto;

import com.againspring.domain.community.PostComment;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommentResponse {

    private Long id;
    private String authorId;
    private String authorNickname;
    private String body;
    private Long likeCount;
    private Boolean isLiked;
    private Instant createdAt;
    private Boolean isAuthor;
    private Boolean isPartner;

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

    public static CommentResponse from(PostComment comment, boolean isLiked, boolean isAuthor, boolean isPartner) {
        return from(comment, isLiked, isAuthor, isPartner, null);
    }

    public static CommentResponse from(PostComment comment, boolean isLiked, boolean isAuthor, boolean isPartner, String authorNickname) {
        return CommentResponse.builder()
                .id(comment.getId())
                .authorId(comment.getAuthorId())
                .authorNickname(authorNickname)
                .body(comment.getBody())
                .likeCount((long) comment.getLikeCount())
                .isLiked(isLiked)
                .createdAt(comment.getCreatedAt())
                .isAuthor(isAuthor)
                .isPartner(isPartner)
                .build();
    }
}
