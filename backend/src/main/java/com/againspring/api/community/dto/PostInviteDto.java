package com.againspring.api.community.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * PostInviteDto - C3 초대 관련 DTO 모음
 */
public class PostInviteDto {

    /**
     * 초대 생성 응답
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InviteResponse {
        private String inviteToken;
        private String inviteUrl;
    }

    /**
     * 초대 토큰으로 조회한 포스트 정보
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostByTokenResponse {
        private String postId;
        private String userTitle;
        private String authorBodyPublished;
        private String category;
    }

    /**
     * 파트너 답변 제출 요청
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartnerAnswerRequest {
        private String userTitle;
        private String bodyRaw;
    }

    /**
     * 발행 모드 설정 요청
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublishModeRequest {
        private String mode;
        private Integer voteDurationHours;
    }
}
