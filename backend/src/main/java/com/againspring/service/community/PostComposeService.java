package com.againspring.service.community;

import com.againspring.domain.community.Post;
import com.againspring.domain.community.VoteOption;
import com.againspring.domain.enums.PostCategory;
import com.againspring.domain.enums.PostStatus;
import com.againspring.domain.enums.PostVisibility;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.VoteOptionRepository;
import com.againspring.safety.KeywordGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PostComposeService - 커뮤니티 사연 등록 서비스
 * 중립화 없이 원문 그대로 즉시 VOTING 상태로 등록.
 * AI 기능은 배심원 평가만 존재 (JuryService, 비동기).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PostComposeService {

    private final PostRepository postRepository;
    private final VoteOptionRepository voteOptionRepository;
    private final KeywordGuard keywordGuard;

    /**
     * 재구성 출처 스냅샷 — 재구성 모드 생성 시만 전달되며 posts 테이블에 저장.
     * null = 일반(창작) 생성.
     */
    public record SourceSnapshot(
        Long exampleId,
        String community,
        String url,
        String originalTitle,
        String originalBody
    ) {}

    /**
     * 사연을 원문 그대로 즉시 등록.
     * 위기 감지 시 IllegalArgumentException("CRISIS_DETECTED") 발생.
     *
     * @param authorId   작성자 ID
     * @param userTitle  사용자가 입력한 제목
     * @param bodyRaw    원본 사연 텍스트
     * @param category   관계 카테고리
     * @param visibility 공개/비공개 설정
     * @param jurorCount AI 배심원 인원 (0-9)
     * @param sessionId  관련 세션 ID (nullable)
     * @param source     크롤 원본 스냅샷 — 재구성 모드 시만 비-null
     * @return 등록된 Post 객체 (status=VOTING)
     */
    public Post composeAndPublish(String authorId, String userTitle, String bodyRaw,
                                  PostCategory category, String visibility,
                                  int jurorCount, String sessionId,
                                  SourceSnapshot source) {
        log.info("Publishing post for author {} category {}", authorId, category);

        // 위기 감지 (이중방어 — FE에서도 감지)
        var scanResult = keywordGuard.scanUserInput(bodyRaw, authorId);
        if (scanResult.isCrisis() || scanResult.isBlocked()) {
            throw new IllegalArgumentException("CRISIS_DETECTED");
        }

        String postId = "post_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        Post.PostBuilder postBuilder = Post.builder()
                .id(postId)
                .authorId(authorId)
                .sessionId(sessionId)
                .bodyRaw(bodyRaw)
                .userTitle(userTitle)
                // 원문 그대로 표시 (중립화 없음)
                .title(userTitle)
                .bodyPublished(bodyRaw)
                .jurorCount(jurorCount)
                .category(category)
                .visibility(PostVisibility.valueOf(visibility.toUpperCase()))
                .status(PostStatus.VOTING)
                .neutralizationPassed(true)   // 항상 통과로 간주 (컬럼 잔존)
                .voteCloseAt(Instant.now().plusSeconds(7L * 24 * 3600))
                .createdAt(Instant.now())
                .updatedAt(Instant.now());
        // 재구성 출처 스냅샷 (재구성 모드 시만 비-null)
        if (source != null) {
            postBuilder
                .sourceExampleId(source.exampleId())
                .sourceCommunity(source.community())
                .sourceUrl(source.url())
                .sourceOriginalTitle(source.originalTitle())
                .sourceOriginalBody(source.originalBody());
        }
        Post post = postBuilder.build();

        postRepository.save(post);
        log.info("Post published immediately: {}", postId);

        // VoteOption 저장 — 2개 고정: "작성자"(0) vs "상대방"(1)
        List<VoteOption> options = new ArrayList<>();
        options.add(VoteOption.builder().postId(postId).label("작성자").orderIdx(0).build());
        options.add(VoteOption.builder().postId(postId).label("상대방").orderIdx(1).build());
        voteOptionRepository.saveAll(options);
        log.info("VoteOptions saved for post {}", postId);

        return post;
    }
}
