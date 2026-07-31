package com.againspring.api.admin;

import com.againspring.domain.ai.AiUserGenerationConfig;
import com.againspring.repository.ai.AiUserGenerationConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AdminAiUserControllerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private AiUserGenerationConfigRepository configRepository;

    @InjectMocks
    private AdminAiUserController controller;

    @Test
    public void getGenerationStatus_returns200_withCorrectCounts() {
        // Setup
        AiUserGenerationConfig mockConfig = AiUserGenerationConfig.builder()
                .id(1)
                .targetPosts(10)
                .targetComments(76)
                .targetReplies(44)
                .targetVotes(65)
                .targetLikes(157)
                .build();
        when(configRepository.findById(1)).thenReturn(Optional.of(mockConfig));

        // Mock posts query: 7 posts done
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Integer.class),
                any()
        )).thenReturn(7);

        // Mock action_log query
        List<Map<String, Object>> actionStats = Arrays.asList(
                createActionLogRow("COMMENT", "POSTED", 52),
                createActionLogRow("REPLY", "POSTED", 30),
                createActionLogRow("VOTE", "POSTED", 41),
                createActionLogRow("LIKE", "POSTED", 60),
                createActionLogRow("COMMENT_LIKE", "POSTED", 38),
                createActionLogRow("COMMENT", "FAILED", 2),
                createActionLogRow("REPLY", "FAILED", 1),
                createActionLogRow("VOTE", "BLOCKED", 1)
        );
        doReturn(actionStats)
                .when(jdbcTemplate)
                .queryForList(anyString(), any(Object.class));

        // Execute
        ResponseEntity<?> response = controller.getGenerationStatus();

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        AdminAiUserController.GenerationStatusResponse resp =
                (AdminAiUserController.GenerationStatusResponse) response.getBody();

        assertThat(resp.getTodayKst()).isNotNull();
        assertThat(resp.getTargets().getPosts().getDone()).isEqualTo(7);
        assertThat(resp.getTargets().getPosts().getTarget()).isEqualTo(10);
        assertThat(resp.getTargets().getPosts().getPercent()).isEqualTo(70);
        assertThat(resp.getTargets().getComments().getDone()).isEqualTo(52);
        assertThat(resp.getTargets().getComments().getTarget()).isEqualTo(76);
        assertThat(resp.getTargets().getComments().getPercent()).isEqualTo(68);
        assertThat(resp.getTargets().getReplies().getDone()).isEqualTo(30);
        assertThat(resp.getTargets().getReplies().getTarget()).isEqualTo(44);
        assertThat(resp.getTargets().getReplies().getPercent()).isEqualTo(68);
        assertThat(resp.getTargets().getVotes().getDone()).isEqualTo(41);
        assertThat(resp.getTargets().getVotes().getTarget()).isEqualTo(65);
        assertThat(resp.getTargets().getVotes().getPercent()).isEqualTo(63);
        assertThat(resp.getTargets().getLikes().getDone()).isEqualTo(98);
        assertThat(resp.getTargets().getLikes().getTarget()).isEqualTo(157);
        assertThat(resp.getTargets().getLikes().getPercent()).isEqualTo(62);
        assertThat(resp.getFailures().getFailed()).isEqualTo(3);
        assertThat(resp.getFailures().getBlocked()).isEqualTo(1);
    }

    @Test
    public void getGenerationStatus_allTargetsZero_returnsZeroPercent() {
        // Setup
        AiUserGenerationConfig zeroConfig = AiUserGenerationConfig.builder()
                .id(1)
                .targetPosts(0)
                .targetComments(0)
                .targetReplies(0)
                .targetVotes(0)
                .targetLikes(0)
                .build();
        when(configRepository.findById(1)).thenReturn(Optional.of(zeroConfig));

        // Mock posts query: 0 posts done
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Integer.class),
                any()
        )).thenReturn(0);

        // Mock action_log query: empty
        doReturn(Arrays.asList())
                .when(jdbcTemplate)
                .queryForList(anyString(), any(Object.class));

        // Execute
        ResponseEntity<?> response = controller.getGenerationStatus();

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        AdminAiUserController.GenerationStatusResponse resp =
                (AdminAiUserController.GenerationStatusResponse) response.getBody();

        assertThat(resp.getTargets().getPosts().getPercent()).isEqualTo(0);
        assertThat(resp.getTargets().getComments().getPercent()).isEqualTo(0);
        assertThat(resp.getTargets().getReplies().getPercent()).isEqualTo(0);
        assertThat(resp.getTargets().getVotes().getPercent()).isEqualTo(0);
        assertThat(resp.getTargets().getLikes().getPercent()).isEqualTo(0);
    }

    @Test
    public void getGenerationStatus_largeNumbers_computesPercentCorrectly() {
        // Setup
        AiUserGenerationConfig mockConfig = AiUserGenerationConfig.builder()
                .id(1)
                .targetPosts(100)
                .targetComments(1000)
                .targetReplies(0)
                .targetVotes(0)
                .targetLikes(0)
                .build();
        when(configRepository.findById(1)).thenReturn(Optional.of(mockConfig));

        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Integer.class),
                any()
        )).thenReturn(51);

        doReturn(Arrays.asList(
                createActionLogRow("COMMENT", "POSTED", 333)
        ))
                .when(jdbcTemplate)
                .queryForList(anyString(), any(Object.class));

        // Execute
        ResponseEntity<?> response = controller.getGenerationStatus();
        AdminAiUserController.GenerationStatusResponse resp =
                (AdminAiUserController.GenerationStatusResponse) response.getBody();

        // Assert: 51/100 = 51%, 333/1000 = 33.3% rounds to 33%
        assertThat(resp.getTargets().getPosts().getPercent()).isEqualTo(51);
        assertThat(resp.getTargets().getComments().getPercent()).isEqualTo(33);
    }

    private Map<String, Object> createActionLogRow(String actionType, String status, int count) {
        Map<String, Object> row = new HashMap<>();
        row.put("action_type", actionType);
        row.put("status", status);
        row.put("cnt", count);
        return row;
    }
}
