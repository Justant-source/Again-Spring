package com.againspring.service.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NewReplyEvent extends ApplicationEvent {

    private final String userId;
    private final String refPostId;
    private final Long refCommentId;
    private final String subtitle;

    public NewReplyEvent(Object source, String userId, String refPostId, Long refCommentId, String subtitle) {
        super(source);
        this.userId = userId;
        this.refPostId = refPostId;
        this.refCommentId = refCommentId;
        this.subtitle = subtitle;
    }
}
