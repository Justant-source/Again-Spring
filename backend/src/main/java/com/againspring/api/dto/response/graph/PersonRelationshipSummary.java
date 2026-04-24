package com.againspring.api.dto.response.graph;

import com.againspring.domain.enums.RelationType;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관계 요약 (대시보드용)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonRelationshipSummary {

    private Long personId; // Neo4j PersonNode ID

    private String personNickname; // 상대방 이름

    private RelationType relationType; // couple, friend, family, etc.

    private int sessionCount; // 이 사람과의 세션 횟수

    private double averageTemperature; // 평균 관계 온도

    private Instant lastSessionAt; // 마지막 세션 날짜

}
