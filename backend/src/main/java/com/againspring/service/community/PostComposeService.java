package com.againspring.service.community;

import com.againspring.domain.community.Post;
import com.againspring.domain.community.VoteOption;
import com.againspring.domain.enums.PostCategory;
import com.againspring.domain.enums.PostStatus;
import com.againspring.domain.enums.PostVisibility;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.community.VoteOptionRepository;
import com.againspring.safety.CrisisDetectedEvent;
import com.againspring.safety.KeywordGuard;
import com.againspring.service.ai.AiUserOutboxWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * PostComposeService - 커뮤니티 사연 등록 서비스
 * 중립화 없이 원문 그대로 즉시 VOTING 상태로 등록.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PostComposeService {

    private final PostRepository postRepository;
    private final VoteOptionRepository voteOptionRepository;
    private final KeywordGuard keywordGuard;
    private final AiUserOutboxWriter aiUserOutboxWriter;
    private final PostSearchNgramIndexer postSearchNgramIndexer;
    private final ApplicationEventPublisher eventPublisher;

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
     * 광장형: 본문 위기/LEVEL1 키워드는 게시 차단하지 않는다(감지·관제만).
     *
     * @param authorId   작성자 ID
     * @param userTitle  사용자가 입력한 제목
     * @param bodyRaw    원본 사연 텍스트
     * @param category   관계 카테고리
     * @param visibility 공개/비공개 설정
     * @param sessionId  관련 세션 ID (nullable)
     * @param source     크롤 원본 스냅샷 — 재구성 모드 시만 비-null
     * @param captureSplitAfterLines X/IG 캡쳐 컷(1-based), nullable
     * @return 등록된 Post 객체 (status=VOTING)
     */
    public Post composeAndPublish(String authorId, String userTitle, String bodyRaw,
                                  PostCategory category, String visibility,
                                  String sessionId,
                                  SourceSnapshot source) {
        return composeAndPublish(authorId, userTitle, bodyRaw, category, visibility,
                sessionId, source, (java.util.List<Integer>) null, null, null);
    }

    /** @deprecated prefer list overload */
    @Deprecated
    public Post composeAndPublish(String authorId, String userTitle, String bodyRaw,
                                  PostCategory category, String visibility,
                                  String sessionId,
                                  SourceSnapshot source,
                                  Integer captureSplitAfterLine) {
        return composeAndPublish(authorId, userTitle, bodyRaw, category, visibility,
                sessionId, source,
                captureSplitAfterLine == null ? null : java.util.List.of(captureSplitAfterLine),
                null, null);
    }

    /** @deprecated prefer list overload */
    @Deprecated
    public Post composeAndPublish(String authorId, String userTitle, String bodyRaw,
                                  PostCategory category, String visibility,
                                  String sessionId,
                                  SourceSnapshot source,
                                  Integer captureSplitAfterLine,
                                  String promoTitle) {
        return composeAndPublish(authorId, userTitle, bodyRaw, category, visibility,
                sessionId, source,
                captureSplitAfterLine == null ? null : java.util.List.of(captureSplitAfterLine),
                promoTitle, null);
    }

    public Post composeAndPublish(String authorId, String userTitle, String bodyRaw,
                                  PostCategory category, String visibility,
                                  String sessionId,
                                  SourceSnapshot source,
                                  java.util.List<Integer> captureSplitAfterLines,
                                  String promoTitle) {
        return composeAndPublish(authorId, userTitle, bodyRaw, category, visibility,
                sessionId, source, captureSplitAfterLines, promoTitle, null);
    }

    public Post composeAndPublish(String authorId, String userTitle, String bodyRaw,
                                  PostCategory category, String visibility,
                                  String sessionId,
                                  SourceSnapshot source,
                                  java.util.List<Integer> captureSplitAfterLines,
                                  String promoTitle,
                                  String metaphorId) {
        return composeAndPublish(authorId, userTitle, bodyRaw, category, visibility,
                sessionId, source, captureSplitAfterLines, promoTitle, metaphorId, null);
    }

    public Post composeAndPublish(String authorId, String userTitle, String bodyRaw,
                                  PostCategory category, String visibility,
                                  String sessionId,
                                  SourceSnapshot source,
                                  java.util.List<Integer> captureSplitAfterLines,
                                  String promoTitle,
                                  String metaphorId,
                                  java.util.List<String> metaphorIds) {
        log.info("Publishing post for author {} category {}", authorId, category);

        // 광장형 정책(docs/frontend/ux/flows/08-crisis.md): 사연·댓글 입력은 차단하지 않는다.
        // KeywordGuard LEVEL1(피해자·소송 등)은 AI 출력 금지어이며 커뮤니티 본문 차단 사유가 아니다.
        // CRISIS 키워드만 관제 이벤트(감사 로그)로 남기고 게시는 계속한다.
        var scanResult = keywordGuard.scanUserInput(bodyRaw, authorId);
        if (scanResult.isCrisis()) {
            List<String> patterns = scanResult.getMatches().stream()
                    .map(m -> m.getPattern())
                    .collect(Collectors.toList());
            log.warn("Crisis keyword detected on post compose author={} patterns={} — publishing anyway (plaza policy)",
                    authorId, patterns);
            eventPublisher.publishEvent(new CrisisDetectedEvent(this, authorId, sessionId, patterns));
        }

        String postId = "post_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        String normalizedPromo = null;
        if (promoTitle != null && !promoTitle.isBlank()) {
            normalizedPromo = PromoTitleService.normalizeAgainstTitle(promoTitle, userTitle);
        }

        String normalizedMetaphor = null;
        if (metaphorId != null && !metaphorId.isBlank()) {
            normalizedMetaphor = metaphorId.trim().toLowerCase(java.util.Locale.ROOT);
            if (normalizedMetaphor.length() > 64) {
                normalizedMetaphor = normalizedMetaphor.substring(0, 64);
            }
        }

        // Process metaphorIds: trim/lowercase, dedupe, cap at 5
        java.util.List<String> normalizedMetaphorIds = null;
        if (metaphorIds != null && !metaphorIds.isEmpty()) {
            java.util.Set<String> seen = new java.util.LinkedHashSet<>();
            for (String id : metaphorIds) {
                if (id != null && !id.isBlank()) {
                    String normalized = id.trim().toLowerCase(java.util.Locale.ROOT);
                    if (normalized.length() > 64) {
                        normalized = normalized.substring(0, 64);
                    }
                    if (!seen.contains(normalized)) {
                        seen.add(normalized);
                        if (seen.size() >= 5) break;
                    }
                }
            }
            if (!seen.isEmpty()) {
                normalizedMetaphorIds = new ArrayList<>(seen);
            }
        }

        Integer legacyFirst = (captureSplitAfterLines != null && !captureSplitAfterLines.isEmpty())
                ? captureSplitAfterLines.get(0) : null;

        Post.PostBuilder postBuilder = Post.builder()
                .id(postId)
                .authorId(authorId)
                .sessionId(sessionId)
                .bodyRaw(bodyRaw)
                .userTitle(userTitle)
                // 원문 그대로 표시 (중립화 없음)
                .title(userTitle)
                .bodyPublished(bodyRaw)
                .category(category)
                .visibility(PostVisibility.valueOf(visibility.toUpperCase()))
                .status(PostStatus.VOTING)
                .neutralizationPassed(true)   // 항상 통과로 간주 (컬럼 잔존)
                .captureSplitAfterLine(legacyFirst)
                .captureSplitAfterLines(captureSplitAfterLines)
                .promoTitle(normalizedPromo)
                .metaphorId(normalizedMetaphor)
                .metaphorIds(normalizedMetaphorIds)
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
        postSearchNgramIndexer.reindex(post);
        log.info("Post published immediately: {}", postId);

        // VoteOption 저장 — 2개 고정: "작성자"(0) vs "상대방"(1)
        List<VoteOption> options = new ArrayList<>();
        options.add(VoteOption.builder().postId(postId).label("작성자").orderIdx(0).build());
        options.add(VoteOption.builder().postId(postId).label("상대방").orderIdx(1).build());
        voteOptionRepository.saveAll(options);
        log.info("VoteOptions saved for post {}", postId);

        // 비공개 글은 계획을 만들지 않으며, 혹시 남아 있는 계획이 있다면 downstream이 취소한다.
        // 같은 DB 트랜잭션에만 기록한다. 이 시점에는 외부 워커/LLM을 호출하지 않는다.
        if (post.getVisibility() == PostVisibility.PUBLIC) {
            aiUserOutboxWriter.postPublished(post);
        } else {
            aiUserOutboxWriter.postLifecycleChanged(post, "POST_PRIVATE", "INITIAL_PRIVATE_VISIBILITY");
        }

        return post;
    }
}
