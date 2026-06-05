package com.againspring.service;

import com.againspring.domain.enums.NotificationType;
import com.againspring.domain.notification.Notification;
import com.againspring.repository.notification.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 작성 서비스 (공지사항, 관리자 브로드캐스트 등에서 사용)
 */
@Service
@RequiredArgsConstructor
public class NotificationWriteService {

    private final NotificationRepository notificationRepository;

    /**
     * 사용자에게 알림 발송
     *
     * @param userId 수신 사용자 ID
     * @param type 알림 종류
     * @param title 제목
     * @param subtitle 부제목
     * @param refPostId 참조 포스트 ID (선택사항)
     */
    @Transactional
    public void send(String userId, NotificationType type, String title, String subtitle, String refPostId) {
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
     * 사용자에게 알림 발송 (refCommentId 포함)
     */
    @Transactional
    public void send(String userId, NotificationType type, String title, String subtitle, String refPostId, Long refCommentId) {
        Notification notification = Notification.builder()
            .userId(userId)
            .type(type)
            .title(title)
            .subtitle(subtitle)
            .refPostId(refPostId)
            .refCommentId(refCommentId)
            .isRead(false)
            .build();

        notificationRepository.save(notification);
    }
}
