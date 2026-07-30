package com.againspring.repository.ai;

import com.againspring.domain.ai.AiUserOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiUserOutboxEventRepository extends JpaRepository<AiUserOutboxEvent, String> {
    boolean existsByIdempotencyKey(String idempotencyKey);
}
