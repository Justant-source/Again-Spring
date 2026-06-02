package com.againspring.service.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 새 댓글 이벤트
 */
@Getter
public class NewCommentEvent extends ApplicationEvent {

    private final String userId;
    private final String refPostId;
    private final String subtitle;

    public NewCommentEvent(Object source, String userId, String refPostId, String subtitle) {
        super(source);
        this.userId = userId;
        this.refPostId = refPostId;
        this.subtitle = subtitle;
    }
}
