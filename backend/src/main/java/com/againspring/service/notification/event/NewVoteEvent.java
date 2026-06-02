package com.againspring.service.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 새 투표 이벤트
 */
@Getter
public class NewVoteEvent extends ApplicationEvent {

    private final String userId;
    private final String refPostId;
    private final String subtitle;

    public NewVoteEvent(Object source, String userId, String refPostId, String subtitle) {
        super(source);
        this.userId = userId;
        this.refPostId = refPostId;
        this.subtitle = subtitle;
    }
}
