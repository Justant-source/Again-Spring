package com.againspring.api.dto.response;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.againspring.domain.marketing.MarketingContent;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Response DTO for marketing content with full details.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentResponse {
	private Long id;
	private Long simulationId;
	private String platform; // X, INSTAGRAM, NAVER_BLOG
	private String title;
	private String bodyText;
	private List<String> hashtags;
	private String status; // DRAFT, REVIEW, APPROVED, EXPORTED, REJECTED
	private String safetyCheckJson;
	private Long editedBy;
	private Long approvedBy;
	private Instant createdAt;
	private Instant updatedAt;
	private Instant scheduledAt;
	private Instant publishedAt;
	private String publishedUrl;
	private String performanceJson;
	private String imagePaths;

	/**
	 * Factory method to create response from entity.
	 */
	public static ContentResponse from(MarketingContent content) {
		List<String> hashtags = null;
		if (content.getHashtags() != null && !content.getHashtags().isEmpty()) {
			hashtags = Arrays.asList(content.getHashtags().split(","));
		}

		return ContentResponse.builder()
				.id(content.getId())
				.simulationId(content.getSimulationId())
				.platform(content.getPlatform().toString())
				.title(content.getTitle())
				.bodyText(content.getBodyText())
				.hashtags(hashtags)
				.status(content.getStatus().toString())
				.safetyCheckJson(content.getSafetyCheckJson())
				.editedBy(content.getEditedBy())
				.approvedBy(content.getApprovedBy())
				.createdAt(content.getCreatedAt())
				.updatedAt(content.getUpdatedAt())
				.scheduledAt(content.getScheduledAt())
				.publishedAt(content.getPublishedAt())
				.publishedUrl(content.getPublishedUrl())
				.performanceJson(content.getPerformanceJson())
				.imagePaths(content.getImagePaths())
				.build();
	}
}
