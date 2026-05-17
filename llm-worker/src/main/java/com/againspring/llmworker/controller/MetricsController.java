package com.againspring.llmworker.controller;

import com.againspring.llmworker.dto.WorkerMetrics;
import com.againspring.llmworker.pool.LlmWorkerPool;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class MetricsController {

    private final LlmWorkerPool pool;

    @GetMapping("/metrics")
    public ResponseEntity<WorkerMetrics> getMetrics() {
        return ResponseEntity.ok(pool.getMetrics());
    }
}
