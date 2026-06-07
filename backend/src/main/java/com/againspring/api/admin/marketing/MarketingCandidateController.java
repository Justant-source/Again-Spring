package com.againspring.api.admin.marketing;

import com.againspring.domain.community.Post;
import com.againspring.domain.enums.PostCategory;
import com.againspring.repository.community.PostRepository;
import com.againspring.repository.UserRepository;
import com.againspring.service.marketing.CommunityPostMarketingReader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 마케팅 후보 사연 목록 API.
 * 관리자가 홍보할 커뮤니티 게시글을 찾기 위한 picker 엔드포인트.
 */
@RestController
@RequestMapping("/api/admin/marketing/candidate-posts")
@ConditionalOnProperty(name = "app.features.marketing.enabled", havingValue = "true")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Marketing Candidates", description = "Browse community posts as marketing candidates")
@SecurityRequirement(name = "bearerAuth")
public class MarketingCandidateController {

    private final PostRepository postRepo;
    private final CommunityPostMarketingReader postReader;
    private final UserRepository userRepo;

    /**
     * 마케팅 후보 사연 목록.
     *
     * @param sortBy   정렬 기준 (recommended | latest), 기본 recommended
     * @param category 카테고리 필터 (COUPLE|MARRIED|FRIEND|FAMILY|WORK|OTHER), 선택
     * @param q        키워드 검색 (제목/본문 LIKE), 선택
     * @param page     페이지 번호 (0-indexed), 기본 0
     * @param size     페이지 크기, 기본 20
     */
    @GetMapping
    @Operation(summary = "List candidate community posts for marketing")
    public ResponseEntity<List<CommunityPostMarketingReader.CandidatePostData>> list(
            @RequestParam(defaultValue = "recommended") String sortBy,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        List<Post> posts;

        if (q != null && !q.isBlank()) {
            // 키워드 검색 — 최신순
            String likeQ = "%" + q.trim() + "%";
            posts = postRepo.findPublicByKeywordForMarketing(likeQ,
                    PageRequest.of(page, size)).getContent();
        } else if ("latest".equalsIgnoreCase(sortBy)) {
            if (category != null && !category.isBlank()) {
                try {
                    PostCategory cat = PostCategory.valueOf(category.toUpperCase());
                    posts = postRepo.findPublicLatestForMarketingByCategory(cat,
                            PageRequest.of(page, size)).getContent();
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().build();
                }
            } else {
                posts = postRepo.findPublicLatestForMarketing(
                        PageRequest.of(page, size)).getContent();
            }
        } else {
            // recommended (기본)
            int offset = page * size;
            if (category != null && !category.isBlank()) {
                posts = postRepo.findPublicRankedForMarketingByCategory(
                        category.toUpperCase(), size, offset);
            } else {
                posts = postRepo.findPublicRankedForMarketing(size, offset);
            }
        }

        Set<String> syntheticIds = getSyntheticUserIds(posts);
        List<CommunityPostMarketingReader.CandidatePostData> result = posts.stream()
                .map(p -> postReader.toCandidateData(p, syntheticIds.contains(p.getAuthorId())))
                .toList();

        return ResponseEntity.ok(result);
    }

    private Set<String> getSyntheticUserIds(List<Post> posts) {
        try {
            Set<String> authorIds = posts.stream()
                    .map(Post::getAuthorId)
                    .filter(id -> id != null)
                    .collect(java.util.stream.Collectors.toSet());
            if (authorIds.isEmpty()) return Collections.emptySet();
            return userRepo.findSyntheticIds(authorIds);
        } catch (Exception e) {
            log.warn("Could not fetch synthetic user IDs: {}", e.getMessage());
            return Collections.emptySet();
        }
    }
}
