package com.againspring.repository;

import com.againspring.domain.Report;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 리포트 저장소 (JPA/MariaDB)
 * JSON 컬럼 기반 참여자 필터링은 네이티브 쿼리로 처리
 */
@Repository
public interface ReportRepository extends JpaRepository<Report, String> {

    /**
     * 세션 ID로 리포트 조회 (1:1 관계)
     */
    Optional<Report> findBySessionId(String sessionId);

    /**
     * 사용자의 모든 리포트 조회 (A 또는 B 참여자, 생성순 역순)
     * participantA.userId 또는 participantB.userId와 매칭
     */
    @Query(value = "SELECT * FROM reports r WHERE "
            + "JSON_EXTRACT(r.participant_a, '$.userId') = :userId OR "
            + "JSON_EXTRACT(r.participant_b, '$.userId') = :userId "
            + "ORDER BY r.created_at DESC",
            nativeQuery = true)
    List<Report> findByParticipantUserId(@Param("userId") String userId);
}
