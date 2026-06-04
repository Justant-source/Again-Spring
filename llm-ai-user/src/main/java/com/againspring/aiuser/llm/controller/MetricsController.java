package com.againspring.aiuser.llm.controller;

import com.againspring.aiuser.llm.dto.WorkerMetrics;
import com.againspring.aiuser.llm.pool.LlmWorkerPool;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
