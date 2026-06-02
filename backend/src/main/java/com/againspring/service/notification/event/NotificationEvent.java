package com.againspring.service.notification.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 알림 이벤트 기반 클래스
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public abstract class NotificationEvent {

    private String userId;
    private String refPostId;
}
