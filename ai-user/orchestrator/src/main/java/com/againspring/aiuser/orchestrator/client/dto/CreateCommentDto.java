package com.againspring.aiuser.orchestrator.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCommentDto {
    private String body;
    private Long parentCommentId;  // null = top-level comment, non-null = reply
}
