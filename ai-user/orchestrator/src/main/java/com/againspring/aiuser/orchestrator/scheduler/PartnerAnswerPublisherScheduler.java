package com.againspring.aiuser.orchestrator.scheduler;

import com.againspring.aiuser.orchestrator.service.threadplan.PartnerAnswerPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PartnerAnswerPublisherScheduler {
    private final PartnerAnswerPublisher publisher;

    @Scheduled(cron = "${ai-user.paired-post.partner-publisher-cron:0 * * * * *}")
    public void publish() {
        publisher.publishDue();
    }
}
