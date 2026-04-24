package com.againspring.repository;

import com.againspring.domain.relationship.UserRelationship;
import com.againspring.domain.enums.RelationType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 사용자 관계 저장소 (JPA/MariaDB)
 * userA <= userB (정규화된 순서)
 */
@Repository
public interface UserRelationshipRepository extends JpaRepository<UserRelationship, Long> {

    /**
     * 두 사용자 간의 특정 관계 타입 조회 (중복 제거를 위한 정규화된 순서)
     */
    Optional<UserRelationship> findByUserAIdAndUserBIdAndRelationshipType(
            String userAId, String userBId, RelationType relationshipType);

    /**
     * 사용자의 모든 관계 조회 (A 또는 B, 최신 세션순)
     * 대시보드용 관계 목록
     */
    List<UserRelationship> findByUserAIdOrUserBIdOrderByLastSessionAtDesc(String userAId, String userBId);
}
