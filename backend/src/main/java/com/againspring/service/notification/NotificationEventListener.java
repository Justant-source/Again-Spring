package com.againspring.service.notification;

import com.againspring.domain.enums.NotificationType;
import com.againspring.service.notification.event.NewVoteEvent;
import com.againspring.service.notification.event.NewCommentEvent;
import com.againspring.service.notification.event.NewReplyEvent;
import com.againspring.service.notification.event.PartnerAnsweredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/**
 * 알림 이벤트 리스너 (C3 광장형)
 * 비즈니스 이벤트를 구독하여 알림을 생성합니다.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNewVote(NewVoteEvent event) {
        notificationService.createNotification(
            event.getUserId(),
            NotificationType.NEW_VOTE,
            "내 사연에 새 투표",
            event.getSubtitle(),
            event.getRefPostId(),
            null
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNewComment(NewCommentEvent event) {
        notificationService.createNotification(
            event.getUserId(),
            NotificationType.NEW_COMMENT,
            "댓글이 달렸어요",
            event.getSubtitle(),
            event.getRefPostId(),
            event.getRefCommentId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNewReply(NewReplyEvent event) {
        notificationService.createNotification(
            event.getUserId(),
            NotificationType.NEW_REPLY,
            "답글이 달렸어요",
            event.getSubtitle(),
            event.getRefPostId(),
            event.getRefCommentId()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPartnerAnswered(PartnerAnsweredEvent event) {
        notificationService.createNotification(
            event.getUserId(),
            NotificationType.PARTNER_ANSWERED,
            "상대가 답변을 남겼어요",
            event.getSubtitle(),
            event.getRefPostId(),
            null
        );
    }
}
