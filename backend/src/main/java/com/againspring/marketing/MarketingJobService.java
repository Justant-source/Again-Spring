package com.againspring.marketing;

import com.againspring.domain.community.Post;
import com.againspring.domain.marketing.MarketingJob;
import com.againspring.marketing.dto.AsmJobView;
import com.againspring.marketing.dto.CreateJobRequest;
import com.againspring.marketing.dto.CreateJobRequest.BriefDto;
import com.againspring.marketing.dto.CreateJobRequest.EmpathyRatioDto;
import com.againspring.marketing.dto.CreateJobRequest.OptionsDto;
import com.againspring.marketing.dto.CreateJobRequest.PolicyDto;
import com.againspring.marketing.dto.CreateJobResponse;
import com.againspring.marketing.dto.JobCallbackPayload;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.marketing.MarketingJobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing marketing jobs
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MarketingJobService {

    private final AsmClient asmClient;
    private final AsmProperties asmProperties;
    private final PostRepository postRepository;
    private final MarketingJobRepository marketingJobRepository;
    private final ObjectMapper objectMapper;

    /**
     * Create a new marketing job for a post
     */
    public MarketingJob createJob(String postId, List<String> targets, boolean autoPublish, String requestedBy) {
        // Idempotency check: reject if active marketing job already exists
        List<String> terminalStatuses = Arrays.asList("PUBLISHED", "FAILED", "PARTIAL");
        marketingJobRepository.findFirstByPostIdAndStatusNotIn(postId, terminalStatuses)
            .ifPresent(existing -> {
                throw new IllegalStateException(
                    "Active marketing job already exists for post " + postId +
                    " (id=" + existing.getId() + ", status=" + existing.getStatus() + ")"
                );
            });

        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

        // Build brief from post
        String summary = post.getBodyPublished() != null ? post.getBodyPublished() : post.getBodyRaw();
        if (summary != null && summary.length() > 500) {
            summary = summary.substring(0, 500);
        }

        BriefDto brief = BriefDto.builder()
            .title(post.getTitle())
            .neutralSummary(summary)
            .sideA("작성자 관점")
            .sideB("상대방 관점")
            .empathyRatio(EmpathyRatioDto.builder().a(50).b(50).build())
            .juryGist("")
            .tags(List.of())
            .policy(PolicyDto.builder()
                .noEmoji(true)
                .forbiddenTerms(Arrays.asList("판결", "처방", "승패", "승자", "패자"))
                .build())
            .build();

        OptionsDto options = OptionsDto.builder()
            .voiceId("default")
            .tone("warm")
            .autoPublish(autoPublish)
            .build();

        CreateJobRequest request = CreateJobRequest.builder()
            .sourceId(post.getId())
            .brief(brief)
            .targets(targets)
            .options(options)
            .build();

        // Generate idempotency key
        String idempotencyKey = UUID.randomUUID().toString();

        // Add callback URL to request
        String callbackUrl = asmProperties.getCallbackBaseUrl() + "/api/internal/marketing/callback";
        request.setCallbackUrl(callbackUrl);

        // Call ASM
        CreateJobResponse response = asmClient.createJob(request, idempotencyKey);

        // Save marketing job
        MarketingJob job = MarketingJob.builder()
            .remoteJobId(response.getJobId())
            .postId(post.getId())
            .status(response.getStatus())
            .autoPublish(autoPublish)
            .requestedBy(requestedBy)
            .targets(serializeJson(targets))
            .idempotencyKey(idempotencyKey)
            .build();

        return marketingJobRepository.save(job);
    }

    /**
     * Apply callback from ASM
     */
    @Transactional
    public void applyCallback(JobCallbackPayload payload) {
        marketingJobRepository.findByRemoteJobId(payload.getJobId()).ifPresent(job -> {
            job.applyRemote(
                payload.getStatus(),
                payload.getPhase(),
                payload.getProgress() != null ? payload.getProgress() : 0.0,
                serializeJson(payload.getArtifacts()),
                serializeJson(payload.getPublications())
            );
            marketingJobRepository.save(job);
            log.info("Callback applied for remote job {}: status={}", payload.getJobId(), payload.getStatus());
        });
    }

    /**
     * Apply remote job state to local job entity
     */
    public void applyPoll(MarketingJob job, AsmJobView view) {
        job.applyRemote(
            view.getStatus(),
            view.getPhase(),
            view.getProgress(),
            serializeJson(view.getArtifacts()),
            serializeJson(view.getPublications())
        );
        marketingJobRepository.save(job);
    }

    /**
     * Trigger publishing for a ready job
     */
    public MarketingJob triggerPublish(Long jobId) {
        MarketingJob job = marketingJobRepository.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        if (!"READY".equals(job.getStatus())) {
            throw new IllegalStateException("Job must be in READY status to publish, current: " + job.getStatus());
        }

        AsmJobView view = asmClient.publish(job.getRemoteJobId());
        applyPoll(job, view);
        return job;
    }

    private String serializeJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize object to JSON", e);
            return null;
        }
    }
}
