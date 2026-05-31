package com.againspring.domain.enums;

/**
 * 포스트 상태 (V17 커뮤니티)
 * DRAFT: 초안
 * NEUTRALIZING: AI 중립화 처리 중
 * VOTING: 투표 진행 중
 * CLOSED: 투표 종료
 * BLOCKED: 커뮤니티 정책 위반으로 차단
 */
public enum PostStatus {
    DRAFT,
    NEUTRALIZING,
    VOTING,
    CLOSED,
    BLOCKED
}
