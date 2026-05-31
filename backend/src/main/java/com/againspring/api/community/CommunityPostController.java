package com.againspring.api.community;

import com.againspring.api.community.dto.*;
import com.againspring.domain.community.Post;
import com.againspring.domain.community.VoteOption;
import com.againspring.service.community.JuryService;
import com.againspring.service.community.PostComposeService;
import com.againspring.service.community.PostService;
import com.againspring.service.community.VoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CommunityPostController - 커뮤니티 포스트 API
 */
@RestController
@RequestMapping("/api/community/posts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Community", description = "커뮤니티 포스트·투표·배심원")
public class CommunityPostController {

    private final PostComposeService composeService;
    private final PostService postService;
    private final VoteService voteService;
    private final JuryService juryService;

    /**
     * 포스트 생성
     * POST /api/community/posts
     */
    @PostMapping
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "포스트 생성", description = "사용자 사연을 중립화하여 공개 포스트로 생성")
    public ResponseEntity<PostResponse> createPost(
            @Valid @RequestBody PostCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userDetails.getUsername();
        Post post = composeService.composeAndNeutralize(
                userId,
                request.getBodyRaw(),
                request.getCategory(),
                request.getVisibility(),
                request.getSessionId()
        );

        // VoteOption 조회
        List<VoteOption> options = List.of(); // TODO: repository에서 조회
        PostResponse response = PostResponse.from(post, options);

        return ResponseEntity.ok(response);
    }

    /**
     * 공개 포스트 목록 조회
     * GET /api/community/posts?category=&page=&size=
     */
    @GetMapping
    @Operation(summary = "공개 포스트 목록", description = "공개된 포스트 중 투표 진행 중이거나 완료된 포스트")
    public ResponseEntity<Page<PostResponse>> listPosts(
            @RequestParam(required = false) String category,
            Pageable pageable) {

        Page<Post> posts = postService.listPublicPosts(category, pageable);
        Page<PostResponse> responses = posts.map(post -> {
            List<VoteOption> options = List.of(); // TODO: repository에서 조회
            return PostResponse.from(post, options);
        });

        return ResponseEntity.ok(responses);
    }

    /**
     * 포스트 상세 조회
     * GET /api/community/posts/{id}
     */
    @GetMapping("/{id}")
    @Operation(summary = "포스트 상세 조회", description = "투표 결과 및 현재 사용자의 투표 여부 포함")
    public ResponseEntity<PostDetailResponse> getPost(
            @PathVariable String id) {

        String userId = null; // TODO: 필요시 @AuthenticationPrincipal 추가
        Post post = postService.getPost(id, userId);

        List<VoteOption> options = List.of(); // TODO: repository에서 조회
        Map<Long, Long> voteResult = voteService.getVoteResult(id);
        Optional<Long> myVote = userId != null ? voteService.getMyVote(id, userId) : Optional.empty();

        PostDetailResponse response = PostDetailResponse.from(post, options, voteResult, myVote);
        return ResponseEntity.ok(response);
    }

    /**
     * 포스트 삭제
     * DELETE /api/community/posts/{id}
     */
    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "포스트 삭제", description = "작성자만 삭제 가능")
    public ResponseEntity<Void> deletePost(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        postService.deletePost(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    /**
     * 투표
     * POST /api/community/posts/{id}/vote
     */
    @PostMapping("/{id}/vote")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "투표 수행", description = "투표를 수행하고 현재 투표 결과 반환")
    public ResponseEntity<VoteResultResponse> castVote(
            @PathVariable String id,
            @Valid @RequestBody VoteRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Map<Long, Long> result = voteService.castVoteAndGetResult(id, request.getOptionId(), userDetails.getUsername());

        // 선택지 정보 조회
        List<VoteOption> options = List.of(); // TODO: repository에서 조회
        long totalVotes = result.values().stream().mapToLong(Long::longValue).sum();

        List<VoteOptionResultDto> resultDtos = options.stream()
                .map(opt -> {
                    long count = result.getOrDefault(opt.getId(), 0L);
                    double percentage = totalVotes > 0 ? (count * 100.0) / totalVotes : 0.0;
                    return VoteOptionResultDto.builder()
                            .id(opt.getId())
                            .label(opt.getLabel())
                            .count(count)
                            .percentage(percentage)
                            .build();
                })
                .toList();

        VoteResultResponse response = VoteResultResponse.builder()
                .options(resultDtos)
                .totalVotes(totalVotes)
                .myVotedOptionId(request.getOptionId())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 배심원 결과 조회
     * GET /api/community/posts/{id}/jury
     */
    @GetMapping("/{id}/jury")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "배심원 투표 결과", description = "작성자만 조회 가능")
    public ResponseEntity<JuryResultResponse> getJuryResult(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        // TODO: JuryService에서 조회 및 집계
        JuryResultResponse response = JuryResultResponse.builder()
                .legalNotice("이 결과는 공감 분포일 뿐 법적 책임이나 과실 비율과 무관합니다.")
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 포스트 신고
     * POST /api/community/posts/{id}/report
     */
    @PostMapping("/{id}/report")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "포스트 신고", description = "부적절한 포스트를 신고")
    public ResponseEntity<Void> reportPost(
            @PathVariable String id,
            @Valid @RequestBody ReportRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        // TODO: CommunityReport 저장 및 처리
        log.info("Post reported: {} by user {}, reason: {}", id, userDetails.getUsername(), request.getReason());

        return ResponseEntity.accepted().build();
    }

    /**
     * 포스트 좋아요 토글
     * POST /api/community/posts/{id}/like
     */
    @PostMapping("/{id}/like")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "포스트 좋아요 토글", description = "포스트에 좋아요를 추가하거나 제거")
    public ResponseEntity<LikeResponse> toggleLike(
            @PathVariable String id,
            @AuthenticationPrincipal UserDetails userDetails) {

        // TODO: CommentService에서 togglePostLike 호출
        LikeResponse response = LikeResponse.builder()
                .liked(true)
                .count(0L)
                .build();

        return ResponseEntity.ok(response);
    }
}
