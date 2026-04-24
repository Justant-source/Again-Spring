package com.againspring.service.graph;

import com.againspring.api.dto.response.graph.PersonRelationshipSummary;
import com.againspring.api.dto.response.graph.SessionHistoryItem;
import com.againspring.api.dto.response.graph.TemperatureEntry;
import com.againspring.domain.enums.ConflictType;
import com.againspring.domain.enums.RelationType;
import com.againspring.domain.relationship.ConflictHistory;
import com.againspring.domain.relationship.TemperatureHistory;
import com.againspring.domain.relationship.UserRelationship;
import com.againspring.repository.ConflictHistoryRepository;
import com.againspring.repository.TemperatureHistoryRepository;
import com.againspring.repository.UserRelationshipRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * SQL 관계 그래프 서비스 (Neo4j 대체)
 * UserRelationship, ConflictHistory, TemperatureHistory 엔티티 기반
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RelationshipGraphService {

    private final UserRelationshipRepository userRelationshipRepository;
    private final ConflictHistoryRepository conflictHistoryRepository;
    private final TemperatureHistoryRepository temperatureHistoryRepository;

    /**
     * 관계 엔트리 생성 또는 업데이트
     * 정규화: userA <= userB (ID 순서)
     */
    public UserRelationship upsertRelationship(String ownerUserId, String counterpartUserId,
                                                RelationType relationType) {
        // 정규화: 작은 ID를 userA로
        String userA = ownerUserId.compareTo(counterpartUserId) <= 0 ? ownerUserId : counterpartUserId;
        String userB = userA.equals(ownerUserId) ? counterpartUserId : ownerUserId;

        return userRelationshipRepository
                .findByUserAIdAndUserBIdAndRelationshipType(userA, userB, relationType)
                .orElseGet(() -> userRelationshipRepository.save(
                        UserRelationship.builder()
                                .userAId(userA)
                                .userBId(userB)
                                .relationshipType(relationType)
                                .sessionCount(0)
                                .averageTemperature(36.5)
                                .build()
                ));
    }

    /**
     * 갈등 기록 (세션 완료 시 호출)
     * UserRelationship 업데이트 및 ConflictHistory, TemperatureHistory 저장
     */
    public void recordConflict(String userAIdInput, String userBIdInput, RelationType relationType,
                               String sessionId, ConflictType conflictType, double temperature,
                               Instant startedAt, Instant endedAt) {

        // 정규화: 작은 ID를 userA로
        String userA = userAIdInput.compareTo(userBIdInput) <= 0 ? userAIdInput : userBIdInput;
        String userB = userA.equals(userAIdInput) ? userBIdInput : userAIdInput;

        // UserRelationship 업서트 및 온도 통계 업데이트
        UserRelationship rel = upsertRelationship(userA, userB, relationType);
        rel.setLastSessionAt(endedAt);
        if (rel.getFirstSessionAt() == null) {
            rel.setFirstSessionAt(startedAt);
        }

        int count = rel.getSessionCount() != null ? rel.getSessionCount() : 0;
        double prevAvg = rel.getAverageTemperature() != null ? rel.getAverageTemperature() : temperature;
        double newAvg = (prevAvg * count + temperature) / (count + 1);

        rel.setSessionCount(count + 1);
        rel.setAverageTemperature(newAvg);
        userRelationshipRepository.save(rel);

        // ConflictHistory 저장
        conflictHistoryRepository.save(ConflictHistory.builder()
                .sessionId(sessionId)
                .userAId(userA)
                .userBId(userB)
                .relationshipType(relationType.name())
                .conflictType(conflictType != null ? conflictType.name() : null)
                .temperature(temperature)
                .createdAt(endedAt)
                .build());

        // TemperatureHistory 저장 (양방향)
        temperatureHistoryRepository.save(TemperatureHistory.builder()
                .userId(userA)
                .relatedUserId(userB)
                .sessionId(sessionId)
                .temperature(temperature)
                .recordedAt(endedAt)
                .build());

        temperatureHistoryRepository.save(TemperatureHistory.builder()
                .userId(userB)
                .relatedUserId(userA)
                .sessionId(sessionId)
                .temperature(temperature)
                .recordedAt(endedAt)
                .build());

        log.info("Recorded conflict for users {} and {} in session {} (temp={})",
                userA, userB, sessionId, temperature);
    }

    /**
     * 사용자의 모든 관계 목록 (대시보드용)
     */
    public List<PersonRelationshipSummary> listRelationships(String userId) {
        List<UserRelationship> relationships =
                userRelationshipRepository.findByUserAIdOrUserBIdOrderByLastSessionAtDesc(userId, userId);

        return relationships.stream()
                .map(rel -> {
                    String counterpartId = rel.getUserAId().equals(userId) ? rel.getUserBId() : rel.getUserAId();
                    return PersonRelationshipSummary.builder()
                            .personId(rel.getId())  // relationship ID as identifier
                            .personNickname(counterpartId)  // fallback to ID since we don't have display name in SQL
                            .relationType(rel.getRelationshipType())
                            .sessionCount(rel.getSessionCount() != null ? rel.getSessionCount() : 0)
                            .averageTemperature(rel.getAverageTemperature() != null ? rel.getAverageTemperature() : 36.5)
                            .lastSessionAt(rel.getLastSessionAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 특정 사용자와의 갈등 이력 조회 (최신순)
     */
    public List<SessionHistoryItem> historyWith(String userId, String counterpartUserId) {
        // 정규화
        String userA = userId.compareTo(counterpartUserId) <= 0 ? userId : counterpartUserId;
        String userB = userA.equals(userId) ? counterpartUserId : userId;

        List<ConflictHistory> conflicts = conflictHistoryRepository
                .findByUserAIdAndUserBIdOrderByCreatedAtDesc(userA, userB);

        return conflicts.stream()
                .map(conflict -> SessionHistoryItem.builder()
                        .sessionId(conflict.getSessionId())
                        .temperature(conflict.getTemperature())
                        .conflictType(conflict.getConflictType() != null
                                ? ConflictType.valueOf(conflict.getConflictType())
                                : null)
                        .createdAt(conflict.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 관계 온도 추이 타임라인 (그래프용)
     */
    public List<TemperatureEntry> temperatureTimeline(String userId, String counterpartUserId) {
        List<TemperatureHistory> temperatures = temperatureHistoryRepository
                .findByUserIdAndRelatedUserIdOrderByRecordedAtAsc(userId, counterpartUserId);

        return temperatures.stream()
                .map(temp -> TemperatureEntry.builder()
                        .date(temp.getRecordedAt().toString())
                        .temperature(temp.getTemperature())
                        .build())
                .collect(Collectors.toList());
    }
}
