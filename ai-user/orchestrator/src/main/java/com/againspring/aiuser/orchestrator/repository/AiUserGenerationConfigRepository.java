package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiUserGenerationConfigRepository extends JpaRepository<AiUserGenerationConfig, Integer> {
}
