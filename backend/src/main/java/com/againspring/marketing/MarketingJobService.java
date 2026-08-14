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
import java.util.Optional;
import java.util.UUID;
import java.util.Locale;
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
    private final VoteService voteService;
    private final CommentService commentService;
    private final VoteOptionRepository voteOptionRepository;
    private final UserRepository userRepository;
    private final MarketingPublishSlotService publishSlotService;
    private final VideoVariantService videoVariantService;
    private final TelegramNotifier telegramNotifier;

    /**
     * Create a new marketing job for a post.
     *
     * <p>When {@code autoPublish} is true and targets have a KST evening slot
     * ({@link MarketingPublishSlotService}), sets {@code scheduledPublishAt} to the next
     * occurrence and sends {@code auto_publish=false} to ASM so generation stops at READY;
     * {@link MarketingPollingScheduler} triggers publish when the slot arrives.
     * Holding COMMIT ≠ social publish (commit selects; slot schedules).
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

        // Full (untruncated) author body — always enriched for channels that need the
        // whole story (e.g. youtube_shorts narration). side_a/side_b above stay
        // 300-char-capped for X/IG capture and are unaffected.
        String authorBodyFull = post.getBodyPublished() != null ? post.getBodyPublished() : post.getBodyRaw();

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

        // Top comments by likeCount (descending) — top 3, full body (no truncation).
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
                .limit(3)
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
        String partnerBodyFull = paired ? post.getPartnerBodyPublished() : null;

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

        // Evening slot: COMMIT/create may be any time of day; social publish waits for KST slot.
        Instant scheduledPublishAt = null;
        boolean asmAutoPublish = autoPublish;
        if (autoPublish) {
            Optional<Instant> slot = publishSlotService.nextSlotForTargets(targets, Instant.now());
            if (slot.isPresent()) {
                scheduledPublishAt = slot.get();
                // Defer ASM auto-publish until AS triggers at scheduledPublishAt.
                asmAutoPublish = false;
            }
        }

        // Persist first so utm_campaign / post_url can use local job id (story_{id}).
        String idempotencyKey = UUID.randomUUID().toString();
        MarketingJob.MarketingJobBuilder pendingBuilder = MarketingJob.builder()
            .postId(post.getId())
            .status("REQUESTED")
            .autoPublish(autoPublish)
            .requestedBy(requestedBy)
            .targets(serializeJson(targets))
            .idempotencyKey(idempotencyKey);
        if (scheduledPublishAt != null) {
            pendingBuilder.scheduledPublishAt(scheduledPublishAt)
                .originalScheduledAt(scheduledPublishAt);
        }
        MarketingJob savedJob = marketingJobRepository.save(pendingBuilder.build());

        String campaign = MarketingUtmUrls.campaignForJob(savedJob.getId());
        Map<String, String> postUrls = MarketingUtmUrls.buildPostUrls(postId, targets, campaign);
        String postUrl = MarketingUtmUrls.primaryPostUrl(postId, postUrls);

        String masterHook = PromoTitleService.resolveOrFallback(post);
        String hookEmotion = post.getHookEmotion() != null && !post.getHookEmotion().isBlank()
            ? post.getHookEmotion().trim() : null;

        // Stage-2 variants (H3): only when committing to video platforms.
        // Separate LLM calls per channel (script + sibom_plan). Guard = code only, no 3rd LLM.
        boolean needReels = containsTarget(targets, "instagram_reels");
        boolean needShorts = containsTarget(targets, "youtube_shorts");
        VideoVariantService.Variants variants = VideoVariantService.Variants.empty();
        if (needReels || needShorts) {
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
            .promoTitle(masterHook)
            .hookEmotion(hookEmotion)
            .hookReels(variants.hookReels())
            .hookShorts(variants.hookShorts())
            .scriptReels(variants.scriptReels())
            .scriptShorts(variants.scriptShorts())
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
            .autoPublish(asmAutoPublish)
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
            savedJob.setStatus("FAILED");
            savedJob.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            marketingJobRepository.save(savedJob);
            telegramNotifier.send(String.format(
                "❌ [Again-Spring] 마케팅 FAILED%n잡 #%s · post=%s%n채널: %s%n원인: %s",
                savedJob.getId() != null ? savedJob.getId() : "?",
                savedJob.getPostId() != null ? savedJob.getPostId() : "?",
                savedJob.getTargets() != null ? savedJob.getTargets() : "[]",
                savedJob.getErrorMessage()));
            throw e;
        }
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
                return publishedOnly
                    ? platform + ": " + (url == null ? "URL 없음" : url)
                    : platform + ": " + (error == null ? "상세 오류 없음" : compact(String.valueOf(error), 500));
            })
            .toList();
        if (lines.isEmpty()) {
            return publishedOnly ? "게시 URL: 원격 서비스 응답에 없음" : "실패 플랫폼: 원격 서비스 응답에 없음";
        }
        return String.join("%n", lines);
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
