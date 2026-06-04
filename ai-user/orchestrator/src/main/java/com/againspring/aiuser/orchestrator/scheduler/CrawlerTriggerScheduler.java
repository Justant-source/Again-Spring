package com.againspring.aiuser.orchestrator.scheduler;

import com.againspring.aiuser.orchestrator.client.AiLearningClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ai-learning 서비스의 크롤러를 orchestrator 측에서 트리거.
 * 매일 새벽 3시 30분 KST.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerTriggerScheduler {

    private final AiLearningClient aiLearningClient;

    @Value("${ai-learning.crawl.enabled:false}")
    private boolean crawlEnabled;

    private static final List<String[]> SOURCES = List.of(
        new String[]{"naver", "500"},
        new String[]{"daum", "500"},
        new String[]{"dcinside", "100"},
        new String[]{"natepan", "50"},
        new String[]{"bobaedream", "100"},
        new String[]{"blind", "50"}
    );

    @Scheduled(cron = "0 30 18 * * *")  // UTC 18:30 = KST 03:30
    public void triggerDailyCrawl() {
        if (!crawlEnabled) {
            log.debug("Crawl trigger disabled");
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
