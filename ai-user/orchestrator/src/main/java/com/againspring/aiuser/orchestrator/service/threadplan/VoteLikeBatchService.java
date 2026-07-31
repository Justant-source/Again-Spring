package com.againspring.aiuser.orchestrator.service.threadplan;

import com.againspring.aiuser.orchestrator.client.BackendBotClient;
import com.againspring.aiuser.orchestrator.client.dto.PostDto;
import com.againspring.aiuser.orchestrator.client.dto.PostFeedPage;
import com.againspring.aiuser.orchestrator.config.OrchestratorProperties;
import com.againspring.aiuser.orchestrator.domain.AiUserGenerationConfig;
import com.againspring.aiuser.orchestrator.domain.Persona;
import com.againspring.aiuser.orchestrator.engine.PlannedAction;
import com.againspring.aiuser.orchestrator.repository.AiUserGenerationConfigRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaActionLogRepository;
import com.againspring.aiuser.orchestrator.repository.PersonaRepository;
import com.againspring.aiuser.orchestrator.task.ActionExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * VOTE/LIKE 배치 서비스 — PLAN 모드 내 VOTE/LIKE 액션 생성 및 즉시 실행.
 *
 * 동작:
 * 1. config 확인 (provider_vote_like가 OFF가 아닌지)
 * 2. targetVotes/targetLikes 조회
 * 3. 오늘 VOTE/LIKE 액션 실적 조회
 * 4. 남은 쿼터 계산
 * 5. 게시글 피드 조회
 * 6. 활성 페르소나에서 무작위로 선정
 * 7. 각 페르소나별로 VOTE/LIKE 액션 실행 (ActionExecutor 직접 호출)
 *
 * VOTE와 LIKE를 동시에 처리하여 목표 달성률을 최적화한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoteLikeBatchService {

    private final BackendBotClient backendBot;
    private final PersonaRepository personaRepo;
    private final PersonaActionLogRepository actionLogRepo;
    private final ActionExecutor actionExecutor;
    private final AiUserGenerationConfigRepository configRepository;
    private final OrchestratorProperties props;

    public void run() {
        // 1. Config 확인
        AiUserGenerationConfig config = configRepository.findById(1).orElse(null);
        if (!props.isEnabled() || !props.getThreadPlan().isEnabled() || config == null
                || "OFF".equalsIgnoreCase(config.getProviderVoteLike())) {
            log.debug("VoteLikeBatchService.run() skipped: config off or disabled");
            return;
        }

        if (config.isScheduleExecutionPaused() || config.isAiUserKillSwitch()) {
            log.debug("VoteLikeBatchService.run() skipped: paused or kill-switch");
            return;
        }

        int targetVotes = config.getTargetVotes();
        int targetLikes = config.getTargetLikes();

        if (targetVotes <= 0 && targetLikes <= 0) {
            log.debug("VoteLikeBatchService.run() skipped: no targets (votes={}, likes={})", targetVotes, targetLikes);
            return;
        }

        // 2. 오늘 실적 조회
        Instant startOfToday = LocalDate.now(ZoneId.of("Asia/Seoul"))
                .atStartOfDay(ZoneId.of("Asia/Seoul"))
                .toInstant();
        long votesCount = actionLogRepo.countByActionTypeAndCreatedAtAfter("VOTE", startOfToday);
        long likesCount = actionLogRepo.countByActionTypeAndCreatedAtAfter("LIKE", startOfToday);

        // 3. 남은 쿼터
        int votesRemaining = Math.max(0, targetVotes - (int) votesCount);
        int likesRemaining = Math.max(0, targetLikes - (int) likesCount);

        if (votesRemaining <= 0 && likesRemaining <= 0) {
            log.info("VoteLikeBatchService: daily caps reached (votes={}/{}, likes={}/{})",
                    votesCount, targetVotes, likesCount, targetLikes);
            return;
        }

        // 4. 게시글 피드 조회
        List<PostDto> feedPosts = new ArrayList<>();
        for (int p = 0; p < 5; p++) {
            List<PostDto> page = backendBot.getFeed(p, 20)
                    .map(PostFeedPage::getContent)
                    .orElse(Collections.emptyList());
            feedPosts.addAll(page);
            if (page.size() < 20) break;
        }

        if (feedPosts.isEmpty()) {
            log.debug("VoteLikeBatchService: no feed posts available");
            return;
        }

        // 5. 활성 페르소나 조회
        List<Persona> activePersonas = personaRepo.findByActiveTrue();
        if (activePersonas.isEmpty()) {
            log.debug("VoteLikeBatchService: no active personas");
            return;
        }

        // 6. 실행
        AtomicInteger votesExecuted = new AtomicInteger(0);
        AtomicInteger likesExecuted = new AtomicInteger(0);

        if (votesRemaining > 0 && hasVotablePost(feedPosts)) {
            executeBatch("VOTE", feedPosts, activePersonas, votesRemaining, votesExecuted, true);
        }

        if (likesRemaining > 0) {
            executeBatch("LIKE", feedPosts, activePersonas, likesRemaining, likesExecuted, false);
        }

        log.info("VoteLikeBatchService: executed votes={} likes={} (targets: votes={}, likes={})",
                votesExecuted.get(), likesExecuted.get(), votesRemaining, likesRemaining);
    }

    private boolean hasVotablePost(List<PostDto> posts) {
        return posts.stream().anyMatch(p -> p.getVoteOptions() != null && !p.getVoteOptions().isEmpty());
    }

    private void executeBatch(String actionType, List<PostDto> feedPosts, List<Persona> activePersonas,
                              int remaining, AtomicInteger executed, boolean isVote) {
        // 피드와 페르소나를 섞어서 다양한 조합 시도
        Collections.shuffle(feedPosts);
        Collections.shuffle(activePersonas);

        int attempts = 0;
        int maxAttempts = remaining * 3; // 시도 한계

        for (int i = 0; i < maxAttempts && executed.get() < remaining; i++) {
            PostDto post = feedPosts.get(i % feedPosts.size());
            Persona persona = activePersonas.get(i % activePersonas.size());

            try {
                if (isVote) {
                    // VOTE: 투표 옵션 확인
                    if (post.getVoteOptions() == null || post.getVoteOptions().isEmpty()) {
                        continue;
                    }
                    // 첫 번째 옵션 선택 (또는 무작위 선택)
                    Long optionId = post.getVoteOptions().get(0).getId();
                    PlannedAction action = PlannedAction.vote(post, optionId);
                    actionExecutor.execute(persona, action);
                } else {
                    // LIKE
                    PlannedAction action = PlannedAction.like(post);
                    actionExecutor.execute(persona, action);
                }
                executed.incrementAndGet();
            } catch (Exception e) {
                log.debug("VoteLikeBatchService: {} failed for persona={} post={}: {}",
                        actionType, persona.getId(), post.getId(), e.getMessage());
                // Continue trying with next combination
            }
            attempts++;
        }

        if (executed.get() == 0 && attempts > 0) {
            log.warn("VoteLikeBatchService: {} batch failed after {} attempts", actionType, attempts);
        }
    }
}
