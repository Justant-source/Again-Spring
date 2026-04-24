package com.againspring.api.dto.response.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 온도 추이 타임라인 항목 (그래프용)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemperatureEntry {

    private String date; // ISO 8601 timestamp

    private double temperature;

}
