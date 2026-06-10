package com.againspring.aiuser.orchestrator.service;

import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.enums.ActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * ActionTypeQuotaService 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ActionTypeQuotaServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DailyPostQuotaService dailyPostQuotaService;

    @Mock
    private AiUserGenerationConfig config;

    private ActionTypeQuotaService service;

    @BeforeEach
    void setUp() {
        service = new ActionTypeQuotaService(jdbcTemplate, dailyPostQuotaService);
    }

    @Test
    void allTargetsZero_returnsAllZeroDeficits() {
        // Arrange
        when(config.getTargetPosts()).thenReturn(0);
        when(config.getTargetComments()).thenReturn(0);
        when(config.getTargetReplies()).thenReturn(0);
        when(config.getTargetVotes()).thenReturn(0);
        when(config.getTargetLikes()).thenReturn(0);
        when(config.isAutoComment()).thenReturn(true);
        when(config.isAutoReply()).thenReturn(true);
        when(config.isOff("COMMENT")).thenReturn(false);
        when(config.isOff("REPLY")).thenReturn(false);
        when(dailyPostQuotaService.postsCreatedToday()).thenReturn(0);
        mockQueryForList(new ArrayList<>());

        // Act
        Map<ActionType, ActionTypeQuotaService.TypeQuota> result = service.computeToday(config);

        // Assert
        assertEquals(ActionTypeQuotaService.TypeQuota.zero(), result.get(ActionType.POST));
        assertEquals(ActionTypeQuotaService.TypeQuota.zero(), result.get(ActionType.COMMENT));
        assertEquals(ActionTypeQuotaService.TypeQuota.zero(), result.get(ActionType.REPLY));
        assertEquals(ActionTypeQuotaService.TypeQuota.zero(), result.get(ActionType.VOTE));
        assertEquals(ActionTypeQuotaService.TypeQuota.zero(), result.get(ActionType.LIKE));
    }

    @Test
    void postsGateDelegatedToDailyPostQuotaService() {
        // Arrange
        when(config.getTargetPosts()).thenReturn(10);
        when(config.getTargetComments()).thenReturn(0);
        when(config.getTargetReplies()).thenReturn(0);
        when(config.getTargetVotes()).thenReturn(0);
        when(config.getTargetLikes()).thenReturn(0);
        when(config.isAutoComment()).thenReturn(false);
        when(config.isAutoReply()).thenReturn(false);
        when(config.isOff("COMMENT")).thenReturn(false);
        when(config.isOff("REPLY")).thenReturn(false);
        when(dailyPostQuotaService.postsCreatedToday()).thenReturn(5);
        mockQueryForList(new ArrayList<>());

        // Act
        Map<ActionType, ActionTypeQuotaService.TypeQuota> result = service.computeToday(config);

        // Assert
        ActionTypeQuotaService.TypeQuota postQuota = result.get(ActionType.POST);
        assertEquals(10, postQuota.target());
        assertEquals(5, postQuota.done());
        assertEquals(5, postQuota.deficit());
    }

    @Test
    void likeCountIncludesCommentLike() {
        // Arrange
        when(config.getTargetPosts()).thenReturn(0);
        when(config.getTargetComments()).thenReturn(0);
        when(config.getTargetReplies()).thenReturn(0);
        when(config.getTargetVotes()).thenReturn(0);
        when(config.getTargetLikes()).thenReturn(50);
        when(config.isAutoComment()).thenReturn(false);
        when(config.isAutoReply()).thenReturn(false);
        when(config.isOff("COMMENT")).thenReturn(false);
        when(config.isOff("REPLY")).thenReturn(false);
        when(dailyPostQuotaService.postsCreatedToday()).thenReturn(0);

        // Mock query result: LIKE=30, COMMENT_LIKE=10
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("action_type", "LIKE", "cnt", 30));
        rows.add(Map.of("action_type", "COMMENT_LIKE", "cnt", 10));
        mockQueryForList(rows);

        // Act
        Map<ActionType, ActionTypeQuotaService.TypeQuota> result = service.computeToday(config);

        // Assert
        ActionTypeQuotaService.TypeQuota likeQuota = result.get(ActionType.LIKE);
        assertEquals(50, likeQuota.target());
        assertEquals(40, likeQuota.done()); // 30 + 10
        assertEquals(10, likeQuota.deficit());
    }

    @Test
    void autoCommentFalse_commentDeficitZero() {
        // Arrange
        when(config.getTargetPosts()).thenReturn(0);
        when(config.getTargetComments()).thenReturn(150);
        when(config.getTargetReplies()).thenReturn(0);
        when(config.getTargetVotes()).thenReturn(0);
        when(config.getTargetLikes()).thenReturn(0);
        when(config.isAutoComment()).thenReturn(false); // Disabled
        when(config.isAutoReply()).thenReturn(false);
        when(dailyPostQuotaService.postsCreatedToday()).thenReturn(0);

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("action_type", "COMMENT", "cnt", 50)); // Exists in DB but ignored
        mockQueryForList(rows);

        // Act
        Map<ActionType, ActionTypeQuotaService.TypeQuota> result = service.computeToday(config);

        // Assert
        assertEquals(ActionTypeQuotaService.TypeQuota.zero(), result.get(ActionType.COMMENT));
    }

    @Test
    void backendCommentOff_commentDeficitZero() {
        // Arrange
        when(config.getTargetPosts()).thenReturn(0);
        when(config.getTargetComments()).thenReturn(150);
        when(config.getTargetReplies()).thenReturn(0);
        when(config.getTargetVotes()).thenReturn(0);
        when(config.getTargetLikes()).thenReturn(0);
        when(config.isAutoComment()).thenReturn(true);
        when(config.isOff("COMMENT")).thenReturn(true); // backend=OFF
        when(config.isAutoReply()).thenReturn(false);
        when(dailyPostQuotaService.postsCreatedToday()).thenReturn(0);

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("action_type", "COMMENT", "cnt", 50));
        mockQueryForList(rows);

        // Act
        Map<ActionType, ActionTypeQuotaService.TypeQuota> result = service.computeToday(config);

        // Assert
        assertEquals(ActionTypeQuotaService.TypeQuota.zero(), result.get(ActionType.COMMENT));
    }

    @Test
    void commentReplyDone_deficitCorrect() {
        // Arrange
        when(config.getTargetPosts()).thenReturn(0);
        when(config.getTargetComments()).thenReturn(150);
        when(config.getTargetReplies()).thenReturn(100);
        when(config.getTargetVotes()).thenReturn(0);
        when(config.getTargetLikes()).thenReturn(0);
        when(config.isAutoComment()).thenReturn(true);
        when(config.isOff("COMMENT")).thenReturn(false); // backend != OFF
        when(config.isAutoReply()).thenReturn(true);
        when(config.isOff("REPLY")).thenReturn(false);
        when(dailyPostQuotaService.postsCreatedToday()).thenReturn(0);

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("action_type", "COMMENT", "cnt", 50));
        rows.add(Map.of("action_type", "REPLY", "cnt", 20));
        mockQueryForList(rows);

        // Act
        Map<ActionType, ActionTypeQuotaService.TypeQuota> result = service.computeToday(config);

        // Assert
        ActionTypeQuotaService.TypeQuota commentQuota = result.get(ActionType.COMMENT);
        assertEquals(150, commentQuota.target());
        assertEquals(50, commentQuota.done());
        assertEquals(100, commentQuota.deficit());

        ActionTypeQuotaService.TypeQuota replyQuota = result.get(ActionType.REPLY);
        assertEquals(100, replyQuota.target());
        assertEquals(20, replyQuota.done());
        assertEquals(80, replyQuota.deficit());
    }

    @Test
    void dbException_returnsAllZero() {
        // Arrange
        when(config.getTargetPosts()).thenReturn(10);
        when(config.getTargetComments()).thenReturn(20);
        when(config.getTargetReplies()).thenReturn(30);
        when(config.getTargetVotes()).thenReturn(40);
        when(config.getTargetLikes()).thenReturn(50);
        when(config.isAutoComment()).thenReturn(true);
        when(config.isAutoReply()).thenReturn(true);
        when(dailyPostQuotaService.postsCreatedToday()).thenThrow(new RuntimeException("DB error"));
        when(jdbcTemplate.queryForList(anyString(), any(java.sql.Timestamp.class))).thenReturn(new ArrayList<>());

        // Act
        Map<ActionType, ActionTypeQuotaService.TypeQuota> result = service.computeToday(config);

        // Assert
        // All deficits should be zero (fallback)
        assertEquals(ActionTypeQuotaService.TypeQuota.zero(), result.get(ActionType.POST));
        assertEquals(ActionTypeQuotaService.TypeQuota.zero(), result.get(ActionType.COMMENT));
        assertEquals(ActionTypeQuotaService.TypeQuota.zero(), result.get(ActionType.REPLY));
        assertEquals(ActionTypeQuotaService.TypeQuota.zero(), result.get(ActionType.VOTE));
        assertEquals(ActionTypeQuotaService.TypeQuota.zero(), result.get(ActionType.LIKE));
    }

    @Test
    void voteTargetZero_deficitZero() {
        // Arrange
        when(config.getTargetPosts()).thenReturn(0);
        when(config.getTargetComments()).thenReturn(0);
        when(config.getTargetReplies()).thenReturn(0);
        when(config.getTargetVotes()).thenReturn(0); // Target = 0
        when(config.getTargetLikes()).thenReturn(0);
        when(config.isAutoComment()).thenReturn(false);
        when(config.isAutoReply()).thenReturn(false);
        when(config.isOff("COMMENT")).thenReturn(false);
        when(config.isOff("REPLY")).thenReturn(false);
        when(dailyPostQuotaService.postsCreatedToday()).thenReturn(0);

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("action_type", "VOTE", "cnt", 100)); // Exists in DB but target=0
        mockQueryForList(rows);

        // Act
        Map<ActionType, ActionTypeQuotaService.TypeQuota> result = service.computeToday(config);

        // Assert
        assertEquals(ActionTypeQuotaService.TypeQuota.zero(), result.get(ActionType.VOTE));
    }

    private void mockQueryForList(List<Map<String, Object>> rows) {
        when(jdbcTemplate.queryForList(
                anyString(),
                any(java.sql.Timestamp.class)
        )).thenReturn(rows);
    }
}
