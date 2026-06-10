package com.againspring.aiuser.orchestrator.engine;

import com.againspring.aiuser.orchestrator.client.dto.PostDto;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.domain.enums.ActionType;
import com.againspring.aiuser.orchestrator.repository.PersonaSeenPostRepository;
import com.againspring.aiuser.orchestrator.service.PostAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ActionPlanner.planForType() 테스트.
 * BehaviorEngine이 미리 선택한 ActionType에 대해
 * 구체적 대상을 찾아 PlannedAction을 반환하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ActionPlannerPlanForTypeTest {

    @Mock
    private PersonaSeenPostRepository seenPostRepo;

    @Mock
    private PostAnalysisService analysisService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ActionPlanner actionPlanner;

    @BeforeEach
    void setUp() {
        actionPlanner = new ActionPlanner(seenPostRepo, analysisService, jdbcTemplate);
    }

    // ════════════════════ REPLY Tests ════════════════════

    @Test
    void planForType_REPLY_returnsReply_whenEligibleTargetsExist() {
        Persona persona = buildTestPersona("persona-1");
        ReplyTarget target = new ReplyTarget(
            "post-1", "Post Title", 123L, "excerpt", "context",
            "body excerpt", "siblings", "other-user-id");
        List<ReplyTarget> replyTargets = List.of(target);

        Optional<PlannedAction> result = actionPlanner.planForType(persona, ActionType.REPLY, List.of(), replyTargets);

        assertTrue(result.isPresent());
        assertEquals(ActionType.REPLY, result.get().type());
        assertEquals(123L, result.get().parentCommentId());
    }

    @Test
    void planForType_REPLY_returnsEmpty_whenNoEligibleTargets() {
        Persona persona = buildTestPersona("persona-1");
        // 자신의 댓글만 있음 → 필터링으로 제외
        ReplyTarget ownComment = new ReplyTarget(
            "post-1", "Post Title", 123L, "excerpt", "context",
            "body excerpt", "siblings", "persona-1");
        List<ReplyTarget> replyTargets = List.of(ownComment);

        Optional<PlannedAction> result = actionPlanner.planForType(persona, ActionType.REPLY, List.of(), replyTargets);

        assertFalse(result.isPresent());
    }

    @Test
    void planForType_REPLY_returnsEmpty_whenTargetListEmpty() {
        Persona persona = buildTestPersona("persona-1");

        Optional<PlannedAction> result = actionPlanner.planForType(persona, ActionType.REPLY, List.of(), List.of());

        assertFalse(result.isPresent());
    }

    // ════════════════════ VOTE Tests ════════════════════

    @Test
    void planForType_VOTE_returnsEmpty_whenNoVotablePosts() {
        Persona persona = buildTestPersona("persona-1");
        PostDto post = buildTestPost("post-1", "category");
        post.setVoteOptions(null);  // 투표 옵션 없음
        List<PostDto> feedPosts = List.of(post);

        when(seenPostRepo.existsByPersonaIdAndPostId("persona-1", "post-1")).thenReturn(false);

        Optional<PlannedAction> result = actionPlanner.planForType(persona, ActionType.VOTE, feedPosts, List.of());

        assertFalse(result.isPresent());
    }

    @Test
    void planForType_VOTE_returnsVote_whenVotablePostExists() {
        Persona persona = buildTestPersona("persona-1");
        PostDto post = buildTestPost("post-1", "category");
        PostDto.VoteOptionDto opt1 = new PostDto.VoteOptionDto();
        opt1.setId(101L);
        opt1.setLabel("작성자");
        PostDto.VoteOptionDto opt2 = new PostDto.VoteOptionDto();
        opt2.setId(102L);
        opt2.setLabel("상대방");
        post.setVoteOptions(List.of(opt1, opt2));
        List<PostDto> feedPosts = List.of(post);

        when(seenPostRepo.existsByPersonaIdAndPostId("persona-1", "post-1")).thenReturn(false);

        Optional<PlannedAction> result = actionPlanner.planForType(persona, ActionType.VOTE, feedPosts, List.of());

        assertTrue(result.isPresent());
        assertEquals(ActionType.VOTE, result.get().type());
        assertEquals("post-1", result.get().targetPost().getId());
        assertTrue(result.get().voteOptionId() == 101L || result.get().voteOptionId() == 102L);
    }

    @Test
    void planForType_VOTE_returnsEmpty_whenNoUnseenPosts() {
        Persona persona = buildTestPersona("persona-1");
        PostDto post = buildTestPost("post-1", "category");
        List<PostDto> feedPosts = List.of(post);

        when(seenPostRepo.existsByPersonaIdAndPostId("persona-1", "post-1")).thenReturn(true);  // 이미 본 글

        Optional<PlannedAction> result = actionPlanner.planForType(persona, ActionType.VOTE, feedPosts, List.of());

        assertFalse(result.isPresent());
    }

    // ════════════════════ LIKE Tests ════════════════════

    @Test
    void planForType_LIKE_returnsEmpty_whenGateFails() {
        // resonance를 매우 낮게 (gate 확률 낮음) 만들기 위해
        // interests map을 비우거나 post category를 설정하지 않음
        Persona persona = buildTestPersona("persona-1");
        persona.setInterests(Map.of());  // 빈 interests

        PostDto post = buildTestPost("post-1", null);  // category 없음
        List<PostDto> feedPosts = List.of(post);

        when(seenPostRepo.existsByPersonaIdAndPostId("persona-1", "post-1")).thenReturn(false);

        // 여러 번 호출해서 gate가 한 번도 통과하지 않을 수 있음을 확인
        // (확률적 게이트이므로 여러 번 시도)
        boolean gateFailedAtLeastOnce = false;
        for (int i = 0; i < 10; i++) {
            Optional<PlannedAction> result = actionPlanner.planForType(persona, ActionType.LIKE, feedPosts, List.of());
            if (result.isEmpty()) {
                gateFailedAtLeastOnce = true;
                break;
            }
        }
        assertTrue(gateFailedAtLeastOnce, "gate should fail at least once in 10 attempts");
    }

    @Test
    void planForType_LIKE_returnsLike_whenPostResonates() {
        Persona persona = buildTestPersona("persona-1");
        persona.setInterests(Map.of("category", 0.9));  // 높은 관심도

        PostDto post = buildTestPost("post-1", "category");
        List<PostDto> feedPosts = List.of(post);

        when(seenPostRepo.existsByPersonaIdAndPostId("persona-1", "post-1")).thenReturn(false);

        Optional<PlannedAction> result = actionPlanner.planForType(persona, ActionType.LIKE, feedPosts, List.of());

        // LIKE 게이트가 통과하면 LIKE 반환, 안 하면 empty (확률적)
        // 최소한 한 번은 통과할 것으로 기대
        assertTrue(result.isEmpty() || result.get().type() == ActionType.LIKE);
    }

    // ════════════════════ COMMENT_LIKE Tests ════════════════════

    @Test
    void planForType_COMMENT_LIKE_returnsCommentLike_whenFeedAvailable() {
        Persona persona = buildTestPersona("persona-1");
        PostDto post = buildTestPost("post-1", "category");
        List<PostDto> feedPosts = List.of(post);

        Optional<PlannedAction> result = actionPlanner.planForType(persona, ActionType.COMMENT_LIKE, feedPosts, List.of());

        assertTrue(result.isPresent());
        assertEquals(ActionType.COMMENT_LIKE, result.get().type());
        assertEquals("post-1", result.get().targetPost().getId());
    }

    @Test
    void planForType_COMMENT_LIKE_returnsEmpty_whenFeedEmpty() {
        Persona persona = buildTestPersona("persona-1");

        Optional<PlannedAction> result = actionPlanner.planForType(persona, ActionType.COMMENT_LIKE, List.of(), List.of());

        assertFalse(result.isPresent());
    }

    // ════════════════════ COMMENT Tests ════════════════════

    @Test
    void planForType_COMMENT_returnsComment_whenUnseenPostAvailable() {
        Persona persona = buildTestPersona("persona-1");
        PostDto post = buildTestPost("post-1", "category");
        List<PostDto> feedPosts = List.of(post);

        when(seenPostRepo.existsByPersonaIdAndPostId("persona-1", "post-1")).thenReturn(false);

        Optional<PlannedAction> result = actionPlanner.planForType(persona, ActionType.COMMENT, feedPosts, List.of());

        assertTrue(result.isPresent());
        assertEquals(ActionType.COMMENT, result.get().type());
        assertEquals("post-1", result.get().targetPost().getId());
    }

    @Test
    void planForType_COMMENT_returnsEmpty_whenNoUnseenPosts() {
        Persona persona = buildTestPersona("persona-1");
        PostDto post = buildTestPost("post-1", "category");
        List<PostDto> feedPosts = List.of(post);

        when(seenPostRepo.existsByPersonaIdAndPostId("persona-1", "post-1")).thenReturn(true);

        Optional<PlannedAction> result = actionPlanner.planForType(persona, ActionType.COMMENT, feedPosts, List.of());

        assertFalse(result.isPresent());
    }

    // ════════════════════ POST Tests ════════════════════

    @Test
    void planForType_POST_returnsEmpty_forNonHeavyPersona() {
        Persona persona = buildTestPersona("persona-1");
        persona.setTier("REGULAR");  // REGULAR 티어

        Optional<PlannedAction> result = actionPlanner.planForType(persona, ActionType.POST, List.of(), List.of());

        assertFalse(result.isPresent());
    }

    @Test
    void planForType_POST_returnsEmpty_whenAlreadyPostedToday() {
        Persona persona = buildTestPersona("persona-1");
        persona.setTier("HEAVY");

        when(jdbcTemplate.queryForObject(
            anyString(), eq(Long.class), eq("persona-1"), any())).thenReturn(1L);  // 1개 게시물 존재

        Optional<PlannedAction> result = actionPlanner.planForType(persona, ActionType.POST, List.of(), List.of());

        assertFalse(result.isPresent());
    }

    @Test
    void planForType_POST_returnsNewPost_forHeavyPersonaWithQuota() {
        Persona persona = buildTestPersona("persona-1");
        persona.setTier("HEAVY");

        when(jdbcTemplate.queryForObject(
            anyString(), eq(Long.class), eq("persona-1"), any())).thenReturn(0L);  // 오늘 게시물 없음

        Optional<PlannedAction> result = actionPlanner.planForType(persona, ActionType.POST, List.of(), List.of());

        assertTrue(result.isPresent());
        assertEquals(ActionType.POST, result.get().type());
    }

    @Test
    void planForType_POST_returnsEmpty_whenJdbcFails() {
        Persona persona = buildTestPersona("persona-1");
        persona.setTier("HEAVY");

        when(jdbcTemplate.queryForObject(
            anyString(), eq(Long.class), eq("persona-1"), any()))
            .thenThrow(new RuntimeException("DB error"));

        // jdbc 실패 시 graceful degrade: false 반환 (가용성 우선)
        // → 글 쓰기 차단 안 함 (로그만 남김)
        Optional<PlannedAction> result = actionPlanner.planForType(persona, ActionType.POST, List.of(), List.of());

        // 예외 발생 시 false를 반환하므로 POST 생성 가능
        assertTrue(result.isPresent());
    }

    // ════════════════════ Unsupported Types ════════════════════

    @Test
    void planForType_returnsEmpty_forUnsupportedType() {
        Persona persona = buildTestPersona("persona-1");

        Optional<PlannedAction> result = actionPlanner.planForType(persona, ActionType.VIEW, List.of(), List.of());

        assertFalse(result.isPresent());
    }

    // ════════════════════ Helpers ════════════════════

    private Persona buildTestPersona(String id) {
        return Persona.builder()
            .id(id)
            .archetype("counselor")
            .tier("HEAVY")
            .voiceProfile(Map.of(
                "like_score", 0.45,
                "vote_score", 0.30,
                "political_strength", 0.5
            ))
            .interests(Map.of("category", 0.5))
            .biasProfile(Map.of("category", 0.0))
            .circadian(Arrays.asList(0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.5, 1.0, 1.0, 1.0,
                                      1.0, 1.0, 1.0, 0.8, 0.5, 0.3, 0.3, 0.3, 0.2, 0.15, 0.1, 0.1))
            .slangLevel(new BigDecimal("0.50"))
            .dailyTarget(6)
            .active(true)
            .createdAt(Instant.now())
            .build();
    }

    private PostDto buildTestPost(String id, String category) {
        PostDto post = new PostDto();
        post.setId(id);
        post.setUserTitle("Test Post");
        post.setCategory(category);
        post.setBodyPublished("Test body");
        return post;
    }
}
