package com.againspring.api.dto.response;

import java.time.Instant;

import com.againspring.domain.marketing.MarketingContent;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Response DTO for marketing content summary (list view).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContentSummaryResponse {
	private Long id;
	private Long simulationId;
	private String platform; // X, INSTAGRAM, NAVER_BLOG
	private String status; // DRAFT, REVIEW, APPROVED, EXPORTED, REJECTED
	private Instant createdAt;

	/**
	 * Factory method to create response from entity.
	 */
	public static ContentSummaryResponse from(MarketingContent content) {
		return ContentSummaryResponse.builder()
				.id(content.getId())
				.simulationId(content.getSimulationId())
				.platform(content.getPlatform().toString())
				.status(content.getStatus().toString())
				.createdAt(content.getCreatedAt())
				.build();
	}
}
