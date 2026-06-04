package com.againspring.aiuser.orchestrator.repository;

import com.againspring.aiuser.orchestrator.domain.AiUserRuntime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiUserRuntimeRepository extends JpaRepository<AiUserRuntime, Integer> {
    // Use findById(1) to get the singleton row
}
