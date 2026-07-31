package com.againspring.aiuser.orchestrator.scheduler;
import com.againspring.aiuser.orchestrator.service.threadplan.ScheduledPostPublisher;
import lombok.RequiredArgsConstructor; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor public class ScheduledPostPublisherScheduler { private final ScheduledPostPublisher publisher; @Scheduled(cron = "${ai-user.thread-plan.scheduled-post-publisher-cron:0 * * * * *}") public void publish() { publisher.publishDue(); } }
