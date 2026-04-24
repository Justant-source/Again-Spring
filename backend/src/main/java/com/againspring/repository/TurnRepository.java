package com.againspring.repository;

import com.againspring.domain.Turn;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 턴 저장소 (JPA/MariaDB)
 */
@Repository
public interface TurnRepository extends JpaRepository<Turn, Long> {

    /**
     * 세션의 모든 턴 조회 (턴 번호순)
     */
    List<Turn> findBySessionIdOrderByTurnNumberAsc(String sessionId);
}
