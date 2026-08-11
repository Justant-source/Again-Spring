package com.againspring.service.community;

import com.againspring.common.exception.BusinessException;
import com.againspring.domain.community.Post;
import com.againspring.domain.community.Vote;
import com.againspring.domain.community.VoteOption;
import com.againspring.domain.enums.PostStatus;
import com.againspring.domain.enums.PostVisibility;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.VoteOptionRepository;
import com.againspring.repository.community.VoteRepository;
import com.againspring.service.notification.event.NewVoteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * VoteService - 투표 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class VoteService {

    private final VoteRepository voteRepository;
    private final PostRepository postRepository;
    private final VoteOptionRepository voteOptionRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 투표 수행 및 현재 투표 결과 반환
     * 이미 투표했으면 optionId 업데이트 (투표 변경 허용)
     *
     * @param postId 포스트 ID
     * @param optionId 선택지 ID
     * @param userId 투표자 사용자 ID
     * @return {optionId: count} 맵
     * @throws BusinessException POST_NOT_FOUND 또는 OPTION_NOT_FOUND
     */
    @Transactional
    public Map<Long, Long> castVoteAndGetResult(String postId, Long optionId, String userId) {
        // 포스트 존재 확인
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("POST_NOT_FOUND", "Post not found: " + postId, 404));

        assertEmpathyVoteAllowed(post);

        // 선택지 존재 확인
        VoteOption option = voteOptionRepository.findById(optionId)
                .orElseThrow(() -> new BusinessException("OPTION_NOT_FOUND", "Vote option not found: " + optionId, 404));

        if (!option.getPostId().equals(postId)) {
            throw new BusinessException("OPTION_MISMATCH", "Option does not belong to this post", 400);
        }

        // 이미 투표했는지 확인
        Optional<Vote> existingVote = voteRepository.findByPostIdAndVoterUserId(postId, userId);

        if (existingVote.isPresent()) {
            // 재투표 금지 — 이미 투표한 경우 409 반환
            throw new com.againspring.common.exception.BusinessException(
                "ALREADY_VOTED", "이미 투표하셨습니다", 409);
        } else {
            // 새 투표 생성
            Vote vote = Vote.builder()
                    .postId(postId)
                    .voterUserId(userId)
                    .optionId(optionId)
                    .createdAt(Instant.now())
                    .build();
            voteRepository.save(vote);
            log.info("Vote cast for post {} by user {}: option {}", postId, userId, optionId);

            // C3 알림: 사연 작성자에게 새 투표 알림 발행
            eventPublisher.publishEvent(new NewVoteEvent(
                this,
                post.getAuthorId(),
                postId,
                userId + "님이 투표했어요"
            ));
        }

        return getVoteResult(postId);
    }

    /**
     * 투표 결과 조회 (각 선택지별 투표 수)
     *
     * @param postId 포스트 ID
     * @return {optionId: count} 맵
     * @throws BusinessException POST_NOT_FOUND
     */
    public Map<Long, Long> getVoteResult(String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("POST_NOT_FOUND", "Post not found: " + postId, 404));

        List<VoteOption> options = voteOptionRepository.findByPostIdOrderByOrderIdx(postId);

        Map<Long, Long> result = new TreeMap<>();
        for (VoteOption opt : options) {
            long count = voteRepository.countByPostIdAndOptionId(postId, opt.getId());
            result.put(opt.getId(), count);
        }

        return result;
    }

    /**
     * 투표 결과 조회 (각 선택지별 투표 수, 사람/AI 분리)
     *
     * @param postId 포스트 ID
     * @return {optionId: VoteCountBreakdown(humanCount, aiCount)} 맵
     * @throws BusinessException POST_NOT_FOUND
     */
    public Map<Long, VoteCountBreakdown> getVoteResultWithBreakdown(String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("POST_NOT_FOUND", "Post not found: " + postId, 404));

        List<VoteOption> options = voteOptionRepository.findByPostIdOrderByOrderIdx(postId);

        Map<Long, VoteCountBreakdown> result = new TreeMap<>();
        for (VoteOption opt : options) {
            result.put(opt.getId(), voteRepository.countByPostIdAndOptionIdWithBreakdown(postId, opt.getId()));
        }

        return result;
    }

    /**
     * 가중치를 적용한 공감 비율 계산
     *
     * 공식:
     * humanVoteCount = 전체 옵션 통틀어 사람표 총합 (해당 게시글)
     * weight_ai = 1.0 / (1.0 + humanVoteCount)   // humanVoteCount=0이면 1, 늘어날수록 0에 수렴
     * weight_human = 1.0 (고정)
     *
     * weighted_count(option) = humanCount(option) * weight_human + aiCount(option) * weight_ai
     * weighted_total = humanVoteCount * weight_human + aiVoteCount_total * weight_ai
     * percentage(option) = weighted_total > 0 ? weighted_count(option) / weighted_total * 100.0 : 0.0
     *
     * @param optionCountBreakdown 옵션별 사람/AI 카운트
     * @return 옵션별 가중치 적용 비율 (%)
     */
    public Map<Long, Double> calculateWeightedPercentages(Map<Long, VoteCountBreakdown> optionCountBreakdown) {
        // 전체 인간 투표 수 계산
        long totalHumanVotes = optionCountBreakdown.values().stream()
                .mapToLong(bd -> bd.humanCount)
                .sum();

        // 전체 AI 투표 수 계산
        long totalAiVotes = optionCountBreakdown.values().stream()
                .mapToLong(bd -> bd.aiCount)
                .sum();

        // 가중치 계산
        double weightAi = 1.0 / (1.0 + totalHumanVotes);

        // 가중치 적용 총 투표 수
        double weightedTotal = (totalHumanVotes * 1.0) + (totalAiVotes * weightAi);

        // 옵션별 가중치 적용 비율 계산
        Map<Long, Double> result = new TreeMap<>();
        for (Map.Entry<Long, VoteCountBreakdown> entry : optionCountBreakdown.entrySet()) {
            Long optionId = entry.getKey();
            VoteCountBreakdown breakdown = entry.getValue();

            double weightedCount = (breakdown.humanCount * 1.0) + (breakdown.aiCount * weightAi);
            double percentage = weightedTotal > 0 ? (weightedCount / weightedTotal) * 100.0 : 0.0;

            result.put(optionId, percentage);
        }

        return result;
    }

    /**
     * 현재 사용자의 투표 선택지 ID 조회
     *
     * @param postId 포스트 ID
     * @param userId 사용자 ID
     * @return Optional<optionId>
     */
    public Optional<Long> getMyVote(String postId, String userId) {
        return voteRepository.findByPostIdAndVoterUserId(postId, userId)
                .map(Vote::getOptionId);
    }

    /**
     * 투표 취소 — 본인 투표를 삭제하고 갱신된 결과 반환
     * 투표 기록이 없으면 NO_VOTE_TO_CANCEL(404) 반환
     */
    @Transactional
    public Map<Long, Long> cancelVoteAndGetResult(String postId, String userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("POST_NOT_FOUND", "Post not found: " + postId, 404));
        assertEmpathyVoteAllowed(post);
        if (!voteRepository.existsByPostIdAndVoterUserId(postId, userId)) {
            throw new BusinessException("NO_VOTE_TO_CANCEL", "취소할 투표가 없습니다", 404);
        }
        voteRepository.deleteByPostIdAndVoterUserId(postId, userId);
        log.info("Vote cancelled for post {} by user {}", postId, userId);
        return getVoteResult(postId);
    }

    /**
     * 공감 투표 가능 조건: PUBLIC + 미삭제. CLOSED는 시한부 투표 레거시로 VOTING과 동일 취급.
     */
    private void assertEmpathyVoteAllowed(Post post) {
        if (post.getDeletedAt() != null) {
            throw new BusinessException("POST_DELETED", "삭제된 게시글에는 투표할 수 없어요.", 410);
        }
        if (post.getVisibility() != PostVisibility.PUBLIC) {
            throw new BusinessException("POST_NOT_PUBLIC", "공개된 게시글에만 투표할 수 있어요.", 403);
        }
        PostStatus status = post.getStatus();
        if (status == PostStatus.BLOCKED || status == PostStatus.DRAFT) {
            throw new BusinessException("VOTE_NOT_ALLOWED", "이 게시글에는 투표할 수 없어요.", 403);
        }
        // VOTING · CLOSED(레거시) 모두 허용 — voteCloseAt 무시
    }
}
