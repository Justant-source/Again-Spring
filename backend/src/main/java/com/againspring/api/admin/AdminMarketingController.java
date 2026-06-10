package com.againspring.api.admin;

import com.againspring.annotation.Auditable;
import com.againspring.api.admin.dto.JobResponse;
import com.againspring.api.admin.dto.CreateJobRequest;
import com.againspring.domain.marketing.MarketingJob;
import com.againspring.marketing.AsmClient;
import com.againspring.marketing.MarketingJobService;
import com.againspring.marketing.dto.AsmJobView;
import com.againspring.repository.marketing.MarketingJobRepository;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
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
}
