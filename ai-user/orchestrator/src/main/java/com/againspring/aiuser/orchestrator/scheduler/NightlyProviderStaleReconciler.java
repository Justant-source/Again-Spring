package com.againspring.aiuser.orchestrator.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * nightly-ai-user-batch.sh가 provider_*를 CLAUDE로 켠 뒤 복원 없이 죽은 경우(SIGKILL·재부팅)를 매시 감지해
 * ai_provider_snapshot에서 되돌린다. 스크립트 자체 trap 복원의 2차 안전망.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NightlyProviderStaleReconciler {
    static final String STALE_SQL = """
        SELECT COUNT(*) FROM ai_provider_snapshot s JOIN ai_user_generation_config c ON c.id = 1
        WHERE s.id = 1 AND s.restored_at IS NULL AND c.updated_by = 'nightly-batch'
          AND c.updated_at < (UTC_TIMESTAMP() - INTERVAL 3 HOUR)""";
    static final String RESTORE_SQL = """
        UPDATE ai_user_generation_config c JOIN ai_provider_snapshot s ON s.id = 1
        SET c.provider_ai_post_bundle = s.provider_ai_post_bundle,
            c.provider_human_post_plan = s.provider_human_post_plan,
            c.provider_human_interaction = s.provider_human_interaction,
            c.updated_by = 'nightly-batch-stale-restore', c.updated_at = UTC_TIMESTAMP()
        WHERE c.id = 1""";
    static final String MARK_SQL = "UPDATE ai_provider_snapshot SET restored_at=UTC_TIMESTAMP(3) WHERE id = 1";

    private final JdbcTemplate jdbc;

    @Scheduled(cron = "0 7 * * * *")
    public void reconcile() {
        Integer stale = jdbc.queryForObject(STALE_SQL, Integer.class);
        if (stale == null || stale == 0) return;
        log.warn("[NightlyProviderStale] provider_* left at nightly-batch values >3h without restore — restoring from ai_provider_snapshot");
        jdbc.update(RESTORE_SQL);
        jdbc.update(MARK_SQL);
    }
}
