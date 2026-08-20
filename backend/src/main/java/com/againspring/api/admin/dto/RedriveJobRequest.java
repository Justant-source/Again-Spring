package com.againspring.api.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request for redrive endpoint: regenerate or recreate failed marketing jobs.
 * Accepts either a list of job IDs or a filter query (status, since, etc).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedriveJobRequest {
    /**
     * List of job IDs to redrive (optional).
     */
    private List<Long> jobIds;

    /**
     * Filter map with optional keys: status (e.g., "FAILED"), since (ISO8601 timestamp).
     * If both jobIds and filter are present, filter is ignored.
     */
    private Map<String, String> filter;

    /**
     * When true, only retry platforms that don't already have PUBLISHED state in publications.
     * When false, retry all platforms (default).
     */
    @Builder.Default
    private boolean skipExisting = false;
}
