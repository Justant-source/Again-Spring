package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.LlmGenerationGate;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * LlmGenerationGate repository (singleton row, id=1 only).
 */
public interface LlmGenerationGateRepository extends JpaRepository<LlmGenerationGate, Integer> {
}
