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
public class CreatePostDto {
    private String userTitle;
    private String bodyRaw;
    private String category;   // PostCategory enum name: COUPLE/MARRIED/FRIEND/FAMILY/WORK/OTHER
    private String visibility; // "PUBLIC"
    @Builder.Default
    private int jurorCount = 0;  // AI 배심원 모드 숨김 처리 — 0으로 고정
}
