package com.againspring.service.notification;

import com.againspring.domain.enums.NotificationType;
import com.againspring.domain.notification.Notification;
import com.againspring.repository.notification.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 알림 서비스 (C3 광장형)
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * 사용자의 알림 목록 조회 (최신 50개)
     */
    @Transactional(readOnly = true)
    public List<NotificationDto> getNotifications(String userId) {
        return notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(NotificationDto::from)
            .collect(Collectors.toList());
    }

    /**
     * 사용자의 모든 알림을 읽음으로 표시
     */
    @Transactional
    public void markAllRead(String userId) {
        notificationRepository.markAllReadByUserId(userId);
    }

    /**
     * 새 알림 생성
     */
    @Transactional
    public void createNotification(String userId, NotificationType type, String title, String subtitle, String refPostId) {
        Notification notification = Notification.builder()
            .userId(userId)
            .type(type)
            .title(title)
            .subtitle(subtitle)
            .refPostId(refPostId)
            .isRead(false)
            .build();

        notificationRepository.save(notification);
    }

    /**
     * 읽지 않은 알림 개수 조회
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }
}
