package com.againspring.seed.dto;

import java.util.List;

/**
 * 시드 시나리오 DTO
 * 각 테스트 시나리오의 메타데이터 + 메시지 + 리포트 정의
 */
public record SeedScenario(
    String id,                        // "S01"
    String ownerEmail,                // "test1@again.com"
    String relationType,              // RelationType enum name: "KOREAN_SPECIFIC"
    String categoryMajor,             // "korean_specific"
    String categoryMiddle,            // "in_law"
    String categoryMinor,             // "marriage_chores"
    String status,                    // SessionStatus enum name: "COMPLETED"
    boolean soloMode,
    long sessionCreatedMinutesAgo,    // 현재로부터 N분 전
    Long partnerJoinedMinutesAgo,     // null이면 솔로 (세션 시작으로부터 N분 후에 파트너 합류)
    String inviteeGuestName,          // null이면 없음
    boolean finalizeSuggestedSet,     // true면 finalizeSuggestedAt = completedAt - 2분
    boolean finalizeAgreedByA,
    boolean finalizeAgreedByB,
    List<String> crisisFlags,
    String inviteToken,               // null이면 없음
    List<SeedMessage> messages,
    SeedReport report                 // null이면 Report INSERT 안 함
) {}
