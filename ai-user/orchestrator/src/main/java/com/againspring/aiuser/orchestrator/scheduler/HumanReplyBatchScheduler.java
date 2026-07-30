package com.againspring.aiuser.orchestrator.scheduler;
import com.againspring.aiuser.orchestrator.service.threadplan.HumanReplyBatchService; import lombok.RequiredArgsConstructor; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor public class HumanReplyBatchScheduler { private final HumanReplyBatchService service; @Scheduled(cron = "${ai-user.thread-plan.human-reply-cron:0 */30 * * * *}") public void run() { service.run(); } }
