package com.againspring.repository.ai;

import com.againspring.domain.ai.BotRequestDedup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BotRequestDedupRepository extends JpaRepository<BotRequestDedup, String> {

    /**
     * MariaDB INSERT IGNORE waits for a concurrent holder of the unique key,
     * then returns 0. That makes the duplicate requester observe the committed
     * target mapping without treating an expected unique-key collision as an
     * exception/rollback condition.
     */
    @Modifying
    @Query(value = """
            INSERT IGNORE INTO bot_request_dedup
                (idempotency_key, target_type, bot_user_id, created_at)
            VALUES (:key, :targetType, :botUserId, NOW(3))
            """, nativeQuery = true)
    int claim(@Param("key") String key,
              @Param("targetType") String targetType,
              @Param("botUserId") String botUserId);
}
