package com.againspring.service.community;

import com.againspring.common.exception.BusinessException;
import com.againspring.domain.community.Post;
import com.againspring.domain.community.Vote;
import com.againspring.domain.community.VoteOption;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.VoteOptionRepository;
import com.againspring.repository.community.VoteRepository;
import com.againspring.service.notification.event.NewVoteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

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

        // 선택지 존재 확인
        VoteOption option = voteOptionRepository.findById(optionId)
                .orElseThrow(() -> new BusinessException("OPTION_NOT_FOUND", "Vote option not found: " + optionId, 404));

        if (!option.getPostId().equals(postId)) {
            throw new BusinessException("OPTION_MISMATCH", "Option does not belong to this post", 400);
        }

        // 이미 투표했는지 확인
        Optional<Vote> existingVote = voteRepository.findByPostIdAndVoterUserId(postId, userId);

        if (existingVote.isPresent()) {
            // 기존 투표 업데이트 (투표 변경 허용)
            Vote vote = existingVote.get();
            vote.setOptionId(optionId);
            voteRepository.save(vote);
            log.info("Vote updated for post {} by user {}: option {} -> {}", postId, userId,
                    existingVote.get().getOptionId(), optionId);
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
     * 배심원 투표 결과 조회 (포스트 작성자만)
     *
     * @param postId 포스트 ID
     * @param requestUserId 요청 사용자 ID
     * @return 배심원 목록
     * @throws BusinessException POST_NOT_FOUND 또는 ACCESS_DENIED
     */
    public List<Map<String, Object>> getJuryResult(String postId, String requestUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("POST_NOT_FOUND", "Post not found: " + postId, 404));

        // 작성자만 조회 가능
        if (!post.getAuthorId().equals(requestUserId)) {
            throw new BusinessException("ACCESS_DENIED", "Only author can view jury results", 403);
        }

        // TODO: JurorRepository 조회 및 결과 집계
        return new ArrayList<>();
    }
}
