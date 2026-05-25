package com.againspring.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request DTO for generating marketing content.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContentRequest {
	private Long simulationId;
	private String platform; // x, instagram, naver_blog
}
