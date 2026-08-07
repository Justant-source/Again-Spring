package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.auth.BotTokenCache;
import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.dto.CreatePostDto;
import com.againspring.aiuser.orchestrator.client.dto.PostDto;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiScheduledPartnerAnswer;
import com.againspring.aiuser.orchestrator.domain.AiScheduledPost;
import com.againspring.aiuser.orchestrator.domain.AiThreadPlan;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.enums.ScheduledPartnerAnswerStatus;
import com.againspring.aiuser.orchestrator.repository.AiScheduledPartnerAnswerRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.util.LiteralNewlineNormalizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Creates the real post when a held {@link AiScheduledPost}'s slot arrives, then replays its
 * pre-generated comment/reply candidates into a thread plan (same replay path as
 * {@code AiPostBundleService.generateAndPublish} — no second LLM call). This is what turns
 * "generated at 3am" into "appeared in the feed at whatever hour the curve picked," which the
 * nightly batch alone cannot do since it only writes the {@code ai_scheduled_posts} row.
 *
 * <p>Paired authors ({@link PairedHoldMeta#ORIGIN_PAIRED}): PUBLIC first (no WAIT_FOR_PARTNER),
 * invite token, then enqueue {@link AiScheduledPartnerAnswer} at T0+Δ. Partner may land in
 * quiet hours; author slots never do (hard ban enforced here too).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledPostPublisher {
    private static final String WORKER = "scheduled-post-publisher";
    private static final Random RNG = new Random();

    private final ScheduledPostLeaseService leases;
    private final PersonaRepository personas;
    private final BotTokenCache tokens;
    private final BackendBotClient backend;
    private final JdbcTemplate jdbcTemplate;
    private final OrchestratorProperties properties;
    private final ThreadPlanService planService;
    private final ThreadPlanGenerationService planGenerationService;
    private final ObjectMapper objectMapper;
    private final AiScheduledPartnerAnswerRepository partnerAnswerRepository;
    private final CandidateScheduleSupport candidateScheduleSupport;
    private final SourceReservationSupport sourceReservationSupport;

    public void publishDue() {
        if (!properties.isEnabled() || !properties.getThreadPlan().isEnabled()
                || !properties.getThreadPlan().isScheduledPostPublisherEnabled()) return;
        int batchSize = properties.getThreadPlan().getScheduledPostPublishBatchSize();
        for (AiScheduledPost row : leases.claimDue(WORKER, batchSize, Duration.ofMinutes(5), Instant.now())) {
            publish(row);
        }
    }

    private void publish(AiScheduledPost row) {
        try {
            // Author publish hard ban KST 02:00–06:00 (solo + paired).
            if (QuietHours.isQuietNow()) {
                Instant resume = QuietHours.nextResumeAfter(Instant.now());
                log.info("Scheduled post deferred for quiet hours id={} until {}", row.getId(), resume);
                leases.defer(row.getId(), WORKER, resume, "QUIET_HOURS");
                return;
            }

            Persona author = personas.findById(row.getPersonaId()).orElse(null);
            if (author == null) {
                failAndRelease(row, "PERSONA_NOT_FOUND", false);
                return;
            }
            String email = jdbcTemplate.queryForObject("select email from users where id = ?", String.class, author.getId());
            Optional<String> jwt = tokens.getToken(author.getId(), email, properties.getBotPassword());
            if (jwt.isEmpty()) {
                // Keep soft-reserve across retryable auth failures.
                leases.releaseFailed(row.getId(), WORKER, "AUTH_FAILED", true);
                return;
            }

            boolean paired = PairedHoldMeta.ORIGIN_PAIRED.equalsIgnoreCase(row.getOrigin());
            Map<String, Object> pairedMeta = paired
                    ? PairedHoldMeta.read(objectMapper, row.getCandidatesJson()).orElse(Map.of())
                    : Map.of();
            int jurorCount = paired ? PairedHoldMeta.jurorCount(pairedMeta, 3) : 0;

            CreatePostDto.CreatePostDtoBuilder postBuilder = CreatePostDto.builder()
                    .userTitle(row.getTitle())
                    .bodyRaw(LiteralNewlineNormalizer.normalize(row.getBody()))
                    .category(row.getCategory())
                    .visibility("PUBLIC").jurorCount(jurorCount)
                    .captureSplitAfterLines(readCaptureSplitsFromCandidates(row.getCandidatesJson(), row.getBody()))
                    .promoTitle(readPromoTitleFromCandidates(row.getCandidatesJson(), row.getTitle()))
                    .metaphorId(readMetaphorIdFromCandidates(row.getCandidatesJson()));
            if (!paired) {
                applyProvenanceFromCandidates(postBuilder, row.getCandidatesJson());
            }
            Optional<PostDto> published = backend.createPost(jwt.get(), postBuilder.build());
            if (published.isEmpty() || published.get().getId() == null) {
                // Transient backend write — keep soft-reserve for retry.
                leases.releaseFailed(row.getId(), WORKER, "BACKEND_WRITE_FAILED", true);
                return;
            }

            PostDto post = published.get();
            if (paired) {
                Instant partnerAt = schedulePartnerAfterAuthorPublic(row, post, jwt.get(), pairedMeta);
                attachPhase1FromHold(row, post, partnerAt);
            } else {
                replayCandidates(row, post);
            }
            // Hard-commit popular source once the post is live.
            sourceReservationSupport.commitFromCandidatesJson(row.getCandidatesJson());
            leases.completePosted(row.getId(), WORKER, post.getId());
        } catch (Exception e) {
            log.warn("Scheduled post publish failed id={}: {}", row.getId(), e.getMessage());
            boolean retryable = row.getAttemptCount() < 3;
            if (retryable) {
                leases.releaseFailed(row.getId(), WORKER, "PUBLISH_EXCEPTION", true);
            } else {
                failAndRelease(row, "PUBLISH_EXCEPTION", false);
            }
        }
    }

    /** Terminal publish failure: release soft-reserve then mark FAILED. */
    private void failAndRelease(AiScheduledPost row, String failureCode, boolean retryable) {
        sourceReservationSupport.releaseFromCandidatesJson(row.getCandidatesJson());
        leases.releaseFailed(row.getId(), WORKER, failureCode, retryable);
    }

    /**
     * Author is already PUBLIC. Always schedule a partner (AI paired contract).
     * Does <strong>not</strong> call setPublishMode / WAIT_FOR_PARTNER — PublishMode is owned elsewhere.
     *
     * @return partnerAt (T0+Δ), or null when partner could not be scheduled
     */
    private Instant schedulePartnerAfterAuthorPublic(AiScheduledPost row, PostDto post, String jwt,
                                                   Map<String, Object> pairedMeta) {
        String partnerPersonaId = PairedHoldMeta.text(pairedMeta, "partnerPersonaId");
        if (partnerPersonaId == null || partnerPersonaId.isBlank()) {
            log.error("Paired scheduled post {} published as {} but missing partnerPersonaId — no partner job",
                    row.getId(), post.getId());
            return null;
        }

        Optional<String> inviteTokenOpt = backend.createInviteToken(jwt, post.getId());
        if (inviteTokenOpt.isEmpty()) {
            log.error("Paired post {} invite token failed — partner cannot be scheduled", post.getId());
            return null;
        }

        OrchestratorProperties.PairedPost config = properties.getPairedPost();
        Duration delta = PartnerDelaySampler.sample(
                RNG,
                config.getPartnerDelayMinutesMin(),
                config.getPartnerDelayMinutesMax(),
                config.getPartnerDelayMedianMinutes());
        Instant partnerAt = Instant.now().plus(delta);
        // Partner MAY land in 02–06 — no QuietHours.enforce here.

        String corrId = PairedHoldMeta.text(pairedMeta, "correlationId");
        AiScheduledPartnerAnswer answer = AiScheduledPartnerAnswer.builder()
                .postId(post.getId())
                .inviteToken(inviteTokenOpt.get())
                .authorPersonaId(row.getPersonaId())
                .partnerPersonaId(partnerPersonaId)
                .category(row.getCategory())
                .authorTitle(row.getTitle())
                .authorBody(row.getBody())
                .correlationId(corrId)
                .scheduledPostId(row.getId())
                .scheduledPartnerAt(partnerAt)
                .status(ScheduledPartnerAnswerStatus.SCHEDULED)
                .build();
        partnerAnswerRepository.save(answer);
        log.info("Paired author PUBLIC post={} scheduled partner at {} (Δ={}m) corrId={}",
                post.getId(), partnerAt, delta.toMinutes(), corrId);
        return partnerAt;
    }

    /**
     * Replay Call1 phase1 candidates clamped strictly before partnerAt.
     * Falls back to {@link ThreadPlanGenerationService#ensureAuthorPhase1CommentPlan} when hold has no items.
     */
    private void attachPhase1FromHold(AiScheduledPost row, PostDto post, Instant partnerAt) {
        Instant t0 = Instant.now();
        Instant deadline = partnerAt != null ? partnerAt : t0.plus(Duration.ofMinutes(55));
        if (row.getCandidatesJson() != null && !row.getCandidatesJson().isBlank()) {
            try {
                Map<String, Object> response = objectMapper.readValue(
                        row.getCandidatesJson(), new TypeReference<>() { });
                response.remove(PairedHoldMeta.KEY);
                if (response.get("items") instanceof List<?> items && !items.isEmpty()) {
                    // Phase1 volume is small (≤4 top-level) — use PHASE1 ready mins, not full-plan 6.
                    candidateScheduleSupport.clampScheduledAtsBefore(response, t0, deadline);
                    AiThreadPlan plan = planService.reservePreGeneratedBundle(post.getId(), 1, t0,
                            row.getTitle(), row.getBody(), row.getCategory(), row.getProvider(), row.getModel());
                    planGenerationService.persistAndFinalize(
                            plan.getId(),
                            response,
                            null,
                            ThreadPlanGenerationService.PHASE1_READY_MIN_TOP_LEVEL,
                            ThreadPlanGenerationService.PHASE1_READY_MIN_ITEMS);
                    return;
                }
            } catch (Exception e) {
                log.warn("Paired phase1 replay failed post={} — falling back to ensureAuthorPhase1: {}",
                        post.getId(), e.getMessage());
            }
        }
        boolean ok = planGenerationService.ensureAuthorPhase1CommentPlan(
                post.getId(), 1, row.getTitle(), row.getBody(), row.getCategory(), t0, deadline);
        if (!ok) {
            log.warn("Paired phase1 plan not ready for post={}", post.getId());
        }
    }

    @SuppressWarnings("unchecked")
    private void replayCandidates(AiScheduledPost row, PostDto post) {
        if (row.getCandidatesJson() == null || row.getCandidatesJson().isBlank()) return;
        try {
            Map<String, Object> response = objectMapper.readValue(row.getCandidatesJson(), new TypeReference<>() { });
            response.remove(AiPostBundleService.SOURCE_PROVENANCE_KEY);
            response.remove(PairedHoldMeta.KEY);
            // Paired holds only store meta — no comment candidates to replay.
            if (!(response.get("items") instanceof List<?> items) || items.isEmpty()) {
                if (response.get("post") == null) return;
            }
            Instant publishedAt = Instant.now();
            // Hold slot may have moved without shifting baked candidate times — always rebase
            // comment release to the actual publish clock (dense early window).
            candidateScheduleSupport.rescheduleFromPublishAt(response, publishedAt);
            AiThreadPlan plan = planService.reservePreGeneratedBundle(post.getId(), 1, publishedAt,
                    row.getTitle(), row.getBody(), row.getCategory(), row.getProvider(), row.getModel());
            planGenerationService.persistAndFinalize(plan.getId(), response);
        } catch (Exception replayFailure) {
            log.error("Published scheduled post {} but could not replay its candidates id={}",
                    post.getId(), row.getId(), replayFailure);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyProvenanceFromCandidates(CreatePostDto.CreatePostDtoBuilder postBuilder, String candidatesJson) {
        if (candidatesJson == null || candidatesJson.isBlank()) return;
        try {
            Map<String, Object> response = objectMapper.readValue(candidatesJson, new TypeReference<>() { });
            Object raw = response.get(AiPostBundleService.SOURCE_PROVENANCE_KEY);
            if (!(raw instanceof Map<?, ?> prov)) return;
            Object reconstruct = prov.get("reconstructMode");
            if (!Boolean.TRUE.equals(reconstruct) && !"true".equalsIgnoreCase(String.valueOf(reconstruct))) return;
            Object id = prov.get("sourceExampleId");
            if (id instanceof Number n) postBuilder.sourceExampleId(n.longValue());
            if (prov.get("sourceCommunity") != null) postBuilder.sourceCommunity(String.valueOf(prov.get("sourceCommunity")));
            if (prov.get("sourceUrl") != null) postBuilder.sourceUrl(String.valueOf(prov.get("sourceUrl")));
            if (prov.get("sourceOriginalTitle") != null) postBuilder.sourceOriginalTitle(String.valueOf(prov.get("sourceOriginalTitle")));
            if (prov.get("sourceOriginalBody") != null) postBuilder.sourceOriginalBody(String.valueOf(prov.get("sourceOriginalBody")));
        } catch (Exception e) {
            log.debug("Could not read source provenance from candidates: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Integer> readCaptureSplitsFromCandidates(String candidatesJson, String body) {
        List<Integer> proposed = null;
        if (candidatesJson != null && !candidatesJson.isBlank()) {
            try {
                Map<String, Object> response = objectMapper.readValue(candidatesJson, new TypeReference<>() { });
                Object postRaw = response.get("post");
                if (postRaw instanceof Map<?, ?> post) {
                    Object v = post.get("capture_split_after_lines");
                    if (v == null) v = post.get("captureSplitAfterLines");
                    if (v instanceof List<?> list && !list.isEmpty()) {
                        proposed = new java.util.ArrayList<>();
                        for (Object o : list) {
                            if (o instanceof Number n) proposed.add(n.intValue());
                        }
                        if (proposed.isEmpty()) proposed = null;
                    }
                    if (proposed == null) {
                        Object one = post.get("capture_split_after_line");
                        if (one == null) one = post.get("captureSplitAfterLine");
                        if (one instanceof Number n) proposed = List.of(n.intValue());
                    }
                }
            } catch (Exception e) {
                log.debug("Could not read capture_split_after_lines from candidates: {}", e.getMessage());
            }
        }
        return AiPostBundleService.resolveCaptureSplits(
                LiteralNewlineNormalizer.normalize(body), proposed);
    }

    /** @deprecated */
    @Deprecated
    @SuppressWarnings("unchecked")
    private Integer readCaptureSplitFromCandidates(String candidatesJson, String body) {
        List<Integer> list = readCaptureSplitsFromCandidates(candidatesJson, body);
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    @SuppressWarnings("unchecked")
    private String readPromoTitleFromCandidates(String candidatesJson, String title) {
        if (candidatesJson == null || candidatesJson.isBlank()) return null;
        try {
            Map<String, Object> response = objectMapper.readValue(candidatesJson, new TypeReference<>() { });
            Object postRaw = response.get("post");
            if (postRaw instanceof Map<?, ?> post) {
                Object v = post.get("promo_title");
                if (v == null) v = post.get("promoTitle");
                if (v != null) {
                    String promo = String.valueOf(v).replace("\\n", "\n").trim();
                    if (!promo.isBlank()) return promo;
                }
            }
        } catch (Exception e) {
            log.debug("Could not read promo_title from candidates: {}", e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String readMetaphorIdFromCandidates(String candidatesJson) {
        if (candidatesJson == null || candidatesJson.isBlank()) return null;
        try {
            Map<String, Object> response = objectMapper.readValue(candidatesJson, new TypeReference<>() { });
            Object postRaw = response.get("post");
            if (postRaw instanceof Map<?, ?> post) {
                Object v = post.get("metaphor_id");
                if (v == null) v = post.get("metaphorId");
                if (v != null) {
                    String id = String.valueOf(v).trim().toLowerCase(java.util.Locale.ROOT);
                    if (!id.isBlank()) return id;
                }
            }
        } catch (Exception e) {
            log.debug("Could not read metaphor_id from candidates: {}", e.getMessage());
        }
        return null;
    }

}
