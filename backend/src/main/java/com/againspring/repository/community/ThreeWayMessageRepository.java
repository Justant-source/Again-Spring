package com.againspring.repository.community;

import com.againspring.domain.community.ThreeWayMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 3자 중재 세션 메시지 저장소 (V17 커뮤니티)
 */
@Repository
public interface ThreeWayMessageRepository extends JpaRepository<ThreeWayMessage, Long> {

    /**
     * 3자 세션의 메시지 조회 (생성순)
     */
    List<ThreeWayMessage> findByTwsIdOrderByCreatedAtAsc(String twsId);
}
