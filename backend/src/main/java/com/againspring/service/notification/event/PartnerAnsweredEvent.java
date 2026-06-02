package com.againspring.service.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 상대방 답변 완료 이벤트
 */
@Getter
public class PartnerAnsweredEvent extends ApplicationEvent {

    private final String userId;
    private final String refPostId;
    private final String subtitle;

    public PartnerAnsweredEvent(Object source, String userId, String refPostId, String subtitle) {
        super(source);
        this.userId = userId;
        this.refPostId = refPostId;
        this.subtitle = subtitle;
    }
}
