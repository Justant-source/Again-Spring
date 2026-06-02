package com.againspring.repository.notification;

import com.againspring.domain.notification.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 알림 저장소
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, String> {

    /**
     * 사용자의 최신 알림 50개 조회
     */
    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * 사용자의 읽지 않은 알림 개수
     */
    long countByUserIdAndIsReadFalse(String userId);

    /**
     * 사용자의 모든 알림을 읽음으로 표시
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId")
    void markAllReadByUserId(@Param("userId") String userId);
}
