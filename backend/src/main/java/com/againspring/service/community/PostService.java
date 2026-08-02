package com.againspring.service.community;

import com.againspring.common.exception.BusinessException;
import com.againspring.domain.community.Post;
import com.againspring.domain.enums.PostCategory;
import com.againspring.domain.enums.PostStatus;
import com.againspring.domain.enums.PostVisibility;
import com.againspring.repository.community.PostRepository;
import com.againspring.service.ai.AiUserOutboxWriter;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * PostService - 커뮤니티 포스트 관리 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PostService {

    private final PostRepository postRepository;
    private final AiUserOutboxWriter aiUserOutboxWriter;
    private final PostSearchNgramIndexer postSearchNgramIndexer;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 공개 포스트 목록 조회 (공개된 VOTING/CLOSED 상태만)
     *
     * @param category 카테고리 필터 (nullable)
     * @param sort 정렬 순서 ("latest" | "recommended", 기본값 "latest")
     * @param pageable 페이징 정보
     * @return 공개 포스트 페이지
     */
    public Page<Post> listPublicPosts(String category, String sort, Pageable pageable) {
        int size   = pageable.getPageSize();
        int offset = (int) pageable.getOffset();

        // 카테고리 파싱 (null 허용)
        PostCategory cat = null;
        if (category != null && !category.isEmpty()) {
            try {
                cat = PostCategory.valueOf(category.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid category: {}", category);
            }
        }

        if ("recommended".equalsIgnoreCase(sort)) {
            // 추천순: Hacker News 스타일 시간 감쇠 + 재부상 보너스
            List<Post> posts;
            long total;
            if (cat != null) {
                posts = postRepository.findRecommendedByCategory(cat.name(), size, offset);
                total = postRepository.countByVisibilityAndStatusAndCategoryAndDeletedAtIsNull(PostVisibility.PUBLIC, PostStatus.VOTING, cat);
            } else {
                posts = postRepository.findRecommended(size, offset);
                total = postRepository.countByVisibilityAndStatusAndDeletedAtIsNull(PostVisibility.PUBLIC, PostStatus.VOTING);
            }
            log.info("Listed {} public posts (category={}, sort=recommended, total={})", posts.size(), category, total);
            return new PageImpl<>(posts, pageable, total);
        }

        // 최신순 (기본)
        Page<Post> page;
        if (cat != null) {
            page = postRepository.findByVisibilityAndStatusAndCategoryAndDeletedAtIsNullOrderByCreatedAtDesc(
                    PostVisibility.PUBLIC, PostStatus.VOTING, cat, pageable);
        } else {
            page = postRepository.findByVisibilityAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                    PostVisibility.PUBLIC, PostStatus.VOTING, pageable);
        }
        log.info("Listed {} public posts (category={}, sort=latest, total={})", page.getNumberOfElements(), category, page.getTotalElements());
        return page;
    }

    /**
     * 작성자의 전체 포스트 목록
     *
     * @param userId 작성자 ID
     * @return 포스트 목록
     */
    public List<Post> listMyPosts(String userId) {
        List<Post> posts = postRepository.findByAuthorIdOrderByCreatedAtDesc(userId);
        log.info("Listed {} posts for author {}", posts.size(), userId);
        return posts;
    }

    public Optional<Post> findById(String postId) {
        return postRepository.findById(postId);
    }

    /**
     * 작성자/상대방 본문 변경은 계획형 AI-user 입장에서 새 revision이다.
     * 이미 게시된 댓글은 삭제하지 않고, downstream이 이전 revision의 미게시 항목만 취소한다.
     */
    public Post updateAuthorBody(Post post, String newBody) {
        post.setBodyRaw(newBody);
        post.setBodyPublished(newBody);
        post.advanceContentRevision();
        Post saved = postRepository.save(post);
        postSearchNgramIndexer.reindex(saved);
        aiUserOutboxWriter.postRevised(saved, "AUTHOR_BODY_UPDATED");
        return saved;
    }

    public Post updatePartnerBody(Post post, String newBody) {
        post.setPartnerBodyRaw(newBody);
        post.setPartnerBodyPublished(newBody);
        post.advanceContentRevision();
        Post saved = postRepository.save(post);
        // partner 본문은 검색 코퍼스 제외 (슬라이스 합의) — ngram 재색인 불필요
        aiUserOutboxWriter.postRevised(saved, "PARTNER_BODY_UPDATED");
        return saved;
    }

    /**
     * 제목/본문 키워드 검색 (V1 슬라이스 ①+②).
     * <p>
     * 매칭: {@code post_search_ngrams} 바이그램 AND (미색인 글은 LIKE 폴백).
     * Exact 티어: 제목 연속 포함 → 티어 안
     * {@code (2*votes + comments + 1) × max(0.05, 0.5^(age/14d))}.
     */
    @Transactional(readOnly = true)
    public Page<Post> searchPosts(String q, String category, Pageable pageable) {
        String normalized = PostSearchQuery.normalize(q);
        if (PostSearchQuery.isTooShort(normalized)) {
            return Page.empty(pageable);
        }
        List<String> tokens = PostSearchQuery.tokens(normalized);
        if (tokens.isEmpty()) {
            return Page.empty(pageable);
        }
        List<String> grams = PostSearchNgrams.extractForQuery(normalized);

        PostCategory cat = null;
        if (category != null && !category.isEmpty()) {
            try {
                cat = PostCategory.valueOf(category.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid category for search: {}", category);
            }
        }

        boolean useNgram = !grams.isEmpty();
        String where = useNgram
                ? buildNgramSearchWhere(grams.size(), tokens.size(), cat != null)
                : buildLikeSearchWhere(tokens.size(), cat != null);

        String orderBy = """
                ORDER BY
                  CASE WHEN p.title LIKE :exactTitle ESCAPE '!' THEN 0 ELSE 1 END ASC,
                  (2.0 * COALESCE(v.cnt, 0) + COALESCE(pc.cnt, 0) + 1.0)
                    * GREATEST(
                        0.05,
                        POWER(0.5, TIMESTAMPDIFF(SECOND, p.created_at, NOW()) / (14.0 * 86400.0))
                      )
                    DESC,
                  p.created_at DESC
                """;

        String fromJoins = """
                FROM posts p
                LEFT JOIN (
                  SELECT post_id, COUNT(*) cnt FROM votes GROUP BY post_id
                ) v ON v.post_id = p.id
                LEFT JOIN (
                  SELECT post_id, COUNT(*) cnt FROM post_comments
                  WHERE status = 'ACTIVE' AND deleted_at IS NULL
                  GROUP BY post_id
                ) pc ON pc.post_id = p.id
                """;

        Query countQuery = entityManager.createNativeQuery("SELECT COUNT(*) " + fromJoins + where);
        bindSearchParams(countQuery, tokens, grams, cat, useNgram);
        long total = ((Number) countQuery.getSingleResult()).longValue();
        if (total == 0) {
            return Page.empty(pageable);
        }

        Query dataQuery = entityManager.createNativeQuery(
                "SELECT p.* " + fromJoins + where + orderBy + " LIMIT :limit OFFSET :offset",
                Post.class);
        bindSearchParams(dataQuery, tokens, grams, cat, useNgram);
        dataQuery.setParameter("exactTitle", PostSearchQuery.containsPattern(normalized));
        dataQuery.setParameter("limit", pageable.getPageSize());
        dataQuery.setParameter("offset", (int) pageable.getOffset());

        @SuppressWarnings("unchecked")
        List<Post> posts = dataQuery.getResultList();
        log.info("Search {} → {}/{} hits (ngram={})",
                PostSearchQuery.describeForLog(normalized, category), posts.size(), total, useNgram);
        return new PageImpl<>(posts, pageable, total);
    }

    private static String buildLikeSearchWhere(int tokenCount, boolean hasCategory) {
        StringBuilder where = new StringBuilder("""
                WHERE p.visibility = 'PUBLIC'
                  AND p.status IN ('VOTING', 'CLOSED')
                  AND p.deleted_at IS NULL
                """);
        if (hasCategory) {
            where.append(" AND p.category = :category ");
        }
        for (int i = 0; i < tokenCount; i++) {
            where.append(" AND (p.title LIKE :tok").append(i)
                    .append(" ESCAPE '!' OR p.body_published LIKE :tok").append(i)
                    .append(" ESCAPE '!') ");
        }
        return where.toString();
    }

    /**
     * ngram AND + 미색인 글 LIKE 폴백.
     */
    private static String buildNgramSearchWhere(int gramCount, int tokenCount, boolean hasCategory) {
        StringBuilder where = new StringBuilder("""
                WHERE p.visibility = 'PUBLIC'
                  AND p.status IN ('VOTING', 'CLOSED')
                  AND p.deleted_at IS NULL
                """);
        if (hasCategory) {
            where.append(" AND p.category = :category ");
        }
        where.append(" AND ( ");
        where.append(" p.id IN ( ");
        where.append("   SELECT n.post_id FROM post_search_ngrams n ");
        where.append("   WHERE n.gram IN (");
        for (int i = 0; i < gramCount; i++) {
            if (i > 0) where.append(", ");
            where.append(":g").append(i);
        }
        where.append(") ");
        where.append("   GROUP BY n.post_id ");
        where.append("   HAVING COUNT(DISTINCT n.gram) >= :gramNeed ");
        where.append(" ) ");
        where.append(" OR ( ");
        where.append("   NOT EXISTS (SELECT 1 FROM post_search_ngrams nx WHERE nx.post_id = p.id) ");
        for (int i = 0; i < tokenCount; i++) {
            where.append(" AND (p.title LIKE :tok").append(i)
                    .append(" ESCAPE '!' OR p.body_published LIKE :tok").append(i)
                    .append(" ESCAPE '!') ");
        }
        where.append(" ) ");
        where.append(" ) ");
        return where.toString();
    }

    private static void bindSearchParams(
            Query query,
            List<String> tokens,
            List<String> grams,
            PostCategory cat,
            boolean useNgram) {
        for (int i = 0; i < tokens.size(); i++) {
            query.setParameter("tok" + i, PostSearchQuery.containsPattern(tokens.get(i)));
        }
        if (useNgram) {
            for (int i = 0; i < grams.size(); i++) {
                query.setParameter("g" + i, grams.get(i));
            }
            query.setParameter("gramNeed", grams.size());
        }
        if (cat != null) {
            query.setParameter("category", cat.name());
        }
    }

    /** 광장별 글 수 (다른 광장 패널용) */
    public java.util.Map<String, Long> getCategoryCounts() {
        java.util.Map<String, Long> counts = new java.util.LinkedHashMap<>();
        counts.put("", postRepository.countPublicAll());
        for (PostCategory cat : PostCategory.values()) {
            counts.put(cat.name(), postRepository.countPublicByCategory(cat));
        }
        return counts;
    }

    /**
     * 포스트 상세 조회
     * - 공개(PUBLIC): 누구나 조회 가능
     * - 비공개(PRIVATE): 작성자만 조회 가능
     *
     * @param postId 포스트 ID
     * @param requestUserId 요청 사용자 ID (nullable)
     * @return 포스트
     * @throws BusinessException POST_NOT_FOUND 또는 ACCESS_DENIED
     */
    public Post getPost(String postId, String requestUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("POST_NOT_FOUND", "Post not found: " + postId, 404));

        // 비공개 포스트는 작성자만 조회 가능
        if (post.getVisibility() == PostVisibility.PRIVATE) {
            if (requestUserId == null || !post.getAuthorId().equals(requestUserId)) {
                throw new BusinessException("ACCESS_DENIED", "Cannot access private post", 403);
            }
        }

        // DRAFT/BLOCKED 상태는 작성자만 조회 가능
        if ((post.getStatus() == PostStatus.DRAFT || post.getStatus() == PostStatus.BLOCKED)) {
            if (requestUserId == null || !post.getAuthorId().equals(requestUserId)) {
                throw new BusinessException("ACCESS_DENIED", "Cannot access non-published post", 403);
            }
        }

        return post;
    }

    /**
     * 포스트 삭제 (작성자만)
     *
     * @param postId 포스트 ID
     * @param userId 요청 사용자 ID
     * @throws BusinessException POST_NOT_FOUND 또는 ACCESS_DENIED
     */
    public void deletePost(String postId, String userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("POST_NOT_FOUND", "Post not found: " + postId, 404));

        if (!post.getAuthorId().equals(userId)) {
            throw new BusinessException("ACCESS_DENIED", "Only author can delete post", 403);
        }

        aiUserOutboxWriter.postLifecycleChanged(post, "POST_DELETED", "AUTHOR_DELETED");
        postRepository.deleteById(postId);
        log.info("Post deleted: {} by user {}", postId, userId);
    }

    /**
     * 포스트 차단 (관리자용)
     *
     * @param postId 포스트 ID
     * @throws BusinessException POST_NOT_FOUND
     */
    public void blockPost(String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("POST_NOT_FOUND", "Post not found: " + postId, 404));

        post.setStatus(PostStatus.BLOCKED);
        Post saved = postRepository.save(post);
        aiUserOutboxWriter.postLifecycleChanged(saved, "POST_BLOCKED", "ADMIN_BLOCKED");
        log.info("Post blocked: {}", postId);
    }

    /**
     * 포스트 차단 해제 (관리자용)
     *
     * @param postId 포스트 ID
     * @throws BusinessException POST_NOT_FOUND
     */
    public void unblockPost(String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException("POST_NOT_FOUND", "Post not found: " + postId, 404));

        // 다시 VOTING 상태로 복구
        post.setStatus(PostStatus.VOTING);
        Post saved = postRepository.save(post);
        aiUserOutboxWriter.postLifecycleChanged(saved, "POST_UNBLOCKED", "ADMIN_UNBLOCKED");
        log.info("Post unblocked: {}", postId);
    }
}
