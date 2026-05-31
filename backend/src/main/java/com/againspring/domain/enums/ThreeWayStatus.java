package com.againspring.domain.enums;

/**
 * 3자 중재 세션 상태 (V17 커뮤니티)
 * WAITING: 상대 초대 대기 중
 * ACTIVE: 양쪽 모두 참여 중 (채팅/투표)
 * CLOSED: 세션 종료
 */
public enum ThreeWayStatus {
    WAITING,
    ACTIVE,
    CLOSED
}
