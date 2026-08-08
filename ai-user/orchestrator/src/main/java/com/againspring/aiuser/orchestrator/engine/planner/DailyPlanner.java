package com.againspring.aiuser.orchestrator.engine.planner;

import com.againspring.aiuser.orchestrator.domain.PersonaDailyQuota;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.repository.PersonaDailyQuotaRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaActionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyPlanner {

    private final PersonaRepository personaRepository;
    private final PersonaDailyQuotaRepository quotaRepository;
    private final PersonaActionLogRepository actionLogRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final int VIEW_PER_COMMENT = 20;
    private static final int VIEW_PER_VOTE = 5;
    private static final int DAILY_VIEW_LIMIT = 50000;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public void planForToday() {
        LocalDate today = LocalDate.now(KST);
        LocalDate yesterday = today.minusDays(1);

        log.info("DailyPlanner: planning quotas for {}", today);

        // Count yesterday's comments and votes across all personas
        String countQuery =
            "SELECT persona_id, " +
            "  SUM(CASE WHEN action_type = 'COMMENT' THEN 1 ELSE 0 END) as comment_count, " +
            "  SUM(CASE WHEN action_type = 'VOTE' THEN 1 ELSE 0 END) as vote_count, " +
            "  SUM(CASE WHEN action_type = 'REPLY' THEN 1 ELSE 0 END) as reply_count " +
            "FROM persona_action_log " +
            "WHERE DATE(CONVERT_TZ(created_at, '+00:00', '+09:00')) = ? " +
            "  AND status = 'POSTED' " +
            "GROUP BY persona_id";

        Map<String, EngagementMetrics> engagement = new HashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(countQuery, yesterday.toString());

        int totalComments = 0;
        int totalVotes = 0;
        int totalReplies = 0;

        for (Map<String, Object> row : rows) {
            String personaId = (String) row.get("persona_id");
            int comments = ((Number) row.getOrDefault("comment_count", 0)).intValue();
            int votes = ((Number) row.getOrDefault("vote_count", 0)).intValue();
            int replies = ((Number) row.getOrDefault("reply_count", 0)).intValue();

            engagement.put(personaId, new EngagementMetrics(comments, votes, replies));
            totalComments += comments;
            totalVotes += votes;
            totalReplies += replies;
        }

        // Calculate total views to generate
        int totalViews = (totalComments * VIEW_PER_COMMENT) + (totalVotes * VIEW_PER_VOTE);
        int cappedViews = Math.min(totalViews, DAILY_VIEW_LIMIT);

        log.info("DailyPlanner: total_comments={}, total_votes={}, calculated_views={}, capped_views={}",
            totalComments, totalVotes, totalViews, cappedViews);

        if (totalViews == 0) {
            log.info("DailyPlanner: no engagement yesterday, skipping view generation");
            return;
        }

        // Distribute views proportionally to engagement
        List<Persona> allPersonas = personaRepository.findAll();
        for (Persona persona : allPersonas) {
            int personaViewTarget = 0;

            if (engagement.containsKey(persona.getId())) {
                EngagementMetrics metrics = engagement.get(persona.getId());
                int personaEngagementViews = (metrics.comments * VIEW_PER_COMMENT) + (metrics.votes * VIEW_PER_VOTE);

                // Scale to daily cap
                personaViewTarget = (int) Math.round((double) personaEngagementViews * cappedViews / totalViews);
            }

            // Get or create quota for today
            PersonaDailyQuota quota = quotaRepository.findByPersonaIdAndDayBucket(persona.getId(), today)
                .orElseGet(() -> PersonaDailyQuota.builder()
                    .personaId(persona.getId())
                    .dayBucket(today)
                    .targetPosts(0)
                    .targetComments(0)
                    .targetReplies(0)
                    .targetVotes(0)
                    .targetLikes(0)
                    .targetViews(0)
                    .donePosts(0)
                    .doneComments(0)
                    .doneReplies(0)
                    .doneVotes(0)
                    .doneLikes(0)
                    .doneViews(0)
                    .build());

            // Rebalancing logic: never decrease target if already started
            // (replanning after failure should only append, not reduce)
            int oldTarget = quota.getTargetViews();
            int newTarget = personaViewTarget;
            if (quota.getDoneViews() > 0) {
                // 이미 수행 중인 쿼터가 있으면 절대 줄이지 말고, 증가분만 추가
                newTarget = Math.max(personaViewTarget, oldTarget);
                if (newTarget > oldTarget) {
                    log.debug("DailyPlanner: {} rebalance {} → {} views (已完成 {})",
                        persona.getId(), oldTarget, newTarget, quota.getDoneViews());
                }
            }

            quota.setTargetViews(newTarget);
            quotaRepository.save(quota);

            if (newTarget > 0) {
                log.debug("DailyPlanner: persona {} -> {} views", persona.getId(), newTarget);
            }
        }

        log.info("DailyPlanner: completed planning for {}", today);
    }

    private static class EngagementMetrics {
        final int comments;
        final int votes;
        final int replies;

        EngagementMetrics(int comments, int votes, int replies) {
            this.comments = comments;
            this.votes = votes;
            this.replies = replies;
        }
    }
}
