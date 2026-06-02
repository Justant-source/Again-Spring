package com.againspring.domain.notification;

import com.againspring.domain.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * 알림 (C3 광장형)
 * 사용자가 받은 모든 알림 기록
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
public class Notification {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 32)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String subtitle;

    @Column(length = 32)
    private String refPostId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = "noti_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        }
    }
}
