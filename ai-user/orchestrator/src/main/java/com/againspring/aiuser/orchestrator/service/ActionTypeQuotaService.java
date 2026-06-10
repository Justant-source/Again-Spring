package com.againspring.aiuser.orchestrator.service;

import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.enums.ActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 액션타입별 일일 쿼터 서비스.
 * 각 액션 타입(POST, COMMENT, REPLY, VOTE, LIKE, COMMENT_LIKE)별로
 * 오늘 생성된 개수와 결핍도(deficit = target - done)를 계산.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActionTypeQuotaService {

    private final JdbcTemplate jdbcTemplate;
    private final DailyPostQuotaService dailyPostQuotaService;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 액션타입별 타겟·완료·결핍 정보.
     * deficit = max(0, target - done)
     */
    public record TypeQuota(int target, int done, int deficit) {
        public static TypeQuota zero() {
            return new TypeQuota(0, 0, 0);
        }

        public static TypeQuota of(int target, int done) {
            return new TypeQuota(target, done, Math.max(0, target - done));
        }
    }

    /**
     * 오늘(KST) 기준 액션타입별 타겟·완료·결핍 맵 반환.
     * 예외 발생 시 모든 값을 zero()로 반환(결함 친화).
     */
    public Map<ActionType, TypeQuota> computeToday(AiUserGenerationConfig config) {
        try {
            Map<ActionType, TypeQuota> result = new HashMap<>();

            // POST: DailyPostQuotaService 위임
            int postsDone = dailyPostQuotaService.postsCreatedToday();
            result.put(ActionType.POST, TypeQuota.of(config.getTargetPosts(), postsDone));

            // COMMENT, REPLY, VOTE, LIKE, COMMENT_LIKE: 단일 쿼리로 GROUP BY 조회
            Map<String, Integer> actionCounts = fetchActionCounts();

            // COMMENT: autoComment && backendComment != "OFF" 일 때만 적용
            if (config.isAutoComment() && !config.isOff("COMMENT")) {
                int commentsDone = actionCounts.getOrDefault("COMMENT", 0);
                result.put(ActionType.COMMENT, TypeQuota.of(config.getTargetComments(), commentsDone));
            } else {
                result.put(ActionType.COMMENT, TypeQuota.zero());
            }

            // REPLY: autoReply && backendReply != "OFF" 일 때만 적용
            if (config.isAutoReply() && !config.isOff("REPLY")) {
                int repliesDone = actionCounts.getOrDefault("REPLY", 0);
                result.put(ActionType.REPLY, TypeQuota.of(config.getTargetReplies(), repliesDone));
            } else {
                result.put(ActionType.REPLY, TypeQuota.zero());
            }

            // VOTE: targetVotes > 0 일 때만 적용 (LLM 미필요, 항상 활성화)
            int votesDone = actionCounts.getOrDefault("VOTE", 0);
            if (config.getTargetVotes() > 0) {
                result.put(ActionType.VOTE, TypeQuota.of(config.getTargetVotes(), votesDone));
            } else {
                result.put(ActionType.VOTE, TypeQuota.zero());
            }

            // LIKE: targetLikes > 0 일 때만 적용
            // LIKE done = direct LIKE + COMMENT_LIKE (admin 좋아요 타겟은 둘 다 포함)
            int likeDone = actionCounts.getOrDefault("LIKE", 0);
            int commentLikeDone = actionCounts.getOrDefault("COMMENT_LIKE", 0);
            int totalLikesDone = likeDone + commentLikeDone;
            if (config.getTargetLikes() > 0) {
                result.put(ActionType.LIKE, TypeQuota.of(config.getTargetLikes(), totalLikesDone));
            } else {
                result.put(ActionType.LIKE, TypeQuota.zero());
            }

            return Collections.unmodifiableMap(result);

        } catch (Exception e) {
            log.warn("computeToday query failed: {}", e.getMessage());
            // 결함 친화: 모든 액션타입을 zero()로 반환
            Map<ActionType, TypeQuota> fallback = new HashMap<>();
            fallback.put(ActionType.POST, TypeQuota.zero());
            fallback.put(ActionType.COMMENT, TypeQuota.zero());
            fallback.put(ActionType.REPLY, TypeQuota.zero());
            fallback.put(ActionType.VOTE, TypeQuota.zero());
            fallback.put(ActionType.LIKE, TypeQuota.zero());
            return Collections.unmodifiableMap(fallback);
        }
    }

    /**
     * 오늘(KST) persona_action_log에서
     * COMMENT, REPLY, VOTE, LIKE, COMMENT_LIKE의 POSTED 개수를 GROUP BY로 조회.
     * 반환: actionType → count 맵
     */
    private Map<String, Integer> fetchActionCounts() {
        ZonedDateTime todayStart = LocalDate.now(KST).atStartOfDay(KST);
        Timestamp since = Timestamp.from(todayStart.toInstant());

        String sql = "SELECT action_type, COUNT(*) as cnt " +
                "FROM persona_action_log " +
                "WHERE status = 'POSTED' " +
                "  AND action_type IN ('COMMENT', 'REPLY', 'VOTE', 'LIKE', 'COMMENT_LIKE') " +
                "  AND created_at >= ? " +
                "GROUP BY action_type";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, since);
        Map<String, Integer> result = new HashMap<>();

        for (Map<String, Object> row : rows) {
            String actionType = (String) row.get("action_type");
            Number count = (Number) row.get("cnt");
            result.put(actionType, count != null ? count.intValue() : 0);
        }

        return result;
    }
}
