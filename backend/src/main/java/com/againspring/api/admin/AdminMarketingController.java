package com.againspring.api.admin;

import com.againspring.annotation.Auditable;
import com.againspring.api.admin.dto.JobResponse;
import com.againspring.api.admin.dto.CreateJobRequest;
import com.againspring.api.admin.dto.MarketingPublishSlotsResponse;
import com.againspring.api.admin.dto.MarketingQuotaResponse;
import com.againspring.api.admin.dto.MarketingScoreWeightsResponse;
import com.againspring.api.admin.dto.UpdateMarketingPublishSlotsRequest;
import com.againspring.api.admin.dto.UpdateMarketingQuotaRequest;
import com.againspring.api.admin.dto.UpdateMarketingScoreWeightsRequest;
import com.againspring.domain.marketing.MarketingJob;
import com.againspring.marketing.AsmClient;
import com.againspring.marketing.MarketingJobService;
import com.againspring.marketing.MarketingPlatformStatsCollectRunner;
import com.againspring.marketing.MarketingPublishSlotService;
import com.againspring.marketing.MarketingQuotaService;
import com.againspring.marketing.MarketingScoreAutoAdjustService;
import com.againspring.marketing.MarketingScoreWeightService;
import com.againspring.marketing.MarketingWeeklyReportService;
import com.againspring.marketing.dto.AsmJobView;
import com.againspring.repository.marketing.MarketingJobRepository;
import com.againspring.service.admin.MarketingStatsService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin API for marketing job management
 * Allows creating, listing, and publishing marketing jobs
 */
@RestController
@RequestMapping("/api/admin/marketing")
@RequiredArgsConstructor
@Tag(name = "Admin — Marketing", description = "마케팅 작업 관리 (ADMIN 전용)")
@SecurityRequirement(name = "bearer-jwt")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMarketingController {

    private final MarketingJobService marketingJobService;
    private final MarketingJobRepository marketingJobRepository;
    private final AsmClient asmClient;
    private final MarketingStatsService marketingStatsService;
    private final MarketingQuotaService marketingQuotaService;
    private final MarketingScoreWeightService marketingScoreWeightService;
    private final MarketingPublishSlotService marketingPublishSlotService;
    private final MarketingPlatformStatsCollectRunner marketingPlatformStatsCollectRunner;
    private final MarketingWeeklyReportService marketingWeeklyReportService;
    private final MarketingScoreAutoAdjustService marketingScoreAutoAdjustService;

    // ===== Daily auto-publish quota (Phase 2 per-platform) =====

    @GetMapping("/quota")
    @Operation(summary = "Marketing daily quota",
        description = "플랫폼별 일일 cap·오늘 KST 사용량 (Phase 2). legacy dailyTextCap/dailyVideoCap은 합산 파생")
    @ApiResponse(responseCode = "200", description = "Quota returned")
    public ResponseEntity<MarketingQuotaResponse> getQuota() {
        return ResponseEntity.ok(MarketingQuotaResponse.from(marketingQuotaService.getStatus()));
    }

    @PutMapping("/quota")
    @Operation(summary = "Update marketing daily quota",
        description = "플랫폼별 cap 저장 (xThread/instagramFeed/instagramReels/youtubeShorts). "
            + "legacy dailyTextCap+dailyVideoCap도 허용(분배 저장)")
    @ApiResponse(responseCode = "200", description = "Quota updated")
    @ApiResponse(responseCode = "400", description = "Invalid caps")
    @Auditable(action = "UPDATE_MARKETING_QUOTA")
    public ResponseEntity<MarketingQuotaResponse> updateQuota(
            @Valid @RequestBody UpdateMarketingQuotaRequest req,
            Authentication auth) {
        String updatedBy = auth != null ? auth.getName() : "admin";
        if (req.hasPlatformCaps()) {
            var current = marketingQuotaService.getPlatformCaps();
            var caps = new MarketingQuotaService.PlatformCaps(
                req.getXThread() != null ? req.getXThread() : current.xThread(),
                req.getInstagramFeed() != null ? req.getInstagramFeed() : current.instagramFeed(),
                req.getInstagramReels() != null ? req.getInstagramReels() : current.instagramReels(),
                req.getYoutubeShorts() != null ? req.getYoutubeShorts() : current.youtubeShorts());
            return ResponseEntity.ok(MarketingQuotaResponse.from(
                marketingQuotaService.updatePlatformCaps(caps, updatedBy)));
        }
        if (!req.hasLegacyCaps()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Provide platform caps or dailyTextCap+dailyVideoCap");
        }
        return ResponseEntity.ok(MarketingQuotaResponse.from(
            marketingQuotaService.updateCaps(req.getDailyTextCap(), req.getDailyVideoCap(), updatedBy)));
    }

    // ===== Popularity score weights (Phase 2 per-platform) =====

    @GetMapping("/score-weights")
    @Operation(summary = "Marketing score weights",
        description = "플랫폼별 인기 점수 가중치 (Phase 2). legacy weightViews/Comments/Votes 포함")
    @ApiResponse(responseCode = "200", description = "Weights returned")
    public ResponseEntity<MarketingScoreWeightsResponse> getScoreWeights() {
        return ResponseEntity.ok(MarketingScoreWeightsResponse.fromPlatform(
            marketingScoreWeightService, marketingScoreWeightService.getPlatformWeights()));
    }

    @PutMapping("/score-weights")
    @Operation(summary = "Update marketing score weights",
        description = "platforms 맵 또는 legacy flat weights 저장 (각 0–100)")
    @ApiResponse(responseCode = "200", description = "Weights updated")
    @ApiResponse(responseCode = "400", description = "Invalid weights")
    @Auditable(action = "UPDATE_MARKETING_SCORE_WEIGHTS")
    public ResponseEntity<MarketingScoreWeightsResponse> updateScoreWeights(
            @Valid @RequestBody UpdateMarketingScoreWeightsRequest req,
            Authentication auth) {
        String updatedBy = auth != null ? auth.getName() : "admin";
        if (req.hasAutoAdjust()) {
            marketingScoreWeightService.updateAutoAdjust(Boolean.TRUE.equals(req.getAutoAdjust()), updatedBy);
        }
        if (req.hasPlatformWeights()) {
            java.util.Map<String, com.againspring.marketing.MarketingPopularityScorer.PlatformWeights> partial =
                new java.util.LinkedHashMap<>();
            for (var e : req.getPlatforms().entrySet()) {
                partial.put(e.getKey(), MarketingScoreWeightService.fromSignalMap(e.getValue()));
            }
            var all = marketingScoreWeightService.updatePlatformWeightsPartial(partial, updatedBy);
            if (req.hasLegacyWeights()) {
                marketingScoreWeightService.updateWeights(
                    req.getWeightViews(), req.getWeightComments(), req.getWeightVotes(), updatedBy);
            }
            return ResponseEntity.ok(MarketingScoreWeightsResponse.fromPlatform(
                marketingScoreWeightService, all));
        }
        if (req.hasLegacyWeights()) {
            marketingScoreWeightService.updateWeights(
                req.getWeightViews(), req.getWeightComments(), req.getWeightVotes(), updatedBy);
            return ResponseEntity.ok(MarketingScoreWeightsResponse.fromPlatform(
                marketingScoreWeightService, marketingScoreWeightService.getPlatformWeights()));
        }
        if (req.hasAutoAdjust()) {
            return ResponseEntity.ok(MarketingScoreWeightsResponse.fromPlatform(
                marketingScoreWeightService, marketingScoreWeightService.getPlatformWeights()));
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Provide platforms map, legacy weights, and/or autoAdjust");
    }

    // ===== KST evening publish slots =====

    @GetMapping("/publish-slots")
    @Operation(summary = "Marketing publish slots", description = "플랫폼별 KST 저녁 발행 슬롯 (HH:mm)")
    @ApiResponse(responseCode = "200", description = "Slots returned")
    public ResponseEntity<MarketingPublishSlotsResponse> getPublishSlots() {
        return ResponseEntity.ok(MarketingPublishSlotsResponse.from(marketingPublishSlotService.getSlots()));
    }

    @PutMapping("/publish-slots")
    @Operation(summary = "Update marketing publish slots", description = "KST 저녁 발행 슬롯 저장 (HH:mm)")
    @ApiResponse(responseCode = "200", description = "Slots updated")
    @ApiResponse(responseCode = "400", description = "Invalid HH:mm")
    @Auditable(action = "UPDATE_MARKETING_PUBLISH_SLOTS")
    public ResponseEntity<MarketingPublishSlotsResponse> updatePublishSlots(
            @Valid @RequestBody UpdateMarketingPublishSlotsRequest req,
            Authentication auth) {
        String updatedBy = auth != null ? auth.getName() : "admin";
        MarketingPublishSlotService.Slots updated = marketingPublishSlotService.updateSlots(
            new MarketingPublishSlotService.Slots(
                req.getInstagramFeed(),
                req.getInstagramReels(),
                req.getYoutubeShorts(),
                req.getXThread()),
            updatedBy);
        return ResponseEntity.ok(MarketingPublishSlotsResponse.from(updated));
    }

    /**
     * Create a new marketing job for a post
     */
    @PostMapping("/jobs")
    @Operation(summary = "Create marketing job", description = "ASM을 통해 마케팅 콘텐츠 생성 작업 시작")
    @ApiResponse(responseCode = "201", description = "Job created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    @Auditable(action = "CREATE_MARKETING_JOB")
    public ResponseEntity<JobResponse> createJob(@Valid @RequestBody CreateJobRequest req) {
        MarketingJob job = marketingJobService.createJob(
            req.getPostId(),
            req.getTargets(),
            req.isAutoPublish(),
            null // requestedBy will be set from security context if needed
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(JobResponse.from(job));
    }

    /**
     * List all marketing jobs
     */
    @GetMapping("/jobs")
    @Operation(summary = "List marketing jobs", description = "모든 마케팅 작업 조회")
    @ApiResponse(responseCode = "200", description = "Jobs listed successfully")
    public ResponseEntity<List<JobResponse>> listJobs() {
        List<MarketingJob> jobs = marketingJobRepository.findAll();
        List<JobResponse> responses = jobs.stream()
            .map(JobResponse::from)
            .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /**
     * Get a specific marketing job by ID
     */
    @GetMapping("/jobs/{id}")
    @Operation(summary = "Get marketing job", description = "특정 마케팅 작업 조회")
    @ApiResponse(responseCode = "200", description = "Job found")
    @ApiResponse(responseCode = "404", description = "Job not found")
    public ResponseEntity<JobResponse> getJob(@PathVariable Long id) {
        MarketingJob job = marketingJobRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        return ResponseEntity.ok(JobResponse.from(job));
    }

    /**
     * Proxy artifact download from ASM — streams file bytes with original content-type
     */
    @GetMapping("/jobs/{id}/artifacts/{name:.+}")
    @Operation(summary = "Download artifact", description = "ASM 아티팩트 파일 프록시 다운로드")
    public ResponseEntity<Resource> getArtifact(@PathVariable Long id, @PathVariable String name) {
        MarketingJob job = marketingJobRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        if (job.getRemoteJobId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job has no remote ID");
        }
        return asmClient.getArtifact(job.getRemoteJobId(), name);
    }

    private static final java.util.Set<String> THUMBNAIL_PLATFORMS =
        java.util.Set.of("youtube_shorts", "instagram_reels");
    private static final long MAX_THUMBNAIL_BYTES = 2L * 1024 * 1024; // matches YouTube thumbnails.set hard cap

    /**
     * Upload/replace a custom thumbnail for a job's YouTube Shorts / Instagram
     * Reels artifact. Stored on ASM as {@code {platform}__customcover.{ext}} —
     * picked up automatically by the publishers (YouTube via thumbnails.set,
     * Reels via the Playwright automation fallback's coverPath) on next publish.
     */
    @PutMapping(value = "/jobs/{id}/artifacts/{platform}/thumbnail",
        consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Set custom thumbnail", description = "쇼츠/릴스 커스텀 썸네일 업로드 (YouTube/Instagram Reels)")
    @Auditable(action = "SET_MARKETING_THUMBNAIL")
    public ResponseEntity<Void> setThumbnail(
            @PathVariable Long id,
            @PathVariable String platform,
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        if (!THUMBNAIL_PLATFORMS.contains(platform)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "platform must be one of " + THUMBNAIL_PLATFORMS);
        }
        String contentType = file.getContentType();
        String ext = switch (contentType == null ? "" : contentType) {
            case "image/png" -> "png";
            case "image/jpeg", "image/jpg" -> "jpg";
            default -> null;
        };
        if (ext == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "file must be image/png or image/jpeg");
        }
        if (file.isEmpty() || file.getSize() > MAX_THUMBNAIL_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "file must be non-empty and at most " + MAX_THUMBNAIL_BYTES + " bytes");
        }

        MarketingJob job = marketingJobRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        if (job.getRemoteJobId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job has no remote ID");
        }

        String name = platform + "__customcover." + ext;
        asmClient.putArtifact(job.getRemoteJobId(), name, file.getBytes(), contentType);
        return ResponseEntity.noContent().build();
    }

    /**
     * Publish a ready marketing job
     */
    @PostMapping("/jobs/{id}/publish")
    @Operation(summary = "Publish marketing job", description = "준비된 마케팅 작업 발행")
    @ApiResponse(responseCode = "200", description = "Job published successfully")
    @ApiResponse(responseCode = "404", description = "Job not found")
    @ApiResponse(responseCode = "400", description = "Job not in READY status")
    @Auditable(action = "PUBLISH_MARKETING_JOB")
    public ResponseEntity<JobResponse> publishJob(@PathVariable Long id) {
        MarketingJob job = marketingJobService.triggerPublish(id);
        return ResponseEntity.ok(JobResponse.from(job));
    }

    /**
     * Retry publishing for a PARTIAL/FAILED job (after credentials have been configured)
     */
    @PostMapping("/jobs/{id}/republish")
    @Operation(summary = "Retry publish marketing job", description = "PARTIAL/FAILED 마케팅 잡 게시 재시도 (자격증명 설정 후)")
    @ApiResponse(responseCode = "200", description = "Job re-queued for publishing")
    @ApiResponse(responseCode = "404", description = "Job not found")
    @ApiResponse(responseCode = "409", description = "Job not in PARTIAL/FAILED status")
    @Auditable(action = "REPUBLISH_MARKETING_JOB")
    public ResponseEntity<JobResponse> republishJob(@PathVariable Long id) {
        MarketingJob job = marketingJobRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));

        if (job.getRemoteJobId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Job has no remote ID");
        }

        AsmJobView view = asmClient.republish(job.getRemoteJobId());
        marketingJobService.applyPoll(job, view);
        return ResponseEntity.ok(JobResponse.from(job));
    }

    // ===== Platform credentials (proxied to ASM; encrypted at rest in ASM) =====

    /**
     * List per-platform credential status. Secrets are masked by ASM —
     * only public field values and a per-secret set/unset flag are returned.
     */
    @GetMapping("/credentials")
    @Operation(summary = "List platform credentials", description = "플랫폼별 계정 설정 상태 조회 (시크릿 마스킹)")
    @ApiResponse(responseCode = "200", description = "Credential statuses listed")
    public ResponseEntity<JsonNode> listCredentials() {
        return ResponseEntity.ok(asmClient.listCredentials());
    }

    /**
     * Create or update the credentials for a platform. Body is {@code {"values": {...}}};
     * blank secret fields preserve the existing stored value (ASM merge semantics).
     */
    @PutMapping("/credentials/{platform}")
    @Operation(summary = "Upsert platform credential", description = "플랫폼 계정정보 저장/수정 (ASM에서 암호화)")
    @ApiResponse(responseCode = "200", description = "Credential saved")
    @ApiResponse(responseCode = "400", description = "Unsupported platform or missing required field")
    @Auditable(action = "UPSERT_MARKETING_CREDENTIAL", targetType = "MARKETING_CREDENTIAL", targetId = "#platform")
    public ResponseEntity<JsonNode> upsertCredential(@PathVariable String platform, @RequestBody JsonNode body) {
        return ResponseEntity.ok(asmClient.upsertCredential(platform, body));
    }

    /**
     * Delete the stored credentials for a platform.
     */
    @DeleteMapping("/credentials/{platform}")
    @Operation(summary = "Delete platform credential", description = "플랫폼 계정정보 삭제")
    @ApiResponse(responseCode = "204", description = "Credential deleted (idempotent)")
    @Auditable(action = "DELETE_MARKETING_CREDENTIAL", targetType = "MARKETING_CREDENTIAL", targetId = "#platform")
    public ResponseEntity<Void> deleteCredential(@PathVariable String platform) {
        asmClient.deleteCredential(platform);
        return ResponseEntity.noContent().build();
    }

    // ===== WaggleBot TTS voices (for platform account editor) =====

    /**
     * List TTS voices registered in WaggleBot (via ASM).
     * {@code sampleUrl} stays as WB media path ({@code /api/media/voices/...});
     * preview goes through {@link #getTtsVoiceSample}.
     */
    @GetMapping("/tts/voices")
    @Operation(summary = "List WaggleBot TTS voices", description = "와글봇 등록 TTS 음성 목록")
    @ApiResponse(responseCode = "200", description = "Voice catalog returned")
    public ResponseEntity<JsonNode> listTtsVoices() {
        return ResponseEntity.ok(asmClient.listWaggleVoices());
    }

    /**
     * Stream a WaggleBot voice sample (wav/mp3) for admin preview.
     * {@code path} must be a WB sample path:
     * {@code /api/tts/voices/{key}/sample} or {@code /api/media/voices/...}.
     */
    @GetMapping("/tts/voice-sample")
    @Operation(summary = "Preview WaggleBot TTS sample", description = "TTS 음성 샘플 스트리밍")
    public ResponseEntity<Resource> getTtsVoiceSample(@RequestParam("path") String path) {
        if (!isAllowedWaggleSamplePath(path)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid sample path");
        }
        return asmClient.getWaggleVoiceSample(path);
    }

    private static boolean isAllowedWaggleSamplePath(String path) {
        if (path == null || path.contains("..")) {
            return false;
        }
        if (path.startsWith("/api/media/voices/") && path.chars().filter(ch -> ch == '/').count() >= 4) {
            return true;
        }
        // /api/tts/voices/{key}/sample
        return path.startsWith("/api/tts/voices/")
                && path.endsWith("/sample")
                && path.chars().filter(ch -> ch == '/').count() == 5;
    }

    // ===== YouTube Shorts OAuth 2.0 authorization-code flow =====

    /**
     * YouTube OAuth — Google 인증 URL 생성.
     * client_id/client_secret이 저장된 상태에서 호출.
     * Body: {"redirect_uri": "https://..."}
     * 반환: {"auth_url": "https://accounts.google.com/o/oauth2/v2/auth?..."}
     */
    @PostMapping("/credentials/youtube_shorts/oauth/start")
    @Operation(summary = "YouTube OAuth start", description = "Google 인증 URL 생성 (OAuth 2.0 authorization-code)")
    @ApiResponse(responseCode = "200", description = "auth_url returned")
    @ApiResponse(responseCode = "400", description = "client_id/client_secret 미설정 또는 redirect_uri 불허")
    public ResponseEntity<JsonNode> youtubeOauthStart(@RequestBody JsonNode body) {
        return ResponseEntity.ok(asmClient.youtubeOauthStart(body));
    }

    /**
     * YouTube OAuth — authorization code 교환 → refresh_token 저장.
     * Body: {"code": "...", "state": "..."}
     * 반환: CredentialStatus (secrets 마스킹)
     */
    @PostMapping("/credentials/youtube_shorts/oauth/exchange")
    @Operation(summary = "YouTube OAuth exchange", description = "authorization code → refresh_token 저장")
    @ApiResponse(responseCode = "200", description = "refresh_token 저장 완료 — CredentialStatus 반환")
    @ApiResponse(responseCode = "400", description = "state 검증 실패 또는 Google OAuth 오류")
    @Auditable(action = "YOUTUBE_OAUTH_EXCHANGE", targetType = "MARKETING_CREDENTIAL", targetId = "youtube_shorts")
    public ResponseEntity<JsonNode> youtubeOauthExchange(@RequestBody JsonNode body) {
        return ResponseEntity.ok(asmClient.youtubeOauthExchange(body));
    }

    // ===== Marketing Statistics =====

    /**
     * Get platform performance metrics over the last N days
     */
    @GetMapping("/performance")
    @Operation(summary = "Platform performance metrics", description = "플랫폼별 마케팅 성공률·게시 현황 조회")
    @ApiResponse(responseCode = "200", description = "Performance metrics listed")
    public ResponseEntity<List<MarketingStatsService.PlatformStatsDto>> getPerformance(
            @RequestParam(defaultValue = "30") int days) {
        List<MarketingStatsService.PlatformStatsDto> performance = marketingStatsService.getPlatformPerformance(days);
        return ResponseEntity.ok(performance);
    }

    /**
     * Get publication timeline of recent jobs
     */
    @GetMapping("/timeline")
    @Operation(summary = "Publication timeline", description = "최근 마케팅 게시 현황 타임라인")
    @ApiResponse(responseCode = "200", description = "Timeline events listed")
    public ResponseEntity<List<MarketingStatsService.TimelineEventDto>> getTimeline(
            @RequestParam(defaultValue = "20") int limit) {
        List<MarketingStatsService.TimelineEventDto> timeline = marketingStatsService.getPublicationTimeline(limit);
        return ResponseEntity.ok(timeline);
    }

    /**
     * Get traffic metrics for a specific job
     */
    @GetMapping("/jobs/{id}/traffic")
    @Operation(summary = "Job traffic metrics", description = "마케팅 잡의 visit_events 기반 트래픽 분석")
    @ApiResponse(responseCode = "200", description = "Traffic metrics")
    public ResponseEntity<MarketingStatsService.JobTrafficDto> getJobTraffic(@PathVariable long id) {
        MarketingStatsService.JobTrafficDto traffic = marketingStatsService.getJobTraffic(id);
        return ResponseEntity.ok(traffic);
    }

    // ===== Phase 2.6–2.7 platform stats + weekly report =====

    @PostMapping("/stats/collect")
    @Operation(summary = "Start platform stats collect (async)",
        description = "백그라운드 수집 시작. Cloudflare/nginx 타임아웃 회피. GET /stats/collect/{runId}로 폴링")
    @ApiResponse(responseCode = "202", description = "Collect started")
    @Auditable(action = "COLLECT_MARKETING_PLATFORM_STATS")
    public ResponseEntity<Map<String, Object>> collectPlatformStats(
            @RequestParam(required = false) List<Long> jobIds,
            @RequestParam(defaultValue = "14") int lookbackDays,
            @RequestParam(defaultValue = "40") int limit) {
        MarketingPlatformStatsCollectRunner.RunView run =
                marketingPlatformStatsCollectRunner.start(jobIds, lookbackDays, limit);
        return ResponseEntity.accepted().body(Map.of(
                "runId", run.runId(),
                "status", run.status().name(),
                "startedAt", run.startedAt() != null ? run.startedAt().toString() : ""
        ));
    }

    @GetMapping("/stats/collect/{runId}")
    @Operation(summary = "Poll platform stats collect status")
    @ApiResponse(responseCode = "200", description = "Collect run status")
    public ResponseEntity<Map<String, Object>> collectPlatformStatsStatus(@PathVariable String runId) {
        MarketingPlatformStatsCollectRunner.RunView run = marketingPlatformStatsCollectRunner.status(runId);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("runId", run.runId());
        body.put("status", run.status().name());
        if (run.startedAt() != null) {
            body.put("startedAt", run.startedAt().toString());
        }
        if (run.finishedAt() != null) {
            body.put("finishedAt", run.finishedAt().toString());
        }
        if (run.error() != null) {
            body.put("error", run.error());
        }
        if (run.summary() != null) {
            body.put("summary", Map.of(
                    "requested", run.summary().requested(),
                    "stored", run.summary().stored(),
                    "partial", run.summary().partial(),
                    "errors", run.summary().errors()
            ));
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/weekly-report")
    @Operation(summary = "Marketing weekly report",
        description = "상위/하위 사연 · 감정·카테고리 · UTM 유입 (Phase 2.7)")
    @ApiResponse(responseCode = "200", description = "Weekly report")
    public ResponseEntity<MarketingWeeklyReportService.WeeklyReportDto> weeklyReport(
            @RequestParam(defaultValue = "0") int weeksAgo) {
        return ResponseEntity.ok(marketingWeeklyReportService.buildReport(weeksAgo));
    }

    @PostMapping("/score-weights/auto-adjust/run")
    @Operation(summary = "Run score auto-adjust once",
        description = "auto_adjust=false면 적용 없이 report-only 결과 반환")
    @ApiResponse(responseCode = "200", description = "Adjust result")
    @Auditable(action = "RUN_MARKETING_SCORE_AUTO_ADJUST")
    public ResponseEntity<MarketingScoreAutoAdjustService.AdjustResult> runAutoAdjust() {
        return ResponseEntity.ok(marketingScoreAutoAdjustService.runWeeklyAdjust());
    }
}
