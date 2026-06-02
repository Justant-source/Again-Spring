package com.againspring.seed;

import com.againspring.domain.User;
import com.againspring.seed.dto.SeedScenario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seed scenario builder (disabled — Session/Message/Report classes removed).
 * NOTE: Mediation code refactored. This component is now a stub.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeedScenarioBuilder {

    /**
     * Build all seed scenarios (stub).
     * @return [0, 0, 0] (no scenarios built)
     */
    @Transactional
    public int[] buildAll(List<SeedScenario> scenarios, List<User> users) {
        log.warn("SeedScenarioBuilder.buildAll() is disabled — Session/Message/Report classes removed");
        return new int[]{0, 0, 0};
    }
}
