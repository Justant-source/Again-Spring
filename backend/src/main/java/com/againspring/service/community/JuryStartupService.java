package com.againspring.service.community;

import com.againspring.domain.community.Post;
import com.againspring.domain.community.VoteOption;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.VoteOptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 서버 시작 시 배심원 생성이 중단된 포스트를 감지하고 재시작.
 * 컨테이너 재시작으로 @Async 스레드가 중단된 경우를 복구.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JuryStartupService implements ApplicationRunner {

    private final PostRepository postRepository;
    private final VoteOptionRepository voteOptionRepository;
    private final JuryService juryService;

    @Override
    public void run(ApplicationArguments args) {
        List<String> postIds = postRepository.findPostIdsNeedingJury();
        if (postIds.isEmpty()) return;

        log.info("[startup] Found {} post(s) with incomplete jury — triggering recovery", postIds.size());

        for (String postId : postIds) {
            try {
                Post post = postRepository.findById(postId).orElse(null);
                if (post == null) continue;

                // 사연 내용이 너무 짧거나 e2e 테스트 더미 포스트면 스킵
                String body = post.getBodyPublished();
                if (body == null || body.trim().length() < 50
                        || body.contains("e2e 테스트") || body.contains("자동 생성된 사연")) {
                    log.info("[startup] Skipping post {} — not a real story", postId);
                    continue;
                }

                List<VoteOption> options = voteOptionRepository.findByPostIdOrderByOrderIdx(postId);
                if (options.isEmpty()) continue;

                int target = post.getJurorCount() != null ? post.getJurorCount() : 0;
                log.info("[startup] Recovering jury for post {} (target={})", postId, target);
                juryService.generateJuryAsync(post, options, target);

            } catch (Exception e) {
                log.warn("[startup] Skipping jury recovery for post {}: {}", postId, e.getMessage());
            }
        }
    }
}
