package com.againspring.aiuser.llm.controller;

import com.againspring.aiuser.llm.dto.WorkerMetrics;
import com.againspring.aiuser.llm.pool.LlmWorkerPool;
import com.againspring.aiuser.llm.service.ProviderHealthRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class MetricsController {

    private final LlmWorkerPool pool;
    private final ProviderHealthRegistry healthRegistry;

    @GetMapping("/metrics")
    public ResponseEntity<WorkerMetrics> getMetrics() {
        return ResponseEntity.ok(pool.getMetrics());
    }

    @GetMapping("/providers/status")
    public Map<String, Object> providersStatus() {
        return healthRegistry.snapshot();
    }
}
