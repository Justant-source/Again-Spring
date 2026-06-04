package com.againspring.aiuser.orchestrator.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.util.List;

/**
 * 백엔드 GET /api/community/posts/{postId}/comments 응답의 댓글 항목.
 * 기존 CommunityCommentController.CommentWithRepliesResponse 구조를 느슨하게 미러링.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommentThreadDto {
    private Long id;
    private String body;
    private String authorNickname;
    private boolean isAuthor;
    private boolean isPartner;
    private int likeCount;
    private List<CommentThreadDto> replies;
}
