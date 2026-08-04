package com.againspring.marketing;

import com.againspring.domain.community.Post;
import com.againspring.domain.community.PostComment;
import com.againspring.domain.community.VoteOption;
import com.againspring.domain.marketing.MarketingJob;
import com.againspring.marketing.dto.AsmJobView;
import com.againspring.marketing.dto.CreateJobRequest;
import com.againspring.marketing.dto.CreateJobRequest.BriefDto;
import com.againspring.marketing.dto.CreateJobRequest.EmpathyRatioDto;
import com.againspring.marketing.dto.CreateJobRequest.OptionsDto;
import com.againspring.marketing.dto.CreateJobRequest.PolicyDto;
import com.againspring.marketing.dto.CreateJobResponse;
import com.againspring.marketing.dto.JobCallbackPayload;
import com.againspring.repository.community.JurorRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.VoteOptionRepository;
import com.againspring.repository.marketing.MarketingJobRepository;
import com.againspring.service.community.CommentService;
import com.againspring.service.community.PromoTitleService;
import com.againspring.service.community.VoteService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final JurorRepository jurorRepository;
    private final VoteService voteService;
    private final CommentService commentService;
    private final VoteOptionRepository voteOptionRepository;

    /**
     * Create a new marketing job for a post
     */
    public MarketingJob createJob(String postId, List<String> targets, boolean autoPublish, String requestedBy) {
        if (!asmProperties.isEnabled()) {
            throw new AsmUnavailableException("ASM is disabled (ASM_ENABLED=false)");
        }
        // Per-platform idempotency: x_thread and instagram_feed each require alone jobs,
        // so concurrent active jobs on *different* platforms for the same post are allowed.
        // Reject only when an active job already covers one of the requested targets.
        for (String target : targets) {
            if (marketingJobRepository.countActivePlatformJobs(postId, target) > 0) {
                throw new IllegalStateException(
                    "이미 처리 중인 마케팅 잡이 있습니다 (postId=" + postId
                        + ", platform=" + target + "). 완료 후 다시 시도해주세요."
                );
            }
        }

        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));

        // Build brief from post — inject real data
        String summary = post.getBodyPublished() != null ? post.getBodyPublished() : post.getBodyRaw();
        if (summary != null && summary.length() > 500) {
            summary = summary.substring(0, 500);
        }

        // Side A: author's actual text (up to 300 chars)
        String sideAText = post.getBodyPublished() != null ? post.getBodyPublished() : post.getBodyRaw();
        if (sideAText != null && sideAText.length() > 300) sideAText = sideAText.substring(0, 300);
        if (sideAText == null) sideAText = "작성자 관점";

        // Side B: partner's text (up to 300 chars), or placeholder
        String sideBText = post.getPartnerBodyPublished() != null ? post.getPartnerBodyPublished() : post.getPartnerBodyRaw();
        if (sideBText != null && sideBText.length() > 300) sideBText = sideBText.substring(0, 300);
        if (sideBText == null || sideBText.isBlank()) sideBText = "상대방 입장은 아직 등록되지 않았어요";

        // Vote results → empathy ratio
        int empathyA = 50, empathyB = 50;
        Map<String, Integer> voteLabels = new LinkedHashMap<>();
        try {
            List<VoteOption> voteOptions = voteOptionRepository.findByPostIdOrderByOrderIdx(postId);
            Map<Long, Long> voteResults = voteService.getVoteResult(postId);
            long totalVotes = voteResults.values().stream().mapToLong(Long::longValue).sum();
            if (!voteOptions.isEmpty() && totalVotes > 0) {
                long optionACount = voteResults.getOrDefault(voteOptions.get(0).getId(), 0L);
                empathyA = (int) Math.round((double) optionACount / totalVotes * 100);
                empathyB = 100 - empathyA;
            }
            for (VoteOption opt : voteOptions) {
                long count = voteResults.getOrDefault(opt.getId(), 0L);
                String label = opt.getLabel() != null ? opt.getLabel() : "선택지";
                voteLabels.put(label, (int) count);
            }
        } catch (Exception e) {
            log.warn("Failed to load vote data for post {}: {}", postId, e.getMessage());
        }

        // Juror opinions
        String juryGist = "";
        List<String> juryOpinions = new ArrayList<>();
        try {
            List<com.againspring.domain.community.Juror> jurors = jurorRepository.findByPostId(postId);
            List<String> comments = jurors.stream()
                .map(j -> j.getEmpathyComment())
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.toList());
            // juryOpinions: top 3, each max 100 chars
            juryOpinions = comments.stream()
                .limit(3)
                .map(c -> c.length() > 100 ? c.substring(0, 100) : c)
                .collect(Collectors.toList());
            // juryGist: combine into 200-char summary
            if (!comments.isEmpty()) {
                String combined = String.join(" / ", comments);
                juryGist = combined.length() > 200 ? combined.substring(0, 200) : combined;
            }
        } catch (Exception e) {
            log.warn("Failed to load juror data for post {}: {}", postId, e.getMessage());
        }

        // Top comments by likeCount (descending), each max 100 chars
        List<String> topComments = new ArrayList<>();
        try {
            List<PostComment> comments = commentService.getTopLevelComments(postId);
            topComments = comments.stream()
                .filter(c -> c.getBody() != null && !c.getBody().isBlank())
                .sorted((a, b) -> {
                    int la = a.getLikeCount() != null ? a.getLikeCount() : 0;
                    int lb = b.getLikeCount() != null ? b.getLikeCount() : 0;
                    return Integer.compare(lb, la);
                })
                .limit(3)
                .map(c -> c.getBody().length() > 100 ? c.getBody().substring(0, 100) : c.getBody())
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to load comments for post {}: {}", postId, e.getMessage());
        }

        // Category → Korean tag
        List<String> tags = new ArrayList<>();
        if (post.getCategory() != null) {
            tags.add(post.getCategory().getDisplayName());
        }

        String postUrl = "https://againspring.net/community/" + postId;

        Integer captureSplit = null;
        boolean paired = post.getPartnerAnsweredAt() != null
                && post.getPartnerBodyPublished() != null
                && !post.getPartnerBodyPublished().isBlank();

        List<Integer> authorProposed = CaptureSplitSupport.coalesceProposed(
                post.getCaptureSplitAfterLines(), post.getCaptureSplitAfterLine());
        List<Integer> partnerProposed = CaptureSplitSupport.coalesceProposed(
                post.getPartnerCaptureSplitAfterLines(), null);

        CaptureSplitSupport.ResolvedCapture authorCap;
        CaptureSplitSupport.ResolvedCapture partnerCap;
        if (paired) {
            CaptureSplitSupport.PairedCapture both = CaptureSplitSupport.resolvePaired(
                    post.getBodyPublished(), authorProposed,
                    post.getPartnerBodyPublished(), partnerProposed);
            authorCap = both.author();
            partnerCap = both.partner();
        } else {
            authorCap = CaptureSplitSupport.resolveSolo(post.getBodyPublished(), authorProposed);
            partnerCap = CaptureSplitSupport.ResolvedCapture.empty();
        }

        List<Double> partHeights = CaptureHeightCalculator.partHeightsCss(
                post.getTitle(), post.getBodyPublished(), authorCap, paired);
        List<Double> partnerHeights = paired
                ? CaptureHeightCalculator.partHeightsCss(
                        post.getTitle(), post.getPartnerBodyPublished(), partnerCap, true)
                : List.of();

        if (!authorCap.splits().isEmpty()) {
            captureSplit = authorCap.splits().get(0);
        }

        String storyTitle = post.getTitle();
        if (storyTitle == null || storyTitle.isBlank()) {
            storyTitle = post.getUserTitle();
        }

        BriefDto brief = BriefDto.builder()
            .title(storyTitle)
            .promoTitle(PromoTitleService.resolveOrFallback(post))
            .neutralSummary(summary)
            .sideA(sideAText)
            .sideB(sideBText)
            .empathyRatio(EmpathyRatioDto.builder().a(empathyA).b(empathyB).build())
            .juryGist(juryGist)
            .juryOpinions(juryOpinions)
            .topComments(topComments)
            .voteLabels(voteLabels)
            .postUrl(postUrl)
            .tags(tags)
            .captureSplitAfterLines(authorCap.splits().isEmpty() ? null : authorCap.splits())
            .partHeightsCss(partHeights.isEmpty() ? null : partHeights)
            .captureBlockCount(authorCap.captureBlockCount() > 0 ? authorCap.captureBlockCount() : null)
            .hasPartnerStory(paired)
            .partnerCaptureSplitAfterLines(partnerCap.splits().isEmpty() ? null : partnerCap.splits())
            .partnerPartHeightsCss(partnerHeights.isEmpty() ? null : partnerHeights)
            .partnerCaptureBlockCount(partnerCap.captureBlockCount() > 0 ? partnerCap.captureBlockCount() : null)
            .captureSplitAfterLine(captureSplit)
            .part1HeightCss(partHeights.isEmpty() ? null : partHeights.get(0))
            .partnerCaptureSplitAfterLine(partnerCap.splits().isEmpty() ? null : partnerCap.splits().get(0))
            .partnerPart1HeightCss(partnerHeights.isEmpty() ? null : partnerHeights.get(0))
            .policy(PolicyDto.builder()
                .noEmoji(true)
                .forbiddenTerms(Arrays.asList("판결", "처방", "승패", "승자", "패자", "가해자", "피해자"))
                .build())
            .build();

        // Generate idempotency key
        String idempotencyKey = UUID.randomUUID().toString();

        // Call ASM to get job ID for utm_campaign
        // Note: We'll set utm_campaign after job is saved and has an ID
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

        MarketingJob savedJob = marketingJobRepository.save(job);

        // Now update options with utm_campaign based on job ID and re-call ASM
        // Actually, ASM already has the job, so we just need to add utm_campaign for tracking
        // This will be used when visit_events are recorded
        // For now, we simply return the saved job
        return savedJob;
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
