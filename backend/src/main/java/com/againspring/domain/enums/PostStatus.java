package com.againspring.domain.enums;

/**
 * 포스트 상태
 * DRAFT:   초안 (미사용 — 향후 임시저장용)
 * VOTING:  투표 진행 중 (등록 즉시 이 상태)
 * CLOSED:  투표 종료
 * BLOCKED: 커뮤니티 정책 위반으로 차단
 */
public enum PostStatus {
    DRAFT,
    VOTING,
    CLOSED,
    BLOCKED
}
