package com.againspring.api.admin.marketing;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.againspring.api.dto.request.ContentRequest;
import com.againspring.api.dto.request.ContentFromTemplateRequest;
import com.againspring.api.dto.request.PerformanceRequest;
import com.againspring.api.dto.request.ScheduleRequest;
import com.againspring.api.dto.request.PublishRequest;
import com.againspring.api.dto.response.ContentResponse;
import com.againspring.api.dto.response.ContentSummaryResponse;
import com.againspring.service.marketing.ContentService;
import com.againspring.service.marketing.PerformanceService;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Admin API controller for managing marketing content generation.
 * Secured to ADMIN role only.
 * 소스: 커뮤니티 게시글(Post) — 외부사연/시뮬레이션 제거.
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
	private final PerformanceService performanceService;

	/**
	 * 커뮤니티 게시글로부터 마케팅 콘텐츠를 비동기 생성한다.
	 * platforms 미지정 시 x, instagram, naver_blog 3종 동시 생성.
	 *
	 * @param postId    소스 커뮤니티 게시글 ID
	 * @param platforms 생성할 플랫폼 목록 (쉼표 구분 or 반복 파라미터)
	 * @return 생성 요청된 콘텐츠 목록 (202 Accepted, GENERATING 상태)
	 */
	@PostMapping("/generate-from-post")
	@Operation(summary = "Generate marketing content from community post (async)",
	           description = "Queue content generation from a community post — returns 202 with GENERATING records immediately")
	public ResponseEntity<List<ContentResponse>> generateFromPost(
			@RequestParam String postId,
			@RequestParam(required = false) List<String> platforms) {
		List<ContentResponse> responses = contentService.generateFromPost(postId, platforms);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(responses);
	}

	/**
	 * List all marketing content with optional filters.
	 */
	@GetMapping
	@Operation(summary = "List marketing content")
	public ResponseEntity<List<ContentSummaryResponse>> list(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String platform) {
		List<ContentSummaryResponse> contents = contentService.findAll(status, platform);
		return ResponseEntity.ok(contents);
	}

	/**
	 * Get single marketing content by ID.
	 */
	@GetMapping("/{id}")
	@Operation(summary = "Get content details")
	public ResponseEntity<ContentResponse> getById(@PathVariable Long id) {
		ContentResponse response = contentService.findById(id);
		return ResponseEntity.ok(response);
	}

	/**
	 * Update marketing content body text.
	 */
	@PutMapping("/{id}")
	@Operation(summary = "Update content body text")
	public ResponseEntity<ContentResponse> update(
			@PathVariable Long id,
			@RequestBody ContentRequest request) {
		ContentResponse response = contentService.update(id, request.getPlatform());
		return ResponseEntity.ok(response);
	}

	/**
	 * Approve marketing content after manual review.
	 */
	@PostMapping("/{id}/approve")
	@Operation(summary = "Approve content")
	public ResponseEntity<ContentResponse> approve(@PathVariable Long id) {
		ContentResponse response = contentService.approve(id);
		return ResponseEntity.ok(response);
	}

	/**
	 * Delete marketing content permanently.
	 */
	@DeleteMapping("/{id}")
	@Operation(summary = "Delete content")
	public ResponseEntity<Void> delete(@PathVariable Long id) {
		contentService.delete(id);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Reject marketing content with reason.
	 */
	@PostMapping("/{id}/reject")
	@Operation(summary = "Reject content")
	public ResponseEntity<ContentResponse> reject(
			@PathVariable Long id,
			@RequestParam(required = false) String reason) {
		ContentResponse response = contentService.reject(id, reason);
		return ResponseEntity.ok(response);
	}

	@PutMapping("/{id}/performance")
	@Operation(summary = "Record performance metrics")
	public ResponseEntity<ContentResponse> recordPerformance(
			@PathVariable Long id,
			@Valid @RequestBody PerformanceRequest request) {
		return ResponseEntity.ok(performanceService.recordPerformance(id, request));
	}

	@PutMapping("/{id}/schedule")
	@Operation(summary = "Set scheduled publish time")
	public ResponseEntity<ContentResponse> schedule(
			@PathVariable Long id,
			@Valid @RequestBody ScheduleRequest request) {
		return ResponseEntity.ok(contentService.schedule(id, request));
	}

	@PutMapping("/{id}/publish")
	@Operation(summary = "Mark content as published")
	public ResponseEntity<ContentResponse> publish(
			@PathVariable Long id,
			@RequestBody(required = false) PublishRequest request) {
		return ResponseEntity.ok(contentService.publish(id, request));
	}

	@PostMapping("/from-template/{templateId}")
	@Operation(summary = "Generate content from template")
	public ResponseEntity<ContentResponse> generateFromTemplate(
			@PathVariable Long templateId,
			@Valid @RequestBody ContentFromTemplateRequest request) {
		ContentResponse response = contentService.generateFromTemplate(templateId, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}
}
