package com.againspring.service.community;

import com.againspring.domain.community.Post;
import com.againspring.domain.community.VoteOption;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.VoteOptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 파트너 답변 제출 후 비동기 후처리 — tonalization + jury 재생성
 * HTTP 요청 경로에서 분리해 LLM 호출로 인한 타임아웃 방지.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerProcessingService {

    private final PostRepository postRepository;
    private final VoteOptionRepository voteOptionRepository;
    private final TonalizationService tonalizationService;
    private final JuryService juryService;

    @Async("taskExecutor")
    @Transactional
    public void processAsync(String postId, String bodyRaw, String userTitle, int jurorCount) {
        try {
            Post post = postRepository.findById(postId).orElse(null);
            if (post == null) return;

            TonalizationService.TonalizationResult tone = tonalizationService.normalize(userTitle, bodyRaw);
            if (tone.success()) {
                // UPDATE만 — save()/merge는 동시 DELETE 이후 동일 PK로 행을 되살릴 수 있음
                String normalizedTitle =
                        (userTitle != null && !userTitle.isBlank()) ? tone.titleNormalized() : null;
                int updated = postRepository.updatePartnerTonalization(
                        postId, tone.bodyNormalized(), normalizedTitle);
                if (updated == 0) {
                    log.info("Async tonalization skip (post gone): {}", postId);
                    return;
                }
                log.info("Async tonalization applied for post {}", postId);
            }

            if (jurorCount > 0) {
                if (!postRepository.existsById(postId)) return;
                List<VoteOption> options = voteOptionRepository.findByPostIdOrderByOrderIdx(postId);
                if (!options.isEmpty()) {
                    juryService.generateJuryAsync(post, options, jurorCount);
                    log.info("Async jury re-triggered for post {}", postId);
                }
            }
        } catch (Exception e) {
            log.warn("Async answer processing failed for post {}: {}", postId, e.getMessage());
        }
    }
}
