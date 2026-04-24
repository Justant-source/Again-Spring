package com.againspring.repository;

import com.againspring.domain.relationship.ConflictHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 갈등 이력 저장소 (JPA/MariaDB)
 */
@Repository
public interface ConflictHistoryRepository extends JpaRepository<ConflictHistory, Long> {

    /**
     * 두 사용자 간의 갈등 이력 조회 (생성순 역순)
     */
    List<ConflictHistory> findByUserAIdAndUserBIdOrderByCreatedAtDesc(String userAId, String userBId);

    /**
     * 세션의 갈등 이력 조회
     */
    List<ConflictHistory> findBySessionId(String sessionId);
}
