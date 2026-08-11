package com.againspring.service.community;

import com.againspring.domain.community.Post;
import com.againspring.repository.community.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파트너 답변 제출 후 비동기 후처리 — tonalization
 * HTTP 요청 경로에서 분리해 LLM 호출로 인한 타임아웃 방지.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnswerProcessingService {

    private final PostRepository postRepository;
    private final TonalizationService tonalizationService;

    @Async("taskExecutor")
    @Transactional
    public void processAsync(String postId, String bodyRaw, String userTitle) {
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
        } catch (Exception e) {
            log.warn("Async answer processing failed for post {}: {}", postId, e.getMessage());
        }
    }
}
