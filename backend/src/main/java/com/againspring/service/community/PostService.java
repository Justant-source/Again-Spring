package com.againspring.service.community;

import com.againspring.common.exception.BusinessException;
import com.againspring.domain.community.Post;
import com.againspring.domain.enums.PostCategory;
import com.againspring.domain.enums.PostStatus;
import com.againspring.domain.enums.PostVisibility;
import com.againspring.repository.community.PostRepository;
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
        postRepository.save(post);
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
        postRepository.save(post);
        log.info("Post unblocked: {}", postId);
    }
}
