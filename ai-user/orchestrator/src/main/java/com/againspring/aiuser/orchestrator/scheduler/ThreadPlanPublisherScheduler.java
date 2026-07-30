package com.againspring.aiuser.orchestrator.scheduler;
import com.againspring.aiuser.orchestrator.service.threadplan.ThreadPlanPublisher;
import lombok.RequiredArgsConstructor; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor public class ThreadPlanPublisherScheduler { private final ThreadPlanPublisher publisher; @Scheduled(cron = "${ai-user.thread-plan.publisher-cron:0 * * * * *}") public void publish() { publisher.publishDue(); } }
