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
import com.againspring.marketing.dto.CreateJobRequest.TopCommentDto;
import com.againspring.marketing.dto.CreateJobResponse;
import com.againspring.marketing.dto.JobCallbackPayload;
import com.againspring.notification.TelegramNotifier;
import com.againspring.repository.UserRepository;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.VoteOptionRepository;
import com.againspring.repository.marketing.MarketingJobRepository;
import com.againspring.service.community.CommentService;
import com.againspring.service.community.PromoTitleService;
import com.againspring.service.community.SibomPlanItem;
import com.againspring.service.community.VideoVariantService;
import com.againspring.service.community.VoteService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import java.util.stream.Collectors;
import com.fasterxml.jackson.core.type.TypeReference;

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
    private final VoteService voteService;
    private final CommentService commentService;
    private final VoteOptionRepository voteOptionRepository;
    private final UserRepository userRepository;
    private final VideoVariantService videoVariantService;
    private final TelegramNotifier telegramNotifier;
    private final MarketingLlmAuthGuard llmAuthGuard;

    /**
     * Create a new marketing job for a post.
     *
     * <p>Automatic jobs are sent to ASM with {@code auto_publish=true}. Publication fires
     * as soon as the remote job is READY — there is no evening slot gate.
     * {@code scheduled_publish_at} is still set (V117 NOT NULL) to the creation instant
     * so the column is never empty; it does not delay publish.
     */
    public MarketingJob createJob(String postId, List<String> targets, boolean autoPublish, String requestedBy) {
        return createJob(postId, targets, autoPublish, requestedBy, null, 1);
    }

    private MarketingJob createJob(String postId, List<String> targets, boolean autoPublish, String requestedBy,
                                   Long retryOfJobId, int generationAttempt) {
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

        // Build brief from post — inject real data. Normalize line breaks (real CRLF,
        // literal \n/₩n) before any length-based truncation so cut points aren't thrown
        // off by escape sequences that later collapse to newlines (2026-08-16).
        String normalizedBody = MarketingBriefText.normalize(
            post.getBodyPublished() != null ? post.getBodyPublished() : post.getBodyRaw());
        String normalizedPartnerBody = MarketingBriefText.normalize(
            post.getPartnerBodyPublished() != null ? post.getPartnerBodyPublished() : post.getPartnerBodyRaw());

        String summary = normalizedBody;
        if (summary != null && summary.length() > 500) {
            summary = summary.substring(0, 500);
        }

        // Side A: author's actual text (up to 300 chars)
        String sideAText = normalizedBody;
        if (sideAText != null && sideAText.length() > 300) sideAText = sideAText.substring(0, 300);
        if (sideAText == null) sideAText = "작성자 관점";

        // Side B: partner's text (up to 300 chars), or placeholder
        String sideBText = normalizedPartnerBody;
        if (sideBText != null && sideBText.length() > 300) sideBText = sideBText.substring(0, 300);
        if (sideBText == null || sideBText.isBlank()) sideBText = "상대방 입장은 아직 등록되지 않았어요";

        // Full (untruncated) author body — always enriched for channels that need the
        // whole story (e.g. youtube_shorts narration). side_a/side_b above stay
        // 300-char-capped for X/IG capture and are unaffected.
        String authorBodyFull = normalizedBody;

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

        // Top comments by likeCount (descending) — top 2, full body (no truncation).
        // Always enriched (not gated by target) for consistency across platforms;
        // youtube_shorts narration needs the full text, others simply ignore extra fields.
        List<TopCommentDto> topComments = new ArrayList<>();
        try {
            List<PostComment> comments = commentService.getTopLevelComments(postId);
            topComments = comments.stream()
                .filter(c -> c.getBody() != null && !c.getBody().isBlank())
                .sorted((a, b) -> {
                    int la = a.getLikeCount() != null ? a.getLikeCount() : 0;
                    int lb = b.getLikeCount() != null ? b.getLikeCount() : 0;
                    return Integer.compare(lb, la);
                })
                .limit(2)
                .map(c -> TopCommentDto.builder()
                    .author(MarketingBriefText.normalize(resolveNickname(c.getAuthorId())))
                    .authorId(c.getAuthorId())
                    .body(MarketingBriefText.normalize(c.getBody()))
                    .likeCount(c.getLikeCount() != null ? c.getLikeCount() : 0)
                    .createdAt(c.getCreatedAt())
                    .side(resolveSide(c.getAuthorId(), post.getAuthorId(), post.getPartnerUserId()))
                    .build())
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to load comments for post {}: {}", postId, e.getMessage());
        }

        // Category → Korean tag
        List<String> tags = new ArrayList<>();
        if (post.getCategory() != null) {
            tags.add(post.getCategory().getDisplayName());
        }

        Integer captureSplit = null;
        boolean paired = post.getPartnerAnsweredAt() != null
                && post.getPartnerBodyPublished() != null
                && !post.getPartnerBodyPublished().isBlank();

        // Full (untruncated) partner body, only when actually paired.
        String partnerBodyFull = paired ? normalizedPartnerBody : null;

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
        storyTitle = MarketingBriefText.normalize(storyTitle);

        // Persist first so utm_campaign / post_url can use local job id (story_{id}).
        String idempotencyKey = UUID.randomUUID().toString();
        // V117 NOT NULL. Value is "created now", not an evening gate — auto-publish is READY-driven.
        Instant scheduledPublishAt = Instant.now();
        MarketingJob.MarketingJobBuilder pendingBuilder = MarketingJob.builder()
            .postId(post.getId())
            .status("REQUESTED")
            .autoPublish(autoPublish)
            .requestedBy(requestedBy)
            .retryOfJobId(retryOfJobId)
            .generationAttempt(generationAttempt)
            .targets(serializeJson(targets))
            .scheduledPublishAt(scheduledPublishAt)
            .originalScheduledAt(scheduledPublishAt)
            .idempotencyKey(idempotencyKey);
        MarketingJob savedJob = marketingJobRepository.save(pendingBuilder.build());

        String campaign = MarketingUtmUrls.campaignForJob(savedJob.getId());
        Map<String, String> postUrls = MarketingUtmUrls.buildPostUrls(postId, targets, campaign);
        String postUrl = MarketingUtmUrls.primaryPostUrl(postId, postUrls);

        String masterHook = MarketingBriefText.normalize(PromoTitleService.resolveOrFallback(post));
        String hookEmotion = post.getHookEmotion() != null && !post.getHookEmotion().isBlank()
            ? post.getHookEmotion().trim() : null;

        // Stage-2 variants (H3): only when committing to video platforms.
        // Separate LLM calls per channel (script + sibom_plan). Guard = code only, no 3rd LLM.
        boolean needReels = containsTarget(targets, "instagram_reels");
        boolean needShorts = containsTarget(targets, "youtube_shorts");
        VideoVariantService.Variants variants = VideoVariantService.Variants.empty();
        if (needReels || needShorts) {
            // Check authentication circuit before calling LLM (Decision #6)
            if (llmAuthGuard.isCircuitOpen()) {
                failJob(savedJob, MarketingFailureStage.VARIANT_LLM, "LLM_AUTH_CIRCUIT_OPEN", false,
                    "Claude session has expired; manual re-authentication required. Circuit is open.");
                return savedJob;
            }

            List<String> candidatesForLlm = post.getSibomCandidates() != null
                ? post.getSibomCandidates() : List.of();
            variants = videoVariantService.generate(
                masterHook,
                hookEmotion,
                storyTitle,
                authorBodyFull,
                needReels,
                needShorts,
                candidatesForLlm
            );
        }

        VideoVariantService.QualityGateResult qualityGate =
            VideoVariantService.validateRequiredSibomPlans(variants, needReels, needShorts);
        if (!qualityGate.isValid()) {
            savedJob.setGenerationDiagnostics(serializeJson(qualityGate.diagnostics()));
            failJob(savedJob, MarketingFailureStage.QUALITY_GATE, qualityGate.failureCode(),
                isRegenerableFailure(qualityGate.failureCode()),
                "Video variant quality gate failed: " + qualityGate.failureCode());
            log.warn("Marketing job {} blocked before ASM: {}", savedJob.getId(), qualityGate.failureCode());
            return savedJob;
        }
        if (needReels || needShorts) {
            savedJob.setGenerationDiagnostics(serializeJson(qualityGate.diagnostics()));
        }
        Integer maxDurationSec = null;
        if (needReels && !needShorts) {
            maxDurationSec = variants.maxDurationReelsSec() != null
                ? variants.maxDurationReelsSec() : VideoVariantService.MAX_DURATION_REELS_SEC;
        } else if (needShorts && !needReels) {
            maxDurationSec = variants.maxDurationShortsSec() != null
                ? variants.maxDurationShortsSec() : VideoVariantService.MAX_DURATION_SHORTS_SEC;
        }

        // Unrequested channel → null; requested empty plan → empty list (not metaphor fallback).
        List<CreateJobRequest.SibomPlanItem> planReels =
            needReels ? toBriefSibomPlan(variants.sibomPlanReels()) : null;
        List<CreateJobRequest.SibomPlanItem> planShorts =
            needShorts ? toBriefSibomPlan(variants.sibomPlanShorts()) : null;
        List<CreateJobRequest.SibomPlanItem> activeSibomPlan = null;
        if (needReels && !needShorts) {
            activeSibomPlan = planReels;
        } else if (needShorts && !needReels) {
            activeSibomPlan = planShorts;
        }

        List<String> sibomCandidates = post.getSibomCandidates();
        if (sibomCandidates != null && sibomCandidates.isEmpty()) {
            sibomCandidates = null;
        }

        BriefDto brief = BriefDto.builder()
            .title(storyTitle)
            .promoTitle(spokenCopy(masterHook))
            .hookEmotion(hookEmotion)
            .hookReels(spokenCopy(variants.hookReels()))
            .hookShorts(spokenCopy(variants.hookShorts()))
            .scriptReels(spokenCopy(variants.scriptReels()))
            .scriptShorts(spokenCopy(variants.scriptShorts()))
            .maxDurationReelsSec(variants.maxDurationReelsSec())
            .maxDurationShortsSec(variants.maxDurationShortsSec())
            .maxDurationSec(maxDurationSec)
            .sibomCandidates(sibomCandidates)
            .sibomPlan(activeSibomPlan)
            .sibomPlanReels(needReels ? planReels : null)
            .sibomPlanShorts(needShorts ? planShorts : null)
            // metaphor_* intentionally omitted (null) — video intro uses sibom_plan
            .category(post.getCategory() != null ? post.getCategory().name() : null)
            .viewCount(post.getViewCount())
            .neutralSummary(summary)
            .sideA(sideAText)
            .sideB(sideBText)
            .authorBody(authorBodyFull)
            .partnerBody(partnerBodyFull)
            .empathyRatio(EmpathyRatioDto.builder().a(empathyA).b(empathyB).build())
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

        long waggleSlaMs = asmProperties.getProcessingSlaMs() > 0
            ? asmProperties.getProcessingSlaMs()
            : 900_000L;
        OptionsDto options = OptionsDto.builder()
            .voiceId("default")
            .tone("warm")
            .autoPublish(autoPublish)
            .utmCampaign(campaign)
            .postUrls(postUrls.isEmpty() ? null : postUrls)
            // The AS variants are already final channel scripts.  Preserve the
            // marketing SLA through ASM instead of asking WaggleBot to re-run
            // its general-purpose Claude chunking path.
            .priority("MARKETING_CRITICAL")
            .deadlineAt(Instant.now().plusMillis(waggleSlaMs).toString())
            .preScripted(needReels || needShorts)
            .renderProfile((needReels || needShorts) ? "marketing_fast" : null)
            .build();

        CreateJobRequest request = CreateJobRequest.builder()
            .sourceId(post.getId())
            .brief(brief)
            .targets(targets)
            .options(options)
            .build();

        String callbackUrl = asmProperties.getCallbackBaseUrl() + "/api/internal/marketing/callback";
        request.setCallbackUrl(callbackUrl);

        try {
            CreateJobResponse response = asmClient.createJob(request, idempotencyKey);
            savedJob.setRemoteJobId(response.getJobId());
            savedJob.setStatus(response.getStatus());
            return marketingJobRepository.save(savedJob);
        } catch (RuntimeException e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            // Determine if retryable: network/timeout errors are retryable, validation errors are not
            boolean retryable = isRetryableAsmError(e);
            failJob(savedJob, MarketingFailureStage.ASM_CREATE,
                deriveAsmErrorCode(e), retryable, errorMsg);
            throw e;
        }
    }

    /** Regenerate a quality-failed video as a separately auditable, auto-publishing child job. */
    public MarketingJob regenerateJob(Long jobId, String requestedBy) {
        MarketingJob previous = marketingJobRepository.findById(jobId)
            .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        if (!"FAILED".equals(previous.getStatus()) || !isRegenerableFailure(previous.getFailureCode())) {
            throw new IllegalStateException("Job is not a regenerable video-quality failure");
        }
        List<String> targets;
        try {
            targets = objectMapper.readValue(previous.getTargets(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Job targets cannot be read for regeneration", e);
        }
        int nextAttempt = previous.getGenerationAttempt() == null ? 2 : previous.getGenerationAttempt() + 1;
        return createJob(previous.getPostId(), targets, true,
            requestedBy == null ? "admin:regenerate:" + jobId : requestedBy,
            previous.getId(), nextAttempt);
    }

    private static boolean isRegenerableFailure(String failureCode) {
        return failureCode != null && (failureCode.startsWith("SIBOM_")
            || failureCode.startsWith("VARIANT_")
            || failureCode.startsWith("DURATION_")
            || failureCode.startsWith("LAYOUT_"));
    }

    /**
     * Apply callback from ASM
     */
    @Transactional
    public void applyCallback(JobCallbackPayload payload) {
        marketingJobRepository.findByRemoteJobId(payload.getJobId()).ifPresent(job -> {
            String previousStatus = job.getStatus();
            applyRemoteState(job, payload.getStatus(), payload.getPhase(), payload.getProgress(),
                serializeJson(payload.getArtifacts()), serializeJson(payload.getPublications()),
                payload.getError(), payload.getPublications());
        applyRemoteGenerationDiagnostics(job, payload.getDiagnostics(), payload.getActualDurationMs(),
            payload.getFailureCode(), payload.getFailureStage(), payload.getRetryable(), payload.getErrorSummary());
        enforceShortformOutroGate(job, job.getStatus(), payload.getDiagnostics());
        marketingJobRepository.save(job);
            notifyTerminalStatusChange(job, previousStatus, payload.getPublications());
            log.info("Callback applied for remote job {}: status={}", payload.getJobId(), payload.getStatus());
        });
    }

    /**
     * Apply remote job state to local job entity
     */
    public void applyPoll(MarketingJob job, AsmJobView view) {
        String previousStatus = job.getStatus();
        applyRemoteState(job, view.getStatus(), view.getPhase(), view.getProgress(),
            serializeJson(view.getArtifacts()), serializeJson(view.getPublications()),
            view.getError(), view.getPublications());
        applyRemoteGenerationDiagnostics(job, view.getDiagnostics(), view.getActualDurationMs(),
            view.getFailureCode(), view.getFailureStage(), view.getRetryable(), view.getErrorSummary());
        enforceShortformOutroGate(job, job.getStatus(), view.getDiagnostics());
        marketingJobRepository.save(job);
        notifyTerminalStatusChange(job, previousStatus, view.getPublications());
    }

    /**
     * Reconcile ASM state without turning a known WaggleBot processing timeout into a
     * publish failure. ASM can finish the same remote id after its caller timed out, so
     * WAITING_EXTERNAL stays pollable and preserves the exact remote state for operators.
     */
    private void applyRemoteState(MarketingJob job, String remoteStatus, String remotePhase,
                                  Double remoteProgress, String artifacts, String publications,
                                  String remoteError, List<Map<String, Object>> publicationRows) {
        String normalizedRemoteStatus = normalizeStatus(remoteStatus);
        String localStatus = resolveLocalStatus(job, normalizedRemoteStatus, remoteError);
        job.applyRemote(localStatus, normalizedRemoteStatus, remotePhase, remoteProgress, artifacts, publications);

        if ("WAITING_EXTERNAL".equals(localStatus)) {
            if (job.getWaitingExternalSince() == null) job.setWaitingExternalSince(Instant.now());
            if (job.getSlaBreachedAt() == null) job.setSlaBreachedAt(Instant.now());
            if (remoteError != null && !remoteError.isBlank()) {
                job.setProcessingDetail(compact(remoteError, 1000));
            }
            job.setErrorMessage(null);
            return;
        }
        if ("SLA_BREACHED".equals(localStatus)) {
            if (job.getSlaBreachedAt() == null) job.setSlaBreachedAt(Instant.now());
            job.setErrorMessage(null);
            return;
        }

        // A later READY/PUBLISHED response is authoritative and must erase the old timeout.
        job.setWaitingExternalSince(null);
        job.setSlaBreachedAt(null);
        job.setProcessingDetail(null);
        applyRemoteError(job, remoteError, publicationRows);
    }

    /** ASM/WaggleBot diagnostics are additive and intentionally limited to safe operational facts. */
    private void applyRemoteGenerationDiagnostics(MarketingJob job, Map<String, Object> diagnostics,
                                                   Long actualDurationMs, String failureCode, String failureStage,
                                                   Boolean retryable, String errorSummary) {
        if (diagnostics != null) job.setGenerationDiagnostics(serializeJson(diagnostics));
        if (actualDurationMs != null && actualDurationMs >= 0) job.setActualDurationMs(actualDurationMs);
        String resolvedCode = MarketingRemoteFailureCodes.resolve(failureCode, diagnostics);
        if (resolvedCode != null && !resolvedCode.isBlank()) job.setFailureCode(compact(resolvedCode, 64));
        if (failureStage != null && !failureStage.isBlank()) job.setFailureStage(compact(failureStage, 64));
        if (retryable != null) job.setRetryable(retryable);
        if (errorSummary != null && !errorSummary.isBlank()) {
            String summary = errorSummary;
            if (MarketingRemoteFailureCodes.isQualityFailure(resolvedCode)
                && MarketingRemoteFailureCodes.looksLikeRawDump(summary)) {
                summary = "terminal video quality gate failed: " + resolvedCode;
            }
            job.setErrorSummary(compact(summary, 1000));
        }
    }

    /**
     * Short-form renders must include the Tone L outro CTA (WaggleBot reports {@code outro_duration_ms}).
     */
    private void enforceShortformOutroGate(MarketingJob job, String localStatus,
                                           Map<String, Object> diagnostics) {
        if (localStatus == null || (!"READY".equals(localStatus) && !"PUBLISHED".equals(localStatus))) {
            return;
        }
        List<String> targets;
        try {
            targets = objectMapper.readValue(job.getTargets(), new TypeReference<>() {});
        } catch (Exception e) {
            return;
        }
        if (!containsTarget(targets, "instagram_reels") && !containsTarget(targets, "youtube_shorts")) {
            return;
        }
        if (extractOutroDurationMs(diagnostics) > 0) {
            return;
        }
        failJob(job, MarketingFailureStage.QUALITY_GATE, "LAYOUT_OUTRO_MISSING", true,
            "Rendered short-form video is missing the mandatory outro CTA frame");
    }

    private static long extractOutroDurationMs(Map<String, Object> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return 0L;
        }
        long topLevel = parseOutroDurationValue(diagnostics.get("outro_duration_ms"));
        if (topLevel == 0L) {
            topLevel = parseOutroDurationValue(diagnostics.get("outroDurationMs"));
        }
        if (topLevel > 0L) {
            return topLevel;
        }
        for (Object value : diagnostics.values()) {
            if (!(value instanceof Map<?, ?> nested)) {
                continue;
            }
            long nestedMs = parseOutroDurationValue(nested.get("outro_duration_ms"));
            if (nestedMs == 0L) {
                nestedMs = parseOutroDurationValue(nested.get("outroDurationMs"));
            }
            if (nestedMs > 0L) {
                return nestedMs;
            }
        }
        return 0L;
    }

    private static long parseOutroDurationValue(Object raw) {
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private String resolveLocalStatus(MarketingJob job, String remoteStatus, String remoteError) {
        if (remoteStatus == null) {
            // ASM rollout compatibility: omit/no-new status must not erase a viable local state.
            return job.getStatus() == null || job.getStatus().isBlank() ? "RUNNING" : job.getStatus();
        }
        if ("FAILED".equals(remoteStatus) && isTransientWaggleTimeout(remoteError)) {
            return "WAITING_EXTERNAL";
        }
        return switch (remoteStatus) {
            case "REQUESTED", "QUEUED", "READY", "PUBLISHING", "PUBLISHED", "FAILED", "PARTIAL", "STALE" -> remoteStatus;
            case "RUNNING", "PROCESSING", "IN_PROGRESS" -> generationExceededSla(job) ? "SLA_BREACHED" : "RUNNING";
            default -> job.getStatus() == null || job.getStatus().isBlank() ? "RUNNING" : job.getStatus();
        };
    }

    private boolean generationExceededSla(MarketingJob job) {
        if (job.getCreatedAt() == null) return false;
        return job.getCreatedAt().plusMillis(asmProperties.getProcessingSlaMs()).isBefore(Instant.now());
    }

    private static boolean isTransientWaggleTimeout(String detail) {
        if (detail == null) return false;
        String normalized = detail.toLowerCase(Locale.ROOT);
        return normalized.contains("wagglebot") && normalized.contains("poll timeout");
    }

    private static String normalizeStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) return null;
        return rawStatus.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Send one operational alert when ASM first reaches a terminal publish state.
     * The previous persisted state prevents duplicate callback/poll notifications.
     */
    private void notifyTerminalStatusChange(MarketingJob job, String previousStatus,
                                            List<Map<String, Object>> publications) {
        String currentStatus = job.getStatus();
        if (currentStatus == null || currentStatus.equalsIgnoreCase(previousStatus)) return;
        boolean published = "PUBLISHED".equalsIgnoreCase(currentStatus);
        boolean failed = "FAILED".equalsIgnoreCase(currentStatus) || "PARTIAL".equalsIgnoreCase(currentStatus);
        if (!published && !failed) return;

        String title = postRepository.findById(job.getPostId())
            .map(Post::getTitle)
            .filter(value -> value != null && !value.isBlank())
            .orElse("제목 없음");
        if (published) {
            telegramNotifier.send(formatPublishedAlert(job, title, publications));
        } else {
            telegramNotifier.send(formatFailureAlert(job, title, publications));
        }
    }

    private static String formatPublishedAlert(MarketingJob job, String title,
                                                List<Map<String, Object>> publications) {
        String publicationLines = publicationLines(publications, true);
        return String.format(
            "✅ [Again-Spring] 예약 마케팅 게시 완료%n제목: %s%n잡 #%s%n%s",
            compact(title, 500), job.getId() != null ? job.getId() : "?", publicationLines);
    }

    private static String formatFailureAlert(MarketingJob job, String title,
                                              List<Map<String, Object>> publications) {
        String detail = compact(job.getErrorMessage(), 1200);
        String publicationLines = publicationLines(publications, false);
        return String.format(
            "❌ [Again-Spring] 예약 마케팅 게시 %s%n제목: %s%n잡 #%s%n원인: %s%n에러 로그: %s",
            "PARTIAL".equalsIgnoreCase(job.getStatus()) ? "일부 실패" : "실패",
            compact(title, 500),
            job.getId() != null ? job.getId() : "?",
            publicationLines,
            detail == null || detail.isBlank() ? "원격 서비스가 상세 오류를 반환하지 않았습니다." : detail);
    }

    private static String publicationLines(List<Map<String, Object>> publications, boolean publishedOnly) {
        if (publications == null || publications.isEmpty()) {
            return publishedOnly ? "게시 URL: 원격 서비스 응답에 없음" : "실패 플랫폼: 원격 서비스 응답에 없음";
        }
        List<String> lines = publications.stream()
            .filter(publication -> publication != null)
            .filter(publication -> {
                String state = String.valueOf(publication.getOrDefault("state", ""));
                return publishedOnly
                    ? "PUBLISHED".equalsIgnoreCase(state) || "SUCCESS".equalsIgnoreCase(state)
                    : "FAILED".equalsIgnoreCase(state) || "NEEDS_AUTH".equalsIgnoreCase(state);
            })
            .map(publication -> {
                String platform = String.valueOf(publication.getOrDefault("platform", "unknown"));
                Object url = publication.get("url");
                if (url == null) url = publication.get("published_url");
                if (url == null) url = publication.get("post_url");
                Object error = publication.get("error");
                if (publishedOnly) {
                    return platform + ": " + (url == null ? "URL 없음" : url)
                        + partnerCommentLine(platform, publication);
                }
                return platform + ": "
                    + (error == null ? "상세 오류 없음" : compact(String.valueOf(error), 500));
            })
            .toList();
        if (lines.isEmpty()) {
            return publishedOnly ? "게시 URL: 원격 서비스 응답에 없음" : "실패 플랫폼: 원격 서비스 응답에 없음";
        }
        return String.join(System.lineSeparator(), lines);
    }

    /**
     * A paired YouTube Short may publish successfully even when its follow-up
     * partner-story comment fails.  Surface that distinct outcome in the
     * operator alert without changing the successful video publication state.
     */
    private static String partnerCommentLine(String platform, Map<String, Object> publication) {
        if (!"youtube_shorts".equalsIgnoreCase(platform)) return "";
        Object raw = publication.get("partner_comment");
        if (!(raw instanceof Map<?, ?> comment)) return "";

        Object stateRaw = comment.get("state");
        String state = stateRaw == null ? "" : String.valueOf(stateRaw);
        Object urlRaw = comment.get("url");
        String url = urlRaw == null ? null : String.valueOf(urlRaw);
        if ("PUBLISHED".equalsIgnoreCase(state) || "SUCCESS".equalsIgnoreCase(state)) {
            return System.lineSeparator() + "상대방 사연 댓글: "
                + (url == null || url.isBlank() ? "작성됨 (URL 없음)" : url)
                + System.lineSeparator() + "YouTube Studio에서 댓글 고정 필요";
        }
        Object errorRaw = comment.get("error");
        String error = errorRaw == null ? "상세 오류 없음" : compact(String.valueOf(errorRaw), 500);
        return System.lineSeparator() + "상대방 사연 댓글 미게시: " + error
            + System.lineSeparator() + "YouTube Studio에서 수동 작성·고정 필요";
    }

    /** TTS/"슬래시" 오염 방지 — brief 훅·대본의 마지막 가드. */
    private static String spokenCopy(String value) {
        if (value == null) return null;
        String cleaned = PromoTitleService.stripSlashSeparators(value);
        return cleaned.isBlank() ? null : cleaned;
    }

    private static String compact(String value, int maxLength) {
        if (value == null) return null;
        String compacted = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return compacted.length() <= maxLength ? compacted : compacted.substring(0, maxLength) + "…";
    }

    private void applyRemoteError(MarketingJob job, String remoteError,
                                  List<Map<String, Object>> publications) {
        String detail = remoteError != null && !remoteError.isBlank()
            ? remoteError
            : publicationFailureDetail(publications);
        if (detail != null && !detail.isBlank()) {
            job.setErrorMessage(detail.length() > 1000
                ? detail.substring(0, 1000)
                : detail);
        } else if ("FAILED".equals(job.getStatus()) || "PARTIAL".equals(job.getStatus())) {
            // Keep prior errorMessage if remote omitted detail.
        } else {
            job.setErrorMessage(null);
        }
    }

    private static String publicationFailureDetail(List<Map<String, Object>> publications) {
        if (publications == null) return null;
        for (Map<String, Object> publication : publications) {
            if (publication == null) continue;
            String state = String.valueOf(publication.getOrDefault("state", ""));
            if (!"FAILED".equalsIgnoreCase(state) && !"NEEDS_AUTH".equalsIgnoreCase(state)) continue;
            Object error = publication.get("error");
            if (error == null || String.valueOf(error).isBlank()) continue;
            String platform = String.valueOf(publication.getOrDefault("platform", "unknown"));
            return platform + ": " + error;
        }
        return null;
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
        if (view.getPublications() == null || view.getPublications().isEmpty()) {
            view = asmClient.getJob(job.getRemoteJobId());
        }
        applyPoll(job, view);
        return job;
    }

    private static boolean containsTarget(List<String> targets, String platform) {
        if (targets == null || platform == null) return false;
        for (String t : targets) {
            if (t != null && platform.equalsIgnoreCase(t.trim())) return true;
        }
        return false;
    }

    private static List<CreateJobRequest.SibomPlanItem> toBriefSibomPlan(
            List<SibomPlanItem> items
    ) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<CreateJobRequest.SibomPlanItem> out = new ArrayList<>(items.size());
        for (SibomPlanItem item : items) {
            if (item == null) continue;
            out.add(CreateJobRequest.SibomPlanItem.builder()
                .role(item.role())
                .imageId(item.imageId())
                .caption(item.caption())
                .beatIndex(item.beatIndex())
                .size(item.size())
                .dwell(item.dwell())
                .build());
        }
        return out;
    }

    /** 사용자 ID → 닉네임 변환 (없으면 익명 반환). CommunityCommentController#resolveNickname과 동일 패턴. */
    private String resolveNickname(String userId) {
        if (userId == null || userId.startsWith("anon_")) return "익명";
        return userRepository.findById(userId)
            .map(u -> u.getNickname() != null ? u.getNickname() : "익명")
            .orElse("익명");
    }

    /** 댓글 작성자를 사연 작성자(author)/상대방(partner)/그 외(neutral)로 구분 (Shorts 진영색 스타일용). */
    private String resolveSide(String commentAuthorId, String postAuthorId, String postPartnerUserId) {
        if (commentAuthorId == null) return "neutral";
        if (postAuthorId != null && postAuthorId.equals(commentAuthorId)) return "author";
        if (postPartnerUserId != null && postPartnerUserId.equals(commentAuthorId)) return "partner";
        return "neutral";
    }

    /**
     * Determine if an ASM creation error is retryable.
     * Network/timeout errors = retryable. Validation/serialization errors = not retryable.
     */
    private boolean isRetryableAsmError(RuntimeException e) {
        if (e == null) return false;
        String msg = e.getMessage();
        if (msg == null) return true; // Unknown error, assume retryable
        String lower = msg.toLowerCase();
        // Connection/timeout errors are retryable
        if (lower.contains("timeout") || lower.contains("connection") || lower.contains("temporarily")) {
            return true;
        }
        // Validation/parsing errors are not retryable
        if (lower.contains("invalid") || lower.contains("malformed") || lower.contains("cannot deserialize")) {
            return false;
        }
        // Default: assume retryable for network-adjacent errors
        return true;
    }

    /**
     * Derive a stable failure code from ASM exception.
     */
    private String deriveAsmErrorCode(RuntimeException e) {
        if (e instanceof AsmUnavailableException) {
            return "ASM_UNAVAILABLE";
        }
        String msg = e.getMessage();
        if (msg == null) {
            return "ASM_ERROR_UNKNOWN";
        }
        String lower = msg.toLowerCase();
        if (lower.contains("timeout")) {
            return "ASM_TIMEOUT";
        }
        if (lower.contains("connection")) {
            return "ASM_CONNECTION_FAILED";
        }
        if (lower.contains("invalid") || lower.contains("malformed")) {
            return "ASM_INVALID_REQUEST";
        }
        if (lower.contains("unauthorized") || lower.contains("forbidden")) {
            return "ASM_AUTH_FAILED";
        }
        return "ASM_ERROR_UNKNOWN";
    }

    /**
     * Single entry point for all marketing job failures.
     * Ensures consistent failure tracking with stage, code, retryable flag, and cause.
     *
     * <p>This method is mandatory for all failure paths. Direct {@code setStatus("FAILED")}
     * calls bypass failure contract enforcement and must not be used.
     *
     * @param job the job to fail
     * @param stage the failure stage (from MarketingFailureStage enum)
     * @param failureCode operator-facing, stable code (e.g., "SIBOM_PLAN_TOO_SHORT")
     * @param retryable whether a new generation attempt is meaningful
     * @param cause detailed failure cause (raw exceptions, diagnostics)
     */
    public void failJob(MarketingJob job, MarketingFailureStage stage, String failureCode,
                        boolean retryable, String cause) {
        // Enforce contract: stage and code are mandatory
        if (stage == null || failureCode == null || failureCode.isBlank()) {
            log.error("Failure contract violation — jobId={} stage={} code={} — this is a code defect",
                job.getId(), stage, failureCode);
            // Telegram alert for contract violation (침묵 방지 강제③)
            telegramNotifier.send(String.format(
                "⚠️ 원인 미기록(코드 결함) — AS#%d%n" +
                "stage=%s · code=%s%n" +
                "post=%s · 채널: %s%n" +
                "이 알림 자체가 버그 신고입니다. 코드를 검토하고 commit을 취소하세요.",
                job.getId(), stage, failureCode,
                job.getPostId(), job.getTargets()));
        }

        job.setStatus("FAILED");
        job.setFailureStage(stage != null ? stage.tagged() : null);
        job.setFailureCode(failureCode != null ? compact(failureCode, 64) : null);
        job.setRetryable(retryable);
        job.setErrorSummary(compact(cause, 500));
        job.setErrorMessage(cause);
        marketingJobRepository.save(job);

        // Build and send telegram notification (결정 9의 재설계된 메시지)
        sendFailureNotification(job);

        log.info("Job {} failed: stage={} code={} retryable={}", job.getId(), stage, failureCode, retryable);
    }

    /**
     * Build and send Telegram notification for job failure.
     * Implements 결정 9 spec (07-p1-telegram.md): environment, retry status, 3-way IDs, stage,
     * code, version, attempt history (KST timestamps), context, and diagnostic commands.
     */
    private void sendFailureNotification(MarketingJob job) {
        String message = buildFailureMessage(job);
        if (message != null && !message.isBlank()) {
            Map<String, Object> markup = buildFailureAlertMarkup(job);
            telegramNotifier.sendWithMarkup(message, markup);
        }
    }

    /**
     * Build inline keyboard markup for failure alert (redrive + ignore buttons).
     * Returns null if buttons are not enabled or if idempotency child already exists.
     */
    private Map<String, Object> buildFailureAlertMarkup(MarketingJob job) {
        if (!telegramNotifier.areButtonsEnabled()) {
            return null;
        }

        // Check if already has a non-terminal redrive child (idempotency)
        boolean hasChild = marketingJobRepository.findByRetryOfJobId(job.getId()).stream()
            .anyMatch(child -> !isTerminalStatus(child.getStatus()));
        if (hasChild) {
            return null; // Redrive already in progress, don't add button again
        }

        long nowEpochSec = System.currentTimeMillis() / 1000;
        String env = isProductionEnvironment() ? "prod" : "dev";

        Map<String, Object> reddriveButton = new LinkedHashMap<>();
        reddriveButton.put("text", "재구동");
        reddriveButton.put("callback_data", String.format("redrive:%s:%d:%d", env, job.getId(), nowEpochSec));

        Map<String, Object> ignoreButton = new LinkedHashMap<>();
        ignoreButton.put("text", "무시");
        ignoreButton.put("callback_data", String.format("ignore:%s:%d", env, job.getId()));

        Map<String, Object> markup = new LinkedHashMap<>();
        markup.put("inline_keyboard", List.of(List.of(reddriveButton, ignoreButton)));
        return markup;
    }

    /**
     * Build complete failure notification message per 결정 9 spec.
     * Message structure:
     * - Header (environment, retry state)
     * - Identifiers (AS#, ASM, WaggleBot)
     * - Stage/Code/Retryable
     * - Version (git commit hash)
     * - Attempt history (KST times, durations, errors)
     * - Context (sibom count, voice, channel)
     * - Diagnostic commands
     */
    String buildFailureMessage(MarketingJob job) {
        StringBuilder sb = new StringBuilder();

        // Header: environment, retry state
        String env = isProductionEnvironment() ? "prod" : "dev";
        int retryCount = (job.getGenerationAttempt() != null) ? job.getGenerationAttempt() : 1;
        sb.append(String.format("❌ [다시봄 마케팅/%s] 발행 실패 — %s (%d/2)%n%n",
            env,
            retryCount >= 2 ? "재시도 소진" : "재시도 가능",
            retryCount));

        // Identifiers (3-way: AS#, ASM job_id, WaggleBot)
        String asmJobId = job.getRemoteJobId() != null ? job.getRemoteJobId() : "-";
        String waggleBotId = extractWaggleBotIdFromDiagnostics(job) != null
            ? extractWaggleBotIdFromDiagnostics(job) : "-";
        sb.append(String.format("잡      AS#%d · ASM %s · WaggleBot %s%n",
            job.getId(), compact(asmJobId, 20), waggleBotId));

        // Content: post ID, channels
        sb.append(String.format("콘텐츠  %s · %s%n",
            compact(job.getPostId(), 25),
            compact(parseChannels(job.getTargets()), 50)));

        // Stage / Code / Retryable
        String stage = job.getFailureStage() != null ? job.getFailureStage() : "⚠️ 단계 미기록";
        String code = job.getFailureCode() != null ? job.getFailureCode() : "⚠️ 코드 미기록";
        String retryable = (job.getRetryable() != null && job.getRetryable()) ? "true" : "false";
        sb.append(String.format("단계    %s%n", stage));
        sb.append(String.format("코드    %s · retryable=%s%n", code, retryable));

        // Version (git commit hash from build or placeholder)
        String version = getCurrentGitVersion();
        sb.append(String.format("버전    AS %s%n", version));

        // Attempt history (KST timestamps + durations)
        String attemptHistory = buildAttemptHistory(job);
        if (!attemptHistory.isBlank()) {
            sb.append(String.format("%n시도 이력 (KST)%n%s", attemptHistory));
        }

        // Context (channel-specific info)
        String context = buildContextBlock(job);
        if (!context.isBlank()) {
            sb.append(String.format("%n컨텍스트%n%s", context));
        }

        // Diagnostic commands
        String commands = buildDiagnosticCommands(job);
        if (!commands.isBlank()) {
            sb.append(String.format("%n확인 명령%n%s", commands));
        }

        String message = sb.toString();
        // Truncate to 3800 chars if needed
        if (message.length() > 3800) {
            message = message.substring(0, 3750) + "…\n(메시지 길이 초과로 일부 생략)";
        }

        return message;
    }

    /**
     * Parse attempt history from generation_diagnostics JSON.
     * Formats each attempt as: "Nth HH:MM:SS → HH:MM:SS (Xm Ys) result_code [error_text]"
     */
    private String buildAttemptHistory(MarketingJob job) {
        if (job.getGenerationDiagnostics() == null) {
            return "";
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> diagnostics = objectMapper.readValue(job.getGenerationDiagnostics(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> attempts = (List<Map<String, Object>>) diagnostics.get("attempts");
            if (attempts == null || attempts.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            int maxAttempts = Math.min(5, attempts.size()); // Show max 5 attempts
            java.time.format.DateTimeFormatter kstFormat =
                java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
                    .withZone(java.time.ZoneId.of("Asia/Seoul"));

            for (int i = 0; i < maxAttempts; i++) {
                Map<String, Object> attempt = attempts.get(i);
                String startedAtStr = (String) attempt.get("started_at");
                Object durationObj = attempt.get("duration_ms");
                String result = (String) attempt.get("result");
                String error = (String) attempt.get("error");

                String startTime = "??:??:??";
                String endTime = "??:??:??";
                String duration = "?s";

                if (startedAtStr != null) {
                    try {
                        java.time.Instant startedAt = java.time.Instant.parse(startedAtStr);
                        startTime = kstFormat.format(startedAt);
                        if (durationObj != null) {
                            long durationMs = ((Number) durationObj).longValue();
                            java.time.Instant endedAt = startedAt.plusMillis(durationMs);
                            endTime = kstFormat.format(endedAt);
                            duration = formatDuration(durationMs);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse attempt timestamp: {}", startedAtStr);
                    }
                }

                sb.append(String.format(" %d차 %s → %s (%s) %s", i + 1, startTime, endTime, duration, result));
                if (error != null && !error.isBlank()) {
                    sb.append(String.format(" — %s", compact(error, 120)));
                }
                sb.append("\n");
            }

            if (attempts.size() > maxAttempts) {
                sb.append(String.format(" … 외 %d건\n", attempts.size() - maxAttempts));
            }

            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to parse generation diagnostics: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Format duration in milliseconds as "Xm Ys" format.
     */
    private static String formatDuration(long ms) {
        long secs = ms / 1000;
        long mins = secs / 60;
        long seconds = secs % 60;
        if (mins > 0) {
            return String.format("%dm %ds", mins, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Build context block: channel-specific info (sibom count, TTS voice, etc.)
     */
    private String buildContextBlock(MarketingJob job) {
        StringBuilder sb = new StringBuilder();

        // Channel info
        String channels = parseChannels(job.getTargets());
        if (!channels.isBlank()) {
            sb.append(" 채널: ").append(channels).append("\n");
        }

        // Parse generation diagnostics for context
        if (job.getGenerationDiagnostics() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> diag = objectMapper.readValue(job.getGenerationDiagnostics(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

                // Sibom plan count
                Object sibomCount = diag.get("guarded_plan_count");
                if (sibomCount != null) {
                    Object sibomRequired = diag.get("shorts_required");
                    if (sibomRequired != null) {
                        sb.append(String.format(" 시봄이: %s장 (요구 %s)%n", sibomCount, sibomRequired));
                    } else {
                        sb.append(String.format(" 시봄이: %s장%n", sibomCount));
                    }
                }

                // TTS voice
                Object voiceId = diag.get("voice_id");
                if (voiceId != null && !String.valueOf(voiceId).equalsIgnoreCase("default")) {
                    sb.append(" TTS: ").append(voiceId).append("\n");
                }

                // Script duration
                Object scriptDuration = diag.get("script_duration_sec");
                if (scriptDuration != null) {
                    sb.append(String.format(" 대본: %s초%n", scriptDuration));
                }
            } catch (Exception e) {
                log.debug("Could not extract additional context from diagnostics", e);
            }
        }

        return sb.toString();
    }

    /**
     * Build stage-specific diagnostic commands.
     */
    private String buildDiagnosticCommands(MarketingJob job) {
        String stage = job.getFailureStage();
        if (stage == null) {
            return "";
        }

        String jobId = String.valueOf(job.getId());

        if (stage.contains("QUALITY_GATE") || stage.contains("VARIANT_LLM")) {
            // Query generation_diagnostics from marketing_job
            return String.format(
                " mysql -uroot -p$PW againspring%n" +
                "  → SELECT id, failure_stage, failure_code, generation_diagnostics FROM marketing_job WHERE id=%s\\G",
                jobId);
        } else if (stage.contains("ASM_CREATE") || stage.contains("ASM_POLL")) {
            // Query ASM job table
            return String.format(
                " docker exec again-spring-marketing-asm-db-1 mariadb -uroot -p$PW asm%n" +
                "  → SELECT * FROM job WHERE remote_job_id='%s'\\G",
                compact(job.getRemoteJobId(), 40));
        } else if (stage.contains("PUBLISH_TRIGGER")) {
            // Check social-poster logs
            return " docker logs again-spring-marketing-social-poster-1 --since 30m";
        } else {
            // Default: check ASM logs
            return " docker logs again-spring-marketing-asm-1 --since 30m";
        }
    }

    /**
     * Parse JSON targets array into readable channel names.
     */
    private String parseChannels(String targetsJson) {
        if (targetsJson == null || targetsJson.isBlank()) {
            return "unknown";
        }
        try {
            @SuppressWarnings("unchecked")
            List<String> targets = objectMapper.readValue(targetsJson, List.class);
            return String.join(", ", targets);
        } catch (Exception e) {
            return compact(targetsJson, 40);
        }
    }

    /**
     * Extract WaggleBot remote job ID from diagnostics if available.
     */
    private String extractWaggleBotIdFromDiagnostics(MarketingJob job) {
        if (job.getGenerationDiagnostics() == null) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> diag = objectMapper.readValue(job.getGenerationDiagnostics(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            return (String) diag.get("waggle_remote_job_id");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get current git version (commit hash) for version field.
     * For now, returns placeholder. In production, this would be injected at build time.
     */
    private String getCurrentGitVersion() {
        // TODO: Inject git commit hash at build time via gradle/maven property
        // For now, try to read from a runtime file or return placeholder
        try {
            Process p = Runtime.getRuntime().exec("git rev-parse --short HEAD");
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            if (line != null && !line.isBlank()) {
                return line.trim();
            }
        } catch (Exception e) {
            log.debug("Could not determine git version: {}", e.getMessage());
        }
        return "unknown";
    }

    /**
     * Check if running in production environment.
     */
    private boolean isProductionEnvironment() {
        String profile = System.getProperty("spring.profiles.active", "");
        return profile.contains("prod") || profile.contains("production");
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

    /**
     * Redrive failed marketing jobs: regenerate if regenerable, or recreate with same params.
     * Returns immediately after starting the jobs (no polling).
     * Completion is observed by the existing polling scheduler.
     *
     * @param jobIds list of job IDs to redrive
     * @param skipExisting if true, skip platforms already PUBLISHED
     * @param requestedBy user/admin performing the redrive
     * @return response containing per-job redrive result
     */
    public List<Map<String, Object>> redriveJobs(List<Long> jobIds, boolean skipExisting, String requestedBy) {
        List<Map<String, Object>> results = new ArrayList<>();

        for (Long jobId : jobIds) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sourceId", jobId);
            result.put("action", "ERROR");
            result.put("reason", null);
            result.put("targetId", null);
            result.put("platformStates", new LinkedHashMap<>());

            MarketingJob source;
            try {
                source = marketingJobRepository.findById(jobId)
                    .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
            } catch (Exception e) {
                result.put("reason", "Job not found: " + e.getMessage());
                results.add(result);
                continue;
            }

            // Check for existing non-terminal redrive child (idempotency)
            MarketingJob existingChild = marketingJobRepository.findByRetryOfJobId(jobId).stream()
                .filter(job -> !isTerminalStatus(job.getStatus()))
                .findFirst()
                .orElse(null);

            if (existingChild != null) {
                result.put("action", "SKIPPED");
                result.put("reason", "Non-terminal redrive child already exists");
                result.put("targetId", existingChild.getId());
                extractPlatformStates((Map<String, String>) result.get("platformStates"), existingChild);
                results.add(result);
                continue;
            }

            // Determine targets and filter for skipExisting
            List<String> targets;
            try {
                targets = objectMapper.readValue(source.getTargets(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                result.put("reason", "Failed to parse targets: " + e.getMessage());
                results.add(result);
                continue;
            }

            // Filter out already-published platforms if skipExisting is true
            if (skipExisting) {
                targets = filterPublishedPlatforms(source, targets);
                if (targets.isEmpty()) {
                    result.put("action", "SKIPPED");
                    result.put("reason", "All platforms already PUBLISHED");
                    results.add(result);
                    continue;
                }
            }

            // Try regenerate first (409 fallback to recreate)
            MarketingJob child = null;
            try {
                if ("FAILED".equals(source.getStatus()) && isRegenerableFailure(source.getFailureCode())) {
                    // Use regenerate endpoint
                    child = regenerateJob(jobId, requestedBy);
                    result.put("action", "REGENERATED");
                } else {
                    // Use recreate fallback
                    child = createJob(source.getPostId(), targets, true, requestedBy, source.getId(),
                        (source.getGenerationAttempt() != null ? source.getGenerationAttempt() : 1) + 1);
                    result.put("action", "RECREATED");
                }
                result.put("targetId", child.getId());
                result.put("reason", null);
                extractPlatformStates((Map<String, String>) result.get("platformStates"), child);
            } catch (Exception e) {
                // 409 Conflict on regenerate → fallback to createJob
                if (e.getMessage() != null && e.getMessage().contains("409")) {
                    try {
                        child = createJob(source.getPostId(), targets, true, requestedBy, source.getId(),
                            (source.getGenerationAttempt() != null ? source.getGenerationAttempt() : 1) + 1);
                        result.put("action", "RECREATED");
                        result.put("targetId", child.getId());
                        result.put("reason", null);
                        extractPlatformStates((Map<String, String>) result.get("platformStates"), child);
                    } catch (Exception e2) {
                        result.put("action", "ERROR");
                        result.put("reason", "Regenerate and recreate both failed: " + e2.getMessage());
                    }
                } else {
                    result.put("action", "ERROR");
                    result.put("reason", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                }
            }

            results.add(result);
        }

        return results;
    }

    /**
     * Filter platforms that already have PUBLISHED state in the source job's publications.
     */
    private List<String> filterPublishedPlatforms(MarketingJob source, List<String> targets) {
        if (source.getPublications() == null || source.getPublications().isBlank()) {
            return targets; // No publication data, return all targets
        }

        try {
            List<Map<String, Object>> publications = objectMapper.readValue(
                source.getPublications(), new TypeReference<List<Map<String, Object>>>() {});
            java.util.Set<String> publishedPlatforms = new java.util.HashSet<>();
            for (Map<String, Object> pub : publications) {
                String state = String.valueOf(pub.getOrDefault("state", ""));
                if ("PUBLISHED".equalsIgnoreCase(state) || "SUCCESS".equalsIgnoreCase(state)) {
                    String platform = String.valueOf(pub.getOrDefault("platform", ""));
                    if (!platform.isBlank()) {
                        publishedPlatforms.add(platform);
                    }
                }
            }
            return targets.stream()
                .filter(t -> !publishedPlatforms.contains(t))
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to filter published platforms for job {}: {}", source.getId(), e.getMessage());
            return targets; // On error, return all targets (fail open)
        }
    }

    /**
     * Find a marketing job by ID (for internal redrive validation).
     */
    public java.util.Optional<MarketingJob> findJobById(Long jobId) {
        return marketingJobRepository.findById(jobId);
    }

    /**
     * Extract platform publication states from a job's publications for response.
     */
    private void extractPlatformStates(Map<String, String> platformStates, MarketingJob job) {
        if (job.getPublications() == null || job.getPublications().isBlank()) {
            return;
        }
        try {
            List<Map<String, Object>> publications = objectMapper.readValue(
                job.getPublications(), new TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> pub : publications) {
                String platform = String.valueOf(pub.getOrDefault("platform", "unknown"));
                String state = String.valueOf(pub.getOrDefault("state", "UNKNOWN"));
                platformStates.put(platform, state);
            }
        } catch (Exception e) {
            log.warn("Failed to extract platform states for job {}: {}", job.getId(), e.getMessage());
        }
    }

    /**
     * Check if a job status is terminal.
     */
    private boolean isTerminalStatus(String status) {
        return status != null && (status.equalsIgnoreCase("PUBLISHED") || status.equalsIgnoreCase("FAILED")
            || status.equalsIgnoreCase("PARTIAL"));
    }
}
