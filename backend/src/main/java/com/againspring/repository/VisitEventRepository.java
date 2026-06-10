package com.againspring.repository;

import com.againspring.domain.VisitEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 방문 이벤트 저장소
 */
@Repository
public interface VisitEventRepository extends JpaRepository<VisitEvent, Long> {
}
