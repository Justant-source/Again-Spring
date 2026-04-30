package com.againspring.seed.dto;

/**
 * 시드 메시지 DTO
 * Message 엔티티 정의
 */
public record SeedMessage(
    String sender,           // "USER_A", "USER_B", "MEDIATOR_TO_A", "MEDIATOR_TO_B"
    String content,
    long deltaMinutes,       // 세션 시작으로부터 N분 후 (단조 증가)
    boolean isFinalizeSuggestion,
    boolean isPartnerJoinNotice,
    Integer crisisLevel      // null이면 없음
) {}
