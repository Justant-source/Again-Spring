package com.againspring.marketing;

import com.againspring.domain.marketing.MarketingJob;
import com.againspring.domain.marketing.MarketingPublicationStats;
import com.againspring.repository.marketing.MarketingJobRepository;
import com.againspring.repository.marketing.MarketingPublicationStatsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pulls best-effort platform metrics from ASM and persists them on AS
 * ({@code marketing_publication_stats}). ASM remains the collector (credentials);
 * AS is the canonical store for admin reports / auto_adjust.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingPlatformStatsCollector {

    private final AsmClient asmClient;
    private final MarketingJobRepository marketingJobRepository;
    private final MarketingPublicationStatsRepository statsRepository;
    private final ObjectMapper objectMapper;

    public record CollectSummary(int requested, int stored, int partial, int errors) {}

    @Transactional
    public CollectSummary collectRecent(int lookbackDays, int limit) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("lookback_days", Math.max(1, lookbackDays));
        body.put("limit", Math.max(1, Math.min(limit, 100)));
        return persistAsmResponse(asmClient.collectStats(body));
    }

    @Transactional
    public CollectSummary collectForJobs(List<Long> jobIds) {
        List<String> remoteIds = new ArrayList<>();
        for (Long id : jobIds) {
            marketingJobRepository.findById(id).ifPresent(job -> {
                if (job.getRemoteJobId() != null && !job.getRemoteJobId().isBlank()) {
                    remoteIds.add(job.getRemoteJobId());
                }
            });
        }
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode arr = body.putArray("job_ids");
        remoteIds.forEach(arr::add);
        body.put("lookback_days", 14);
        body.put("limit", 100);
        return persistAsmResponse(asmClient.collectStats(body));
    }

    /**
     * Daily/scheduled: collect last 14d published jobs from ASM.
     */
    @Transactional
    public CollectSummary collectScheduled() {
        try {
            return collectRecent(14, 40);
        } catch (Exception e) {
            log.warn("Scheduled platform stats collect failed (best-effort): {}", e.getMessage());
            return new CollectSummary(0, 0, 0, 1);
        }
    }

    private CollectSummary persistAsmResponse(JsonNode response) {
        JsonNode results = response != null ? response.get("results") : null;
        if (results == null || !results.isArray()) {
            return new CollectSummary(0, 0, 0, 0);
        }
        int stored = 0;
        int partial = 0;
        int errors = 0;
        for (JsonNode row : results) {
            String remoteJobId = text(row, "job_id");
            String platform = text(row, "platform");
            if (remoteJobId == null || platform == null) {
                errors++;
                continue;
            }
            Optional<MarketingJob> jobOpt = marketingJobRepository.findByRemoteJobId(remoteJobId);
            if (jobOpt.isEmpty()) {
                log.debug("Skipping stats for unknown remote job {}", remoteJobId);
                errors++;
                continue;
            }
            MarketingJob job = jobOpt.get();
            Instant collectedAt = parseInstant(text(row, "collected_at")).orElse(Instant.now());
            boolean isPartial = row.path("partial").asBoolean(true);
            String err = text(row, "error");
            JsonNode metrics = row.get("metrics");
            String metricsJson;
            try {
                metricsJson = metrics != null ? objectMapper.writeValueAsString(metrics) : "{}";
            } catch (Exception e) {
                metricsJson = "{}";
                isPartial = true;
            }

            MarketingPublicationStats entity = MarketingPublicationStats.builder()
                .jobId(job.getId())
                .postId(job.getPostId())
                .platform(platform)
                .remoteJobId(remoteJobId)
                .remoteId(text(row, "remote_id"))
                .url(text(row, "url"))
                .collectedAt(collectedAt)
                .metricsJson(metricsJson)
                .partial(isPartial)
                .errorMessage(truncate(err, 500))
                .build();
            statsRepository.save(entity);
            stored++;
            if (isPartial) {
                partial++;
            }
            if (err != null && !err.isBlank()) {
                log.info("Platform stats partial job={} platform={}: {}", job.getId(), platform, err);
            }
        }
        return new CollectSummary(results.size(), stored, partial, errors);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s == null || s.isBlank() || "null".equals(s) ? null : s;
    }

    private static Optional<Instant> parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(raw));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Exposed for weekly report window helpers. */
    public Instant sinceDays(int days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }
}
