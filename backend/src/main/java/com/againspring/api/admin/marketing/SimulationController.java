package com.againspring.api.admin.marketing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Marketing Simulation Controller (stub implementation).
 * NOTE: Report and marketing service classes removed. This endpoint is disabled.
 */
@RestController
@RequestMapping("/api/admin/marketing/simulations")
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SimulationController {
    // Stub: all endpoints disabled
}
