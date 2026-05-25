package com.againspring.service.marketing;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.againspring.api.dto.response.ContentResponse;
import com.againspring.api.dto.response.ContentSummaryResponse;
import com.againspring.api.dto.request.ScheduleRequest;
import com.againspring.api.dto.request.PublishRequest;
import com.againspring.api.dto.request.ContentFromTemplateRequest;
import com.againspring.domain.marketing.MarketingContent;
import com.againspring.domain.marketing.MarketingSimulation;
import com.againspring.domain.marketing.MarketingContentTemplate;
import com.againspring.repository.marketing.MarketingContentRepository;
import com.againspring.repository.marketing.MarketingSimulationRepository;
import com.againspring.repository.marketing.MarketingContentTemplateRepository;
import com.againspring.safety.MarketingCopyGuard;
import com.againspring.service.marketing.content.PlatformContentRouter;
import com.againspring.service.marketing.content.PlatformDescriptorLoader;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing marketing content generation and lifecycle.
 * Async generation is delegated to ContentGenerationExecutor to preserve @Async proxy.
 */
@Service
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class ContentService {

	private final MarketingContentRepository contentRepo;
	private final MarketingSimulationRepository simRepo;
	private final MarketingCopyGuard copyGuard;
	private final ContentGenerationExecutor generationExecutor;
	private final MarketingContentTemplateRepository templateRepo;
	private final PlatformContentRouter router;

	/**
	 * 비동기 콘텐츠 생성 — GENERATING 레코드를 즉시 저장 후 202 반환.
	 * 실제 LLM 작업은 ContentGenerationExecutor(marketingExecutor)에서 백그라운드 실행.
	 */
	@Transactional
	public ContentResponse generateAsync(Long simulationId, String platformStr) {
		MarketingSimulation simulation = simRepo.findById(simulationId)
				.orElseThrow(() -> new EntityNotFoundException("Simulation not found: " + simulationId));

		if (simulation.getStatus() != MarketingSimulation.Status.COMPLETED) {
			throw new IllegalStateException("Simulation is not completed: " + simulation.getStatus());
		}

		MarketingContent.Platform platform;
		try {
			platform = MarketingContent.Platform.valueOf(platformStr.toUpperCase());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid platform: " + platformStr);
		}

		if (contentRepo.findBySimulationIdAndPlatform(simulationId, platform).isPresent()) {
			throw new IllegalArgumentException(
					"Content already exists for simulation " + simulationId + " on platform " + platform);
		}

		// GENERATING 레코드를 즉시 저장
		MarketingContent stub = MarketingContent.builder()
				.simulationId(simulationId)
				.platform(platform)
				.bodyText("")
				.status(MarketingContent.Status.GENERATING)
				.build();
		MarketingContent saved = contentRepo.save(stub);
		log.info("Queued content generation: id={}, simulation={}, platform={}", saved.getId(), simulationId, platform);

		// 별도 빈을 통해 @Async 프록시 올바르게 적용
		generationExecutor.execute(saved.getId(), simulation, platform);

		return ContentResponse.from(saved);
	}

	/**
	 * List all content with optional filters.
	 */
	public List<ContentSummaryResponse> findAll(String statusStr, String platformStr) {
		List<MarketingContent> contents;

		if (statusStr != null && platformStr != null) {
			MarketingContent.Status status = MarketingContent.Status.valueOf(statusStr.toUpperCase());
			MarketingContent.Platform platform = MarketingContent.Platform.valueOf(platformStr.toUpperCase());
			contents = contentRepo.findByPlatformAndStatus(platform, status,
					org.springframework.data.domain.Pageable.unpaged()).getContent();
		} else if (statusStr != null) {
			MarketingContent.Status status = MarketingContent.Status.valueOf(statusStr.toUpperCase());
			contents = contentRepo.findByStatus(status,
					org.springframework.data.domain.Pageable.unpaged()).getContent();
		} else if (platformStr != null) {
			MarketingContent.Platform platform = MarketingContent.Platform.valueOf(platformStr.toUpperCase());
			contents = contentRepo.findAll().stream()
					.filter(c -> c.getPlatform() == platform)
					.collect(Collectors.toList());
		} else {
			contents = contentRepo.findAll();
		}

		return contents.stream()
				.map(ContentSummaryResponse::from)
				.collect(Collectors.toList());
	}

	/**
	 * Find single content by ID.
	 */
	public ContentResponse findById(Long id) {
		MarketingContent content = contentRepo.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Content not found: " + id));
		return ContentResponse.from(content);
	}

	/**
	 * Update content body text.
	 */
	public ContentResponse update(Long id, String bodyText) {
		MarketingContent content = contentRepo.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Content not found: " + id));

		content.setBodyText(bodyText);
		MarketingContent updated = contentRepo.save(content);
		log.info("Updated marketing content: id={}", id);

		return ContentResponse.from(updated);
	}

	/**
	 * Approve content after manual review and re-run safety check.
	 */
	public ContentResponse approve(Long id) {
		MarketingContent content = contentRepo.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Content not found: " + id));

		boolean hasViolations = copyGuard.hasViolations(content.getBodyText());
		if (!hasViolations) {
			content.setStatus(MarketingContent.Status.APPROVED);
			content.setSafetyCheckJson(String.format(
					"{\"violations_detected\": false, \"checked_at\": \"%s\"}", java.time.Instant.now()));
		} else {
			content.setSafetyCheckJson(String.format(
					"{\"violations_detected\": true, \"checked_at\": \"%s\"}", java.time.Instant.now()));
		}

		MarketingContent updated = contentRepo.save(content);
		log.info("Approved marketing content: id={}, status={}", id, updated.getStatus());

		return ContentResponse.from(updated);
	}

	/**
	 * Delete content permanently.
	 */
	@Transactional
	public void delete(Long id) {
		if (!contentRepo.existsById(id)) {
			throw new EntityNotFoundException("Content not found: " + id);
		}
		contentRepo.deleteById(id);
		log.info("Deleted marketing content: id={}", id);
	}

	/**
	 * Reject content with reason.
	 */
	public ContentResponse reject(Long id, String reason) {
		MarketingContent content = contentRepo.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Content not found: " + id));

		content.setStatus(MarketingContent.Status.REJECTED);
		content.setSafetyCheckJson(String.format(
				"{\"rejected\": true, \"reason\": \"%s\", \"rejected_at\": \"%s\"}",
				reason != null ? reason : "No reason provided", java.time.Instant.now()));

		MarketingContent updated = contentRepo.save(content);
		log.info("Rejected marketing content: id={}, reason={}", id, reason);

		return ContentResponse.from(updated);
	}

	@Transactional
	public ContentResponse schedule(Long id, ScheduleRequest request) {
		MarketingContent content = contentRepo.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Content not found: " + id));
		content.setScheduledAt(request.getScheduledAt());
		MarketingContent updated = contentRepo.save(content);
		log.info("Scheduled content: id={}, scheduledAt={}", id, request.getScheduledAt());
		return ContentResponse.from(updated);
	}

	@Transactional
	public ContentResponse publish(Long id, PublishRequest request) {
		MarketingContent content = contentRepo.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Content not found: " + id));
		content.setPublishedAt(request != null && request.getPublishedAt() != null
				? request.getPublishedAt()
				: java.time.Instant.now());
		if (request != null && request.getPublishedUrl() != null) {
			content.setPublishedUrl(request.getPublishedUrl());
		}
		content.setStatus(MarketingContent.Status.EXPORTED);
		MarketingContent updated = contentRepo.save(content);
		log.info("Published content: id={}, url={}", id, content.getPublishedUrl());
		return ContentResponse.from(updated);
	}

	@Transactional
	public ContentResponse generateFromTemplate(Long templateId, ContentFromTemplateRequest req) {
		MarketingContentTemplate template = templateRepo.findById(templateId)
				.orElseThrow(() -> new EntityNotFoundException("Template not found: " + templateId));

		MarketingSimulation simulation = simRepo.findById(req.getSimulationId())
				.orElseThrow(() -> new EntityNotFoundException("Simulation not found: " + req.getSimulationId()));

		if (simulation.getStatus() != MarketingSimulation.Status.COMPLETED) {
			throw new IllegalStateException("Simulation is not completed");
		}

		MarketingContent.Platform platform = req.getPlatform() != null
				? MarketingContent.Platform.valueOf(req.getPlatform().toUpperCase())
				: template.getPlatform();

		String instantiatedBody = template.getBodyTemplate();
		if (req.getVariables() != null) {
			for (java.util.Map.Entry<String, String> entry : req.getVariables().entrySet()) {
				instantiatedBody = instantiatedBody.replace("${" + entry.getKey() + "}", entry.getValue());
			}
		}
		if (instantiatedBody.contains("${") || instantiatedBody.contains("{{")) {
			throw new IllegalStateException("Template has unresolved placeholders. Provide all required variable values.");
		}

		try {
			String generated = router.generateWithTemplate(
					platform,
					"시뮬레이션 대화 기반 콘텐츠 생성 (템플릿 사용)",
					"general",
					instantiatedBody
			);
			String sanitized = copyGuard.sanitize(generated);

			MarketingContent content = MarketingContent.builder()
					.simulationId(req.getSimulationId())
					.platform(platform)
					.bodyText(sanitized)
					.status(copyGuard.hasViolations(generated) ? MarketingContent.Status.REVIEW : MarketingContent.Status.DRAFT)
					.templateId(templateId)
					.safetyCheckJson(String.format(
							"{\"violations_detected\": %b, \"template_id\": %d, \"checked_at\": \"%s\"}",
							copyGuard.hasViolations(generated), templateId, java.time.Instant.now()))
					.build();

			MarketingContent saved = contentRepo.save(content);
			log.info("Generated from template: templateId={}, contentId={}", templateId, saved.getId());
			return ContentResponse.from(saved);
		} catch (Exception e) {
			throw new RuntimeException("Content generation from template failed: " + e.getMessage(), e);
		}
	}
}
