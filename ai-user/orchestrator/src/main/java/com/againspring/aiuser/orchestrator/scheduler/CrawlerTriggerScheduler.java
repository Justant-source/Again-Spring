package com.againspring.aiuser.orchestrator.scheduler;

import com.againspring.aiuser.orchestrator.client.AiLearningClient;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Legacy orchestrator→learning crawl kick (pre-Wave1-D multi-source).
 *
 * <p><b>Retired for daily schedule</b>: {@code ai-learning} APScheduler owns
 * natepan/blind crawl (KST 02:00). Keep this class off via
 * {@code AI_LEARNING_CRAWL_ENABLED=false} on the orchestrator container so it
 * does not race the 03:05 nightly generation batch.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerTriggerScheduler {

    private final AiLearningClient aiLearningClient;
    private final OrchestratorProperties props;

    @Value("${ai-learning.crawl.enabled:false}")
    private boolean crawlEnabled;

    private static final List<String[]> SOURCES = List.of(
        new String[]{"natepan", "1500"},
        new String[]{"blind", "500"}
    );

    /** KST 02:00 — only if explicitly re-enabled; learning scheduler is SSOT. */
    @Scheduled(cron = "0 0 17 * * *")  // UTC 17:00 = KST 02:00
    public void triggerDailyCrawl() {
        if (!props.isEnabled()) {
            log.debug("Crawl trigger skipped: AI_USER_ENABLED=false");
            return;
        }
        if (!crawlEnabled) {
            log.debug("Crawl trigger disabled (ai-learning scheduler owns daily crawl at 02:00 KST)");
            return;
        }
        log.info("Triggering daily crawl for {} sources", SOURCES.size());
        for (String[] src : SOURCES) {
            try {
                aiLearningClient.triggerCrawl(src[0], Integer.parseInt(src[1]));
            } catch (Exception e) {
                log.warn("Crawl trigger failed for {}: {}", src[0], e.getMessage());
            }
        }
    }
}
