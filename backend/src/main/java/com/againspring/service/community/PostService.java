package com.againspring.service.community;

import com.againspring.common.exception.BusinessException;
import com.againspring.domain.community.Post;
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
        // 카테고리 필터링
        List<Post> posts;
        if (category != null && !category.isEmpty()) {
            try {
                com.againspring.domain.enums.PostCategory cat =
                        com.againspring.domain.enums.PostCategory.valueOf(category.toUpperCase());
                posts = postRepository.findByVisibilityAndStatusAndCategoryOrderByCreatedAtDesc(
                        PostVisibility.PUBLIC,
                        PostStatus.VOTING,
                        cat,
                        pageable
                );
            } catch (IllegalArgumentException e) {
                log.warn("Invalid category: {}", category);
                posts = postRepository.findByVisibilityAndStatusOrderByCreatedAtDesc(
                        PostVisibility.PUBLIC,
                        PostStatus.VOTING,
                        pageable
                );
            }
        } else {
            posts = postRepository.findByVisibilityAndStatusOrderByCreatedAtDesc(
                    PostVisibility.PUBLIC,
                    PostStatus.VOTING,
                    pageable
            );
        }

        // "recommended" 정렬은 추후 추천도(voteCount) 컬럼 추가 예정
        // 현재는 "latest"만 작동 (createdAt desc는 이미 적용됨)

        long total = posts.size();
        log.info("Listed {} public posts (category={}, sort={})", total, category, sort);
        return new PageImpl<>(posts, pageable, total);
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
