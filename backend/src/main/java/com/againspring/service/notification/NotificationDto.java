package com.againspring.service.notification;

import com.againspring.domain.notification.Notification;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 알림 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {

    private String id;
    private String type;
    private String title;
    private String subtitle;
    private String refPostId;
    private Boolean isRead;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant createdAt;

    public static NotificationDto from(Notification notification) {
        return NotificationDto.builder()
            .id(notification.getId())
            .type(notification.getType().name())
            .title(notification.getTitle())
            .subtitle(notification.getSubtitle())
            .refPostId(notification.getRefPostId())
            .isRead(notification.getIsRead())
            .createdAt(notification.getCreatedAt())
            .build();
    }
}
