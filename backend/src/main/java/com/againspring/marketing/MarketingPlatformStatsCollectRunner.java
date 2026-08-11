package com.againspring.marketing;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Fire-and-forget platform stats collect so Cloudflare/nginx short proxy timeouts
 * do not abort the browser request. FE polls {@link #status(String)}.
 */
@Slf4j
@Service
public class MarketingPlatformStatsCollectRunner {

    public enum Status { RUNNING, COMPLETED, FAILED }

    public record RunView(
            String runId,
            Status status,
            Instant startedAt,
            Instant finishedAt,
            MarketingPlatformStatsCollector.CollectSummary summary,
            String error
    ) {}

    private final MarketingPlatformStatsCollector collector;
    private final MarketingStatsEventService statsEventService;
    private final ObjectMapper objectMapper;
    private final Executor taskExecutor;
    private final Map<String, RunView> runs = new ConcurrentHashMap<>();

    public MarketingPlatformStatsCollectRunner(
            MarketingPlatformStatsCollector collector,
            MarketingStatsEventService statsEventService,
            ObjectMapper objectMapper,
            @Qualifier("taskExecutor") Executor taskExecutor) {
        this.collector = collector;
        this.statsEventService = statsEventService;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
    }

    public RunView start(List<Long> jobIds, int lookbackDays, int limit) {
        String runId = UUID.randomUUID().toString().replace("-", "");
        Instant started = Instant.now();
        runs.put(runId, new RunView(runId, Status.RUNNING, started, null, null, null));
        recordQuiet("COLLECT_STARTED", null, Map.of(
                "runId", runId,
                "lookbackDays", lookbackDays,
                "limit", limit,
                "jobIds", jobIds != null ? jobIds.size() : 0
        ));
        taskExecutor.execute(() -> {
            try {
                MarketingPlatformStatsCollector.CollectSummary summary;
                if (jobIds != null && !jobIds.isEmpty()) {
                    summary = collector.collectForJobs(jobIds);
                } else {
                    summary = collector.collectRecent(lookbackDays, limit);
                }
                runs.put(runId, new RunView(
                        runId, Status.COMPLETED, started, Instant.now(), summary, null));
                recordQuiet("COLLECT_COMPLETED", null, Map.of(
                        "runId", runId,
                        "requested", summary.requested(),
                        "stored", summary.stored(),
                        "partial", summary.partial(),
                        "errors", summary.errors()
                ));
            } catch (Exception e) {
                log.warn("Async platform stats collect failed runId={}: {}", runId, e.getMessage());
                String err = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                runs.put(runId, new RunView(
                        runId, Status.FAILED, started, Instant.now(), null, err));
                recordQuiet("COLLECT_FAILED", null, Map.of("runId", runId, "error", err));
            }
        });
        return runs.get(runId);
    }

    public RunView status(String runId) {
        RunView view = runs.get(runId);
        if (view == null) {
            return new RunView(runId, Status.FAILED, null, Instant.now(), null, "unknown runId");
        }
        return view;
    }

    private void recordQuiet(String type, String platform, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(new LinkedHashMap<>(payload));
            statsEventService.record(type, platform, json);
        } catch (Exception e) {
            log.debug("stats event {} skipped: {}", type, e.getMessage());
        }
    }
}
