package com.againspring.api.community;

import com.againspring.api.community.dto.*;
import com.againspring.domain.community.Post;
import com.againspring.domain.community.VoteOption;
import com.againspring.domain.enums.CommentStatus;
import com.againspring.domain.enums.PostVisibility;
import com.againspring.repository.community.CommunityReportRepository;
import com.againspring.repository.community.VoteOptionRepository;
import com.againspring.service.community.CommentService;
import com.againspring.service.community.BotWriteIdempotencyService;
import com.againspring.service.community.PostComposeService;
import com.againspring.service.community.PostService;
import com.againspring.service.community.PromoTitleService;
import com.againspring.service.community.ViewService;
import com.againspring.service.community.VoteCountBreakdown;
import com.againspring.service.community.VoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * CommunityPostController - 커뮤니티 포스트 API
 */
@RestController
@RequestMapping("/api/community/posts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Community", description = "커뮤니티 포스트·투표")
public class CommunityPostController {

    private final PostComposeService composeService;
    private final PostService postService;
    private final VoteService voteService;
    private final ViewService viewService;
    private final VoteOptionRepository voteOptionRepository;
    private final CommunityReportRepository communityReportRepository;
    private final CommentService commentService;
    private final com.againspring.repository.UserRepository userRepository;
    private final com.againspring.repository.community.PostCommentRepository postCommentRepository;
    private final com.againspring.repository.community.VoteRepository voteRepository;
    private final com.againspring.repository.community.PostRepository postRepository;
    private final BotWriteIdempotencyService botWriteIdempotencyService;
    private final PromoTitleService promoTitleService;

    /**
     * 포스트 생성 — 원문 즉시 등록 + VoteOption 저장
     */
    @PostMapping
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "포스트 생성")
    public ResponseEntity<PostResponse> createPost(
            @Valid @RequestBody PostCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        String userId = userDetails.getUsername();
        // 재구성 출처 스냅샷 (AI 봇 전용, 일반 사용자는 null)
        com.againspring.service.community.PostComposeService.SourceSnapshot source = null;
        if (request.getSourceExampleId() != null) {
            source = new com.againspring.service.community.PostComposeService.SourceSnapshot(
                request.getSourceExampleId(),
                request.getSourceCommunity(),
                request.getSourceUrl(),
                request.getSourceOriginalTitle(),
                request.getSourceOriginalBody()
            );
        }
        final com.againspring.service.community.PostComposeService.SourceSnapshot sourceSnapshot = source;
        java.util.List<Integer> splits = com.againspring.marketing.CaptureSplitSupport.coalesceProposed(
                request.getCaptureSplitAfterLines(), request.getCaptureSplitAfterLine());
        boolean botIdempotent = botWriteIdempotencyService.appliesTo(userId, idempotencyKey);
        BotWriteIdempotencyService.Execution<Post> execution = botIdempotent
                ? botWriteIdempotencyService.execute(
                        userId, idempotencyKey, BotWriteIdempotencyService.TargetType.POST,
                        () -> composeService.composeAndPublish(
                                userId, request.getUserTitle(), request.getBodyRaw(), request.getCategory(),
                                request.getVisibility(), request.getSessionId(), sourceSnapshot,
                                splits, request.getPromoTitle(), request.getMetaphorId(), request.getMetaphorIds()),
                        existingId -> postRepository.findById(existingId).orElse(null))
                : new BotWriteIdempotencyService.Execution<>(composeService.composeAndPublish(
                        userId, request.getUserTitle(), request.getBodyRaw(), request.getCategory(),
                        request.getVisibility(), request.getSessionId(), sourceSnapshot,
                        splits, request.getPromoTitle(), request.getMetaphorId(), request.getMetaphorIds()), true);
        Post post = execution.target();

        List<VoteOption> options = voteOptionRepository.findByPostIdOrderByOrderIdx(post.getId());

        // 마케팅 훅 제목 — 모든 신규 사연 1회 생성 (발행 시 LLM 없음)
        if (execution.created()) {
            promoTitleService.generateAsync(post.getId());
        }

        return ResponseEntity.ok(PostResponse.from(post, options));
    }

    /**
     * 포스트 검색 — 바이그램 ngram + 제목 exact 티어 + 인기×감쇠.
     */
    @GetMapping("/search")
    @Operation(summary = "포스트 검색 (바이그램 ngram, exact 티어 + 인기×감쇠)")
    public ResponseEntity<Page<PostResponse>> searchPosts(
            @RequestParam String q,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        if (q == null || q.isBlank()) return ResponseEntity.ok(Page.empty());
        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);
        // 정렬은 PostService 네이티브 ORDER BY (Pageable Sort 무시)
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<Post> posts = postService.searchPosts(q, category, pageable);

        String userId = resolveUserId(authentication);
        java.util.Map<String, Long> myVoteMap = new java.util.HashMap<>();
        if (userId != null) {
            List<String> postIds = posts.stream().map(Post::getId).toList();
            voteRepository.findByVoterUserIdAndPostIdIn(userId, postIds)
                    .forEach(v -> myVoteMap.put(v.getPostId(), v.getOptionId()));
        }

        Page<PostResponse> responses = posts.map(post -> {
            List<VoteOption> options = voteOptionRepository.findByPostIdOrderByOrderIdx(post.getId());
            long voteCount = voteRepository.countByPostId(post.getId());
            long commentCount = postCommentRepository.countByPostIdAndStatusAndDeletedAtIsNull(post.getId(), CommentStatus.ACTIVE);
            String authorNickname = userRepository.findById(post.getAuthorId())
                    .map(u -> u.getNickname() != null ? u.getNickname() : "익명")
                    .orElse("익명");
            Long votedOptionId = myVoteMap.get(post.getId());
            return PostResponse.from(post, options, voteCount, commentCount, authorNickname, votedOptionId);
        });
        return ResponseEntity.ok(responses);
    }

    /**
     * 광장별 글 수 — 검색 패널의 "다른 광장" 표시용
     */
    @GetMapping("/counts")
    @Operation(summary = "광장별 공개 글 수")
    public ResponseEntity<java.util.Map<String, Long>> getCounts() {
        return ResponseEntity.ok(postService.getCategoryCounts());
    }

    /**
     * 공개 포스트 목록 조회
     */
    @GetMapping
    @Operation(summary = "공개 포스트 목록")
    public ResponseEntity<Page<PostResponse>> listPosts(
            @RequestParam(required = false) String category,
            @RequestParam(name = "sortBy", defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        // Pageable을 수동 생성 — Spring Data의 sort 파라미터와 이름 충돌 방지
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Post> posts = postService.listPublicPosts(category, sort, pageable);

        // 로그인/게스트 사용자의 투표 내역 벌크 조회 → myVoteSide 표시용
        String userId = resolveUserId(authentication);
        java.util.Map<String, Long> myVoteMap = new java.util.HashMap<>();
        if (userId != null) {
            List<String> postIds = posts.stream().map(Post::getId).toList();
            voteRepository.findByVoterUserIdAndPostIdIn(userId, postIds)
                    .forEach(v -> myVoteMap.put(v.getPostId(), v.getOptionId()));
        }

        Page<PostResponse> responses = posts.map(post -> {
            List<VoteOption> options = voteOptionRepository.findByPostIdOrderByOrderIdx(post.getId());
            long voteCount = voteRepository.countByPostId(post.getId());
            long commentCount = postCommentRepository.countByPostIdAndStatusAndDeletedAtIsNull(post.getId(), CommentStatus.ACTIVE);
            String authorNickname = userRepository.findById(post.getAuthorId())
                    .map(u -> u.getNickname() != null ? u.getNickname() : "익명")
                    .orElse("익명");
            Long votedOptionId = myVoteMap.get(post.getId());
            return PostResponse.from(post, options, voteCount, commentCount, authorNickname, votedOptionId);
        });
        return ResponseEntity.ok(responses);
    }

    /**
     * 투표한 글 목록 — 본인이 투표한 PUBLIC 포스트 최신순
     */
    @GetMapping("/voted")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "투표한 글 목록")
    public ResponseEntity<List<PostResponse>> listVotedPosts(
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userDetails.getUsername();
        List<com.againspring.domain.community.Vote> votes =
                voteRepository.findByVoterUserIdOrderByCreatedAtDesc(userId);

        List<PostResponse> responses = votes.stream()
                .map(v -> postService.findById(v.getPostId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(post -> {
                    List<VoteOption> options = voteOptionRepository.findByPostIdOrderByOrderIdx(post.getId());
                    long voteCount = voteRepository.countByPostId(post.getId());
                    long commentCount = postCommentRepository.countByPostIdAndStatusAndDeletedAtIsNull(post.getId(), CommentStatus.ACTIVE);
                    String authorNickname = userRepository.findById(post.getAuthorId())
                            .map(u -> u.getNickname() != null ? u.getNickname() : "익명")
                            .orElse("익명");
                    return PostResponse.from(post, options, voteCount, commentCount, authorNickname);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * 내 사연 목록 — 본인이 작성한 모든 포스트 (PUBLIC + PRIVATE 포함)
     */
    @GetMapping("/mine")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "내 사연 목록")
    public ResponseEntity<List<PostResponse>> listMyPosts(
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userDetails.getUsername();
        List<Post> posts = postService.listMyPosts(userId);
        List<PostResponse> responses = posts.stream().map(post -> {
            List<VoteOption> options = voteOptionRepository.findByPostIdOrderByOrderIdx(post.getId());
            long voteCount = voteRepository.countByPostId(post.getId());
            long commentCount = postCommentRepository.countByPostIdAndStatusAndDeletedAtIsNull(post.getId(), CommentStatus.ACTIVE);
            String authorNickname = userRepository.findById(post.getAuthorId())
                    .map(u -> u.getNickname() != null ? u.getNickname() : "익명")
                    .orElse("익명");
            return PostResponse.from(post, options, voteCount, commentCount, authorNickname);
        }).collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /**
     * 포스트 상세 조회
     */
    @GetMapping("/{id}")
    @Operation(summary = "포스트 상세 조회")
    public ResponseEntity<PostDetailResponse> getPost(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userDetails != null ? userDetails.getUsername() : null;
        Post post = postService.getPost(id, userId);

        List<VoteOption> options = voteOptionRepository.findByPostIdOrderByOrderIdx(id);

        // 투표 결과 조회 (사람/AI 분리)
        var voteResultWithBreakdown = voteService.getVoteResultWithBreakdown(id);
        // 가중치 적용 비율 계산
        var weightedPercentages = voteService.calculateWeightedPercentages(voteResultWithBreakdown);

        Optional<Long> myVote = userId != null ? voteService.getMyVote(id, userId) : Optional.empty();
        long commentCount = postCommentRepository.countByPostIdAndStatusAndDeletedAtIsNull(id, CommentStatus.ACTIVE);

        String authorNickname = userRepository.findById(post.getAuthorId())
                .map(u -> u.getNickname() != null ? u.getNickname() : "익명")
                .orElse("익명");
        String partnerNickname = post.getPartnerUserId() != null
                ? userRepository.findById(post.getPartnerUserId())
                        .map(u -> u.getNickname() != null ? u.getNickname() : "익명")
                        .orElse("익명")
                : null;
        boolean isPartner = userId != null && userId.equals(post.getPartnerUserId());

        return ResponseEntity.ok(PostDetailResponse.from(post, options, voteResultWithBreakdown, weightedPercentages, myVote, commentCount, userId, authorNickname, partnerNickname, isPartner));
    }

    /**
     * 조회수 기록 — device_id 기준 중복 카운트 방지
     * 공개 엔드포인트 (인증 불필요)
     */
    @PostMapping("/{id}/view")
    @Operation(summary = "조회수 기록")
    public ResponseEntity<Map<String, Long>> recordView(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {

        String deviceId = body.get("deviceId");
        if (deviceId == null || deviceId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        long viewCount = viewService.recordViewAndGetCount(id, deviceId);
        return ResponseEntity.ok(Map.of("viewCount", viewCount));
    }

    /**
     * 포스트 삭제 (작성자만)
     */
    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "포스트 삭제")
    public ResponseEntity<Void> deletePost(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        postService.deletePost(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    /**
     * 공개 투표
     */
    @PostMapping("/{id}/vote")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "투표 수행")
    public ResponseEntity<VoteResultResponse> castVote(
            @PathVariable String id,
            @Valid @RequestBody VoteRequest request,
            Authentication authentication) {

        String userId = resolveUserId(authentication);
        if (userId == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        voteService.castVoteAndGetResult(id, request.getOptionId(), userId);
        List<VoteOption> options = voteOptionRepository.findByPostIdOrderByOrderIdx(id);

        // 투표 결과 조회 (사람/AI 분리)
        var resultWithBreakdown = voteService.getVoteResultWithBreakdown(id);
        long totalVotes = resultWithBreakdown.values().stream()
                .mapToLong(bd -> bd.getTotalCount())
                .sum();

        // 가중치 적용 비율 계산
        var weightedPercentages = voteService.calculateWeightedPercentages(resultWithBreakdown);

        List<VoteOptionResultDto> resultDtos = options.stream()
                .map(opt -> {
                    long count = resultWithBreakdown.getOrDefault(opt.getId(), new VoteCountBreakdown(0L, 0L)).getTotalCount();
                    double percentage = weightedPercentages.getOrDefault(opt.getId(), 0.0);
                    return VoteOptionResultDto.builder()
                            .id(opt.getId())
                            .label(opt.getLabel())
                            .count(count)
                            .percentage(percentage)
                            .build();
                })
                .toList();

        return ResponseEntity.ok(VoteResultResponse.builder()
                .options(resultDtos)
                .totalVotes(totalVotes)
                .myVotedOptionId(request.getOptionId())
                .build());
    }

    /**
     * 투표 취소
     */
    @DeleteMapping("/{id}/vote")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "투표 취소")
    public ResponseEntity<VoteResultResponse> cancelVote(
            @PathVariable String id,
            Authentication authentication) {

        String userId = resolveUserId(authentication);
        if (userId == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        voteService.cancelVoteAndGetResult(id, userId);
        List<VoteOption> options = voteOptionRepository.findByPostIdOrderByOrderIdx(id);

        // 투표 결과 조회 (사람/AI 분리)
        var resultWithBreakdown = voteService.getVoteResultWithBreakdown(id);
        long totalVotes = resultWithBreakdown.values().stream()
                .mapToLong(bd -> bd.getTotalCount())
                .sum();

        // 가중치 적용 비율 계산
        var weightedPercentages = voteService.calculateWeightedPercentages(resultWithBreakdown);

        List<VoteOptionResultDto> resultDtos = options.stream()
                .map(opt -> {
                    long count = resultWithBreakdown.getOrDefault(opt.getId(), new VoteCountBreakdown(0L, 0L)).getTotalCount();
                    double percentage = weightedPercentages.getOrDefault(opt.getId(), 0.0);
                    return VoteOptionResultDto.builder()
                            .id(opt.getId())
                            .label(opt.getLabel())
                            .count(count)
                            .percentage(percentage)
                            .build();
                })
                .toList();

        return ResponseEntity.ok(VoteResultResponse.builder()
                .options(resultDtos)
                .totalVotes(totalVotes)
                .myVotedOptionId(null)
                .build());
    }

    /**
     * 포스트 신고
     */
    @PostMapping("/{id}/report")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "포스트 신고")
    public ResponseEntity<Void> reportPost(
            @PathVariable String id,
            @Valid @RequestBody ReportRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        com.againspring.domain.community.CommunityReport report =
                com.againspring.domain.community.CommunityReport.builder()
                        .targetType("POST")
                        .targetId(id)
                        .reporterUserId(userDetails.getUsername())
                        .reason(request.getReason())
                        .status("PENDING")
                        .build();
        communityReportRepository.save(report);
        return ResponseEntity.accepted().build();
    }

    /**
     * 포스트 좋아요 토글
     */
    @PostMapping("/{id}/like")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "포스트 좋아요 토글")
    public ResponseEntity<LikeResponse> toggleLike(
            @PathVariable String id,
            Authentication authentication) {

        String userId = resolveUserId(authentication);
        if (userId == null) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        boolean liked = commentService.togglePostLike(id, userId);
        long count = commentService.getPostLikeCount(id);
        return ResponseEntity.ok(LikeResponse.builder().liked(liked).count(count).build());
    }

    /**
     * 작성자 본문 수정
     */
    @PatchMapping("/{id}/body")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "작성자 본문 수정")
    public ResponseEntity<Void> editAuthorBody(
            @PathVariable String id,
            @RequestBody java.util.Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = userDetails.getUsername();
        Post post = postService.getPost(id, userId);
        if (!userId.equals(post.getAuthorId())) {
            return ResponseEntity.status(403).build();
        }
        String newBody = body.get("bodyRaw");
        if (newBody == null || newBody.isBlank()) return ResponseEntity.badRequest().build();
        postService.updateAuthorBody(post, newBody);
        return ResponseEntity.ok().build();
    }

    /**
     * 상대방 본문 수정
     */
    @PatchMapping("/{id}/partner-body")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "상대방 본문 수정")
    public ResponseEntity<Void> editPartnerBody(
            @PathVariable String id,
            @RequestBody java.util.Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        String userId = userDetails.getUsername();
        Post post = postService.getPost(id, userId);
        if (!userId.equals(post.getPartnerUserId())) {
            return ResponseEntity.status(403).build();
        }
        String newBody = body.get("bodyRaw");
        if (newBody == null || newBody.isBlank()) return ResponseEntity.badRequest().build();
        postService.updatePartnerBody(post, newBody);
        return ResponseEntity.ok().build();
    }

    private String resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;
        String name = authentication.getName();
        return "anonymousUser".equals(name) ? null : name;
    }
}
