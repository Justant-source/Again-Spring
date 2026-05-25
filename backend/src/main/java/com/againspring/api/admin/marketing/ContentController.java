package com.againspring.api.admin.marketing;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.againspring.api.dto.request.ContentRequest;
import com.againspring.api.dto.response.ContentResponse;
import com.againspring.api.dto.response.ContentSummaryResponse;
import com.againspring.service.marketing.ContentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Admin API controller for managing marketing content generation.
 * Secured to ADMIN role only.
 */
@RestController
@RequestMapping("/api/admin/marketing/contents")
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Marketing Content", description = "Manage generated marketing content")
@SecurityRequirement(name = "bearerAuth")
public class ContentController {

	private final ContentService contentService;

	/**
	 * Generate marketing content for a completed simulation.
	 *
	 * @param simulationId ID of the completed simulation
	 * @param platform Target platform (x, instagram, naver_blog)
	 * @return Generated content (201 Created)
	 */
	@PostMapping("/generate")
	@Operation(summary = "Generate marketing content (async)", description = "Queue content generation — returns 202 with GENERATING record immediately")
	public ResponseEntity<ContentResponse> generate(
			@RequestParam Long simulationId,
			@RequestParam String platform) {
		ContentResponse response = contentService.generateAsync(simulationId, platform);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
	}

	/**
	 * List all marketing content with optional filters.
	 *
	 * @param status Filter by status (DRAFT, REVIEW, APPROVED, EXPORTED, REJECTED)
	 * @param platform Filter by platform (X, INSTAGRAM, NAVER_BLOG)
	 * @return List of content summaries
	 */
	@GetMapping
	@Operation(summary = "List marketing content", description = "List all marketing content with optional filters")
	public ResponseEntity<List<ContentSummaryResponse>> list(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String platform) {
		List<ContentSummaryResponse> contents = contentService.findAll(status, platform);
		return ResponseEntity.ok(contents);
	}

	/**
	 * Get single marketing content by ID.
	 *
	 * @param id Content ID
	 * @return Content details
	 */
	@GetMapping("/{id}")
	@Operation(summary = "Get content details", description = "Retrieve full details of marketing content")
	public ResponseEntity<ContentResponse> getById(@PathVariable Long id) {
		ContentResponse response = contentService.findById(id);
		return ResponseEntity.ok(response);
	}

	/**
	 * Update marketing content body text.
	 *
	 * @param id Content ID
	 * @param request Update request containing new body text
	 * @return Updated content
	 */
	@PutMapping("/{id}")
	@Operation(summary = "Update content", description = "Update marketing content body text")
	public ResponseEntity<ContentResponse> update(
			@PathVariable Long id,
			@RequestBody ContentRequest request) {
		ContentResponse response = contentService.update(id, request.getPlatform());
		return ResponseEntity.ok(response);
	}

	/**
	 * Approve marketing content after manual review.
	 * Re-runs safety checks before approval.
	 *
	 * @param id Content ID
	 * @return Approved content
	 */
	@PostMapping("/{id}/approve")
	@Operation(summary = "Approve content", description = "Approve content and re-run safety checks")
	public ResponseEntity<ContentResponse> approve(@PathVariable Long id) {
		ContentResponse response = contentService.approve(id);
		return ResponseEntity.ok(response);
	}

	/**
	 * Reject marketing content with reason.
	 *
	 * @param id Content ID
	 * @param reason Rejection reason
	 * @return Rejected content
	 */
	@PostMapping("/{id}/reject")
	@Operation(summary = "Reject content", description = "Reject content with reason")
	public ResponseEntity<ContentResponse> reject(
			@PathVariable Long id,
			@RequestParam(required = false) String reason) {
		ContentResponse response = contentService.reject(id, reason);
		return ResponseEntity.ok(response);
	}
}
