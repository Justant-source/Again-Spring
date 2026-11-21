package com.againspring.marketing.experiment;

import com.againspring.domain.enums.PostStatus;
import com.againspring.domain.enums.PostVisibility;
import com.againspring.domain.community.Post;
import com.againspring.domain.marketing.MarketingJob;
import com.againspring.marketing.MarketingJobService;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.marketing.MarketingJobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongConsumer;

/**
 * 포맷 비교용 일괄 테스트 렌더(자동 게시 없음). 최근 공개 사연 N개 × 포맷 arm M개를 만든다.
 *
 * <p>같은 (사연, 플랫폼)에는 동시에 활성 잡을 둘 수 없으므로, 사연별로 arm을 순서대로 렌더하고
 * 사연끼리는 병렬로 돈다. 진행 상황은 테스트 탭의 "서버의 최근 테스트 잡"에서 본다.
 * 한 번에 한 배치만 돌고, 서버를 재시작하면 남은 arm은 이어지지 않는다(1회성 도구).
 * 같은 버튼을 다시 눌러도 최근 12시간 안에 이미 READY인 (사연, arm, 플랫폼)은 건너뛰어 빠진 것만 채운다.
 */
@Slf4j
@Service
public class MarketingPreviewBatchService {

    static final Set<String> TERMINAL = Set.of("READY", "FAILED", "STALE", "PARTIAL");
    static final Set<String> PLATFORMS = Set.of("youtube_shorts", "instagram_reels");
    static final long POLL_MILLIS = 15_000L;
    static final long MAX_WAIT_MILLIS = 45L * 60_000L;
    /** 이 시간 안에 이미 완성된 (사연, arm, 플랫폼)은 다시 렌더하지 않는다. */
    static final long REUSE_HOURS = 12L;

    private final PostRepository postRepository;
    private final MarketingJobService jobService;
    private final MarketingJobRepository jobRepository;
    private final MarketingFormatArmService armService;
    private final Executor executor;
    private final LongConsumer sleeper;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public record Started(List<String> postIds, List<String> arms, String platform) {}

    @Autowired
    public MarketingPreviewBatchService(PostRepository postRepository, MarketingJobService jobService,
                                        MarketingJobRepository jobRepository, MarketingFormatArmService armService) {
        this(postRepository, jobService, jobRepository, armService,
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "marketing-preview-batch");
                t.setDaemon(true);
                return t;
            }),
            ms -> {
                try {
                    Thread.sleep(ms);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
    }

    MarketingPreviewBatchService(PostRepository postRepository, MarketingJobService jobService,
                                 MarketingJobRepository jobRepository, MarketingFormatArmService armService,
                                 Executor executor, LongConsumer sleeper) {
        this.postRepository = postRepository;
        this.jobService = jobService;
        this.jobRepository = jobRepository;
        this.armService = armService;
        this.executor = executor;
        this.sleeper = sleeper;
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * @param armKeys null/빈 목록이면 baseline을 뺀 모든 arm(켜짐 여부 무관)
     * @throws IllegalArgumentException 플랫폼·arm이 올바르지 않거나 사연이 없는 경우
     * @throws IllegalStateException    이미 배치가 돌고 있는 경우
     */
    public Started start(int postCount, List<String> armKeys, String platform) {
        String plat = platform == null || platform.isBlank() ? "youtube_shorts" : platform.trim();
        if (!PLATFORMS.contains(plat)) {
            throw new IllegalArgumentException("platform must be one of " + PLATFORMS);
        }
        int n = Math.max(1, Math.min(postCount, 10));
        List<String> arms = armKeys == null || armKeys.isEmpty()
            ? armService.listAll().stream().map(FormatArm::key).filter(k -> !"baseline".equals(k)).toList()
            : armKeys;
        for (String key : arms) {
            if (armService.find(key).isEmpty()) {
                throw new IllegalArgumentException("Unknown formatArm: " + key);
            }
        }
        List<String> postIds = postRepository
            .findByVisibilityAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                PostVisibility.PUBLIC, PostStatus.VOTING, PageRequest.of(0, n))
            .getContent().stream().map(Post::getId).toList();
        if (postIds.isEmpty() || arms.isEmpty()) {
            throw new IllegalArgumentException("렌더할 사연 또는 arm이 없습니다");
        }
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("이미 일괄 렌더가 진행 중입니다");
        }
        AtomicInteger remaining = new AtomicInteger(postIds.size());
        for (String postId : postIds) {
            executor.execute(() -> {
                try {
                    runPost(postId, arms, plat);
                } finally {
                    if (remaining.decrementAndGet() == 0) {
                        running.set(false);
                        log.info("preview batch finished");
                    }
                }
            });
        }
        log.info("preview batch started: posts={} arms={} platform={}", postIds, arms, plat);
        return new Started(postIds, arms, plat);
    }

    private void runPost(String postId, List<String> arms, String platform) {
        Instant since = Instant.now().minus(REUSE_HOURS, ChronoUnit.HOURS);
        for (String arm : arms) {
            try {
                if (jobRepository.countReadyTestJobs(postId, arm, platform, since) > 0) {
                    log.info("preview batch reuse (already READY) post={} arm={}", postId, arm);
                    continue;
                }
                MarketingJob job = jobService.createTestJobWithArm(postId, List.of(platform), "preview-batch", arm);
                waitUntilTerminal(job);
            } catch (Exception e) {
                log.warn("preview batch skip post={} arm={}: {}", postId, arm, e.getMessage());
            }
        }
    }

    private void waitUntilTerminal(MarketingJob created) {
        if (created == null || created.getId() == null || TERMINAL.contains(created.getStatus())) {
            return;
        }
        long waited = 0;
        while (waited < MAX_WAIT_MILLIS) {
            sleeper.accept(POLL_MILLIS);
            waited += POLL_MILLIS;
            String status = jobRepository.findById(created.getId()).map(MarketingJob::getStatus).orElse("FAILED");
            if (TERMINAL.contains(status)) {
                return;
            }
        }
        log.warn("preview batch wait timeout job={}", created.getId());
    }
}
