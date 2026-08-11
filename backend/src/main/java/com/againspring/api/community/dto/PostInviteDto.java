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
     * 초대 토큰으로 조회한 포스트 정보 (파트너 소유권·권한 포함)
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
        private boolean deleted;
        /** NONE | ACTIVE | TOMBSTONE */
        private String partnerState;
        /** UNOWNED | OWNED | OWNED_BY_OTHER | AUTHOR */
        private String ownership;
        private String partnerBodyPublished;
        private boolean canWrite;
        private boolean canEdit;
        private boolean canDelete;
        private boolean canClaim;
    }

    /**
     * 파트너 답변 제출/수정 요청
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PartnerAnswerRequest {
        private String userTitle;
        private String bodyRaw;
        /** Partner capture cuts (1-based); optional from AI Call2. */
        @com.fasterxml.jackson.annotation.JsonAlias({"capture_split_after_lines", "partner_capture_split_after_lines"})
        private java.util.List<Integer> captureSplitAfterLines;
    }

    /**
     * 발행 모드 설정 요청.
     * {@code voteDurationHours}는 deprecated — 서버에서 무시(시한부 투표 제거).
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PublishModeRequest {
        private String mode;
        /** Legacy — 시한부 투표 제거. API 호환용으로만 유지, 무시됨. */
        private Integer voteDurationHours;
    }
}
